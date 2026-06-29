# Phase 6a — Security: JWT Authentication & Spring Security Config

> **Goal:** Implement JWT-based stateless authentication. Covers the user table,
> `JwtService`, the JWT filter, `SecurityFilterChain`, and the login endpoint.
> Role-based access control and the credential store are in Phase 6b.

> **Depends on:** Phase 1 (Flyway), Phase 3a (`ApiResponse`)  
> **Produces:** `V4__create_app_user.sql`, `JwtService`, `JwtAuthFilter`,
> `SecurityConfig`, `AuthController`, `AppUserDetailsService`

---

## 6a.1 User Table — Flyway Migration `V4__create_app_user.sql`

```sql
CREATE TABLE APP_USER (
    USER_ID         NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    USERNAME        VARCHAR2(100)  NOT NULL,
    PASSWORD_HASH   VARCHAR2(200)  NOT NULL,    -- BCrypt hash
    ROLE            VARCHAR2(20)   NOT NULL
                    CHECK (ROLE IN ('ADMIN','OPERATOR','VIEWER')),
    ENABLED         CHAR(1)        DEFAULT 'Y' NOT NULL CHECK (ENABLED IN ('Y','N')),
    PASSWORD_EXPIRED CHAR(1)       DEFAULT 'N' NOT NULL CHECK (PASSWORD_EXPIRED IN ('Y','N')),
    CREATED_AT      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT UQ_APP_USERNAME UNIQUE (USERNAME)
);

-- Seed default admin (password = 'changeme' — MUST be rotated on first login)
-- BCrypt hash of 'changeme' with strength 12:
INSERT INTO APP_USER (USERNAME, PASSWORD_HASH, ROLE, PASSWORD_EXPIRED)
VALUES (
    'admin',
    '$2a$12$tuLjV5GQFvzEynPLJsOdCOUeAF3SNMrPh3MJqCbcEt.58eiW0Mkay',
    'ADMIN',
    'Y'    -- force password change on first login
);

COMMIT;
```

---

## 6a.2 `AppUser` JPA Entity

```java
// com.yourco.orchestrator.domain.AppUser

@Entity
@Table(name = "APP_USER")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "USERNAME", nullable = false, unique = true)
    private String username;

    @Column(name = "PASSWORD_HASH", nullable = false)
    private String passwordHash;

    @Column(name = "ROLE", nullable = false)
    private String role;   // "ADMIN", "OPERATOR", "VIEWER"

    @Column(name = "ENABLED", nullable = false)
    private String enabled = "Y";

    @Column(name = "PASSWORD_EXPIRED", nullable = false)
    private String passwordExpired = "N";

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;
}
```

```java
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
}
```

---

## 6a.3 `AppUserDetailsService`

Bridges Spring Security's `UserDetailsService` with the `APP_USER` table.

```java
// com.yourco.orchestrator.security.AppUserDetailsService

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepo.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return User.builder()
            .username(user.getUsername())
            .password(user.getPasswordHash())
            .roles(user.getRole())           // Spring prefixes "ROLE_" automatically
            .disabled(!"Y".equals(user.getEnabled()))
            .credentialsExpired("Y".equals(user.getPasswordExpired()))
            .build();
    }
}
```

---

## 6a.4 `JwtService`

Signs and validates JWTs using HMAC-SHA256 with a secret from environment variable.

```java
// com.yourco.orchestrator.security.JwtService

@Service
@Slf4j
public class JwtService {

    // Must be at least 32 characters (256 bits for HS256)
    @Value("${JWT_SECRET}")
    private String secret;

    @Value("${orchestrator.security.jwt-expiry-hours:8}")
    private int expiryHours;

    public String generateToken(UserDetails user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", user.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority).toList());

        return Jwts.builder()
            .claims(claims)
            .subject(user.getUsername())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiryHours * 3_600_000L))
            .signWith(getSigningKey(), Jwts.SIG.HS256)
            .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails user) {
        try {
            String username = extractUsername(token);
            return username.equals(user.getUsername()) && !isExpired(token);
        } catch (JwtException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public int getExpiryHours() { return expiryHours; }

    // ---------------------------------------------------------------
    private boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
```

> **Dependency:** Add to `pom.xml`:
> ```xml
> <dependency>
>   <groupId>io.jsonwebtoken</groupId>
>   <artifactId>jjwt-api</artifactId>
>   <version>0.12.5</version>
> </dependency>
> <dependency>
>   <groupId>io.jsonwebtoken</groupId>
>   <artifactId>jjwt-impl</artifactId>
>   <version>0.12.5</version>
>   <scope>runtime</scope>
> </dependency>
> <dependency>
>   <groupId>io.jsonwebtoken</groupId>
>   <artifactId>jjwt-jackson</artifactId>
>   <version>0.12.5</version>
>   <scope>runtime</scope>
> </dependency>
> ```

---

## 6a.5 `JwtAuthFilter`

Runs on every request before Spring Security's username/password filter.

```java
// com.yourco.orchestrator.security.JwtAuthFilter

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Skip if no Bearer token present
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token    = authHeader.substring(7);
        final String username = jwtService.extractUsername(token);

        // Only authenticate if not already authenticated in this request
        if (username != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (jwtService.isTokenValid(token, userDetails)) {
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

---

## 6a.6 `SecurityConfig`

```java
// com.yourco.orchestrator.config.SecurityConfig

@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // enables @PreAuthorize on service methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AppUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public — Angular assets and login
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers(
                    "/", "/index.html",
                    "/assets/**", "/*.js", "/*.css", "/*.ico",
                    "/favicon.ico"
                ).permitAll()
                // Actuator health (for load balancer checks)
                .requestMatchers("/actuator/health").permitAll()
                // Everything else requires authentication
                // (fine-grained RBAC applied via @PreAuthorize in Phase 6b)
                .anyRequest().authenticated()
            )
            .userDetailsService(userDetailsService)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

---

## 6a.7 Auth Controller

```java
// com.yourco.orchestrator.api.controller.AuthController

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authManager;
    private final AppUserDetailsService userDetailsService;
    private final AppUserRepository userRepo;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        // Will throw AuthenticationException (→ 401) if invalid
        authManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.username(), request.password()));

        UserDetails user = userDetailsService.loadUserByUsername(request.username());
        String token = jwtService.generateToken(user);

        return ApiResponse.success(new AuthResponse(
            token,
            jwtService.getExpiryHours() * 3600L,
            user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList()
        ));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication auth) {

        AppUser user = userRepo.findByUsername(auth.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordExpired("N");
        userRepo.save(user);

        return ApiResponse.success(null);
    }
}

// DTOs
public record LoginRequest(
    @NotBlank String username,
    @NotBlank String password
) {}

public record AuthResponse(
    String accessToken,
    long expiresInSeconds,
    List<String> roles
) {}

public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = 8) String newPassword
) {}
```

---

## 6a.8 Handling 401 / 403 Responses

Spring Security needs a custom entry point to return `ApiResponse` JSON (not the default HTML error page) when a request is unauthenticated or forbidden:

```java
@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
            ApiResponse.error("Unauthorized: " + ex.getMessage()));
    }
}
```

Add to `SecurityConfig.filterChain()`:

```java
.exceptionHandling(ex -> ex
    .authenticationEntryPoint(jwtAuthEntryPoint)
)
```

---

## Phase 6a Acceptance Criteria

- [ ] Flyway `V4` migration creates `APP_USER` and inserts the default admin
- [ ] `POST /api/auth/login` with `admin` / `changeme` returns a JWT and role list
- [ ] `POST /api/auth/login` with wrong credentials returns `401` JSON (not HTML)
- [ ] Any protected endpoint without a token returns `401` JSON
- [ ] Any protected endpoint with a valid token processes the request
- [ ] Expired token (manipulate expiry time in test) returns `401`
- [ ] `POST /api/auth/change-password` with correct current password updates the hash
- [ ] After password change, old credentials are rejected
- [ ] Default admin has `PASSWORD_EXPIRED = Y`; the Angular login component should redirect to a change-password screen if the response includes `credentialsExpired: true`
- [ ] `BCryptPasswordEncoder` with strength 12 — unit test that hash round-trip passes

---

**Previous:** [Phase 5e — Routing, Config & Build](./PHASE-5e-UI-Routing-Config-Build.md)  
**Next:** [Phase 6b — Security: RBAC, Credentials & Audit](./PHASE-6b-Security-RBAC-Credentials-Audit.md)
