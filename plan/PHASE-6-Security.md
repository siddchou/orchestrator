# Phase 6 — Security & Credential Management

> **Goal:** Secure the platform with JWT-based authentication, role-based access control,
> an encrypted credential store for SFTP secrets, and a full audit trail.

---

## 6.1 Authentication Flow

```
Angular Login Form
      │
      ▼
POST /api/auth/login  { username, password }
      │
      ▼
Spring Security AuthenticationManager
      │ (validates against APP_USER table or LDAP)
      ▼
JwtService.generateToken(username, roles)
      │
      ▼
{ accessToken, expiresIn }
      │
Angular stores token in memory (not localStorage)
      │
Every subsequent request:
  Authorization: Bearer <token>
      │
JwtAuthFilter validates signature + expiry
```

---

## 6.2 User Table

Add to Flyway as `V4__create_app_user.sql`:

```sql
CREATE TABLE APP_USER (
    USER_ID     NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    USERNAME    VARCHAR2(100) NOT NULL,
    PASSWORD    VARCHAR2(200) NOT NULL,   -- BCrypt hash
    ROLE        VARCHAR2(20)  NOT NULL
                CHECK (ROLE IN ('ADMIN','OPERATOR','VIEWER')),
    ENABLED     CHAR(1)       DEFAULT 'Y' NOT NULL CHECK (ENABLED IN ('Y','N')),
    CREATED_AT  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT UQ_USERNAME UNIQUE (USERNAME)
);

-- Seed a default admin (password = 'changeme' BCrypt hash — MUST be changed first login)
INSERT INTO APP_USER (USERNAME, PASSWORD, ROLE)
VALUES ('admin', '$2a$12$eImiTXuWVxfM37uY4JANjQ==', 'ADMIN');
```

---

## 6.3 Spring Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/", "/index.html", "/assets/**", "/*.js", "/*.css").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/runs/**", "/api/jobs/**").hasAnyRole("VIEWER","OPERATOR","ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/jobs/*/run").hasAnyRole("OPERATOR","ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/runs/*/cancel").hasAnyRole("OPERATOR","ADMIN")
                .requestMatchers("/api/system/**").hasRole("ADMIN")
                .requestMatchers("/api/jobs/**", "/api/env-vars/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
```

---

## 6.4 JWT Service

```java
@Service
public class JwtService {

    // HS256 secret from environment variable (min 256 bits)
    @Value("${JWT_SECRET}")
    private String secret;

    @Value("${orchestrator.security.jwt-expiry-hours:8}")
    private int expiryHours;

    public String generateToken(UserDetails user) {
        return Jwts.builder()
            .subject(user.getUsername())
            .claim("roles", user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiryHours * 3_600_000L))
            .signWith(getKey())
            .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser().verifyWith(getKey()).build()
            .parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean isValid(String token, UserDetails user) {
        try {
            String username = extractUsername(token);
            return username.equals(user.getUsername()) && !isExpired(token);
        } catch (JwtException ex) {
            return false;
        }
    }

    private boolean isExpired(String token) {
        return Jwts.parser().verifyWith(getKey()).build()
            .parseSignedClaims(token).getPayload().getExpiration().before(new Date());
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
```

### JWT Filter

```java
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }

        String token = authHeader.substring(7);
        String username = jwtService.extractUsername(token);

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (jwtService.isValid(token, userDetails)) {
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        chain.doFilter(req, res);
    }
}
```

---

## 6.5 Auth Controller

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        authManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.username(), request.password())
        );
        UserDetails user = userDetailsService.loadUserByUsername(request.username());
        String token = jwtService.generateToken(user);
        return ApiResponse.success(new AuthResponse(token, jwtService.getExpiryHours() * 3600));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication auth) {
        // Validate current password, then BCrypt hash new password and save
        ...
    }
}

public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
public record AuthResponse(String accessToken, long expiresInSeconds) {}
```

---

## 6.6 Role Summary

| Role | Permissions |
|------|-------------|
| `VIEWER` | Read-only: list jobs, view runs, view logs |
| `OPERATOR` | Viewer + trigger runs manually, cancel runs |
| `ADMIN` | Everything: create/edit/delete jobs, manage schedules, global config, manage users |

Apply method-level security where needed:

```java
@PreAuthorize("hasRole('ADMIN')")
public void deleteJob(Long id) { ... }

@PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
public JobRun triggerRun(Long jobId, ...) { ... }
```

---

## 6.7 Credential Management API

```java
@RestController
@RequestMapping("/api/credentials")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CredentialController {

    private final JobCredentialRepository credRepo;
    private final CredentialDecryptionService cryptoService;

    @GetMapping
    public ApiResponse<List<CredentialSummary>> list() {
        // Return refs and types ONLY — never the encrypted value
        return ApiResponse.success(credRepo.findAll().stream()
            .map(c -> new CredentialSummary(c.getCredentialId(), c.getCredentialRef(), c.getCredType()))
            .toList());
    }

    @PostMapping
    public ApiResponse<CredentialSummary> create(
            @Valid @RequestBody CredentialRequest request) throws Exception {
        String encrypted = cryptoService.encrypt(request.value());
        JobCredential cred = JobCredential.builder()
            .credentialRef(request.ref())
            .credType(CredentialType.valueOf(request.type()))
            .credValue(encrypted)
            .createdAt(LocalDateTime.now())
            .build();
        cred = credRepo.save(cred);
        return ApiResponse.success(new CredentialSummary(
            cred.getCredentialId(), cred.getCredentialRef(), cred.getCredType()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        credRepo.deleteById(id);
    }
}

public record CredentialSummary(Long id, String ref, CredentialType type) {}
public record CredentialRequest(
    @NotBlank String ref,
    @NotBlank String type,   // "PASSWORD" or "SSH_KEY"
    @NotBlank String value   // plain text — encrypted server-side
) {}
```

---

## 6.8 Audit Logging

Apply audit logging as a Spring AOP aspect so it does not pollute business services.

```java
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditRepo;

    @AfterReturning(
        pointcut = "@annotation(auditable)",
        returning = "result"
    )
    public void audit(JoinPoint jp, Auditable auditable, Object result) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";

        AuditLog entry = AuditLog.builder()
            .username(username)
            .action(auditable.action())
            .entityType(auditable.entityType())
            // Extract entity ID from result via reflection if needed
            .createdAt(LocalDateTime.now())
            .build();
        auditRepo.save(entry);
    }
}

// Annotation usage on service methods:
@Auditable(action = "DELETE_JOB", entityType = "JOB")
public void deleteJob(Long id) { ... }

@Auditable(action = "TRIGGER_RUN", entityType = "JOB_RUN")
public JobRun triggerRun(Long jobId, ...) { ... }
```

---

## 6.9 LDAP Integration (Optional)

If the environment has Active Directory or LDAP, replace the `APP_USER` table auth
with Spring Security LDAP:

```java
@Configuration
@ConditionalOnProperty("orchestrator.security.ldap.enabled")
public class LdapSecurityConfig {

    @Bean
    public AuthenticationProvider ldapAuthProvider(
            @Value("${orchestrator.security.ldap.url}") String ldapUrl,
            @Value("${orchestrator.security.ldap.user-search-base}") String userSearchBase) {
        LdapAuthenticationProvider provider = new LdapAuthenticationProvider(
            new BindAuthenticator(new DefaultSpringSecurityContextSource(ldapUrl)),
            new DefaultLdapAuthoritiesPopulator(
                new DefaultSpringSecurityContextSource(ldapUrl), "ou=groups")
        );
        return provider;
    }
}
```

When LDAP is active, the `APP_USER` table is only used for local fallback admin access.

---

## 6.10 Environment Variables Required

| Variable | Purpose |
|----------|---------|
| `JWT_SECRET` | HS256 signing key (min 32 chars) |
| `ORCHESTRATOR_ENCRYPTION_KEY` | AES-256 key for credential encryption (exactly 32 chars) |
| `DB_HOST`, `DB_SERVICE`, `DB_USER`, `DB_PASSWORD` | Oracle connection |

These must be set in the systemd `EnvironmentFile` (see Phase 7) — never in `application.yml`.

---

## Phase 6 Acceptance Criteria

- [ ] `POST /api/auth/login` with valid credentials returns a JWT
- [ ] Protected endpoints return `401` without a token
- [ ] `VIEWER` role cannot `POST /api/jobs` (returns `403`)
- [ ] `OPERATOR` role can trigger runs but cannot create jobs
- [ ] `ADMIN` role has full access to all endpoints
- [ ] Credentials stored via API are AES-256 encrypted in DB — raw value not retrievable via any endpoint
- [ ] Credential list endpoint returns `ref` and `type` only — never the encrypted value
- [ ] Audit log records every job create, delete, trigger, and cancel event
- [ ] Token expiry enforced — expired tokens return `401`
- [ ] Default admin password must be changed on first login (enforced via `passwordExpired` flag)

---

**Previous:** [Phase 5 — Angular UI](./PHASE-5-UI.md)  
**Next:** [Phase 7 — Deployment](./PHASE-7-Deploy.md)
