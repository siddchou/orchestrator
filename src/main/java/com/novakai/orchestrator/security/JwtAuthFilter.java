package com.novakai.orchestrator.security;

// @author Siddhant Choudhary

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.extern.slf4j.Slf4j;
import java.io.IOException;

@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        log.info("JwtAuthFilter ENTER: method={} uri={} authHeader={}",
                request.getMethod(), request.getRequestURI(),
                authHeader != null ? "PRESENT(" + authHeader.substring(0, Math.min(20, authHeader.length())) + "...)" : "NULL");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.info("JwtAuthFilter: no Bearer token for {} {} — passing through without auth",
                    request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        final String jwtToken = authHeader.substring(7);
        final String username = jwtService.extractUsername(jwtToken);
        log.info("JwtAuthFilter: extracted username={} from token for {} {}",
                username, request.getMethod(), request.getRequestURI());

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtService.isTokenValid(jwtToken, userDetails)) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.info("JwtAuthFilter: SET auth for user {} on request {}", username, request.getRequestURI());
            } else {
                log.info("JwtAuthFilter: INVALID token for user {} on request {}", username, request.getRequestURI());
            }
        } else {
            log.info("JwtAuthFilter: skipped — SecurityContext already has auth={}",
                    SecurityContextHolder.getContext().getAuthentication() != null);
        }
        filterChain.doFilter(request, response);
    }
}
