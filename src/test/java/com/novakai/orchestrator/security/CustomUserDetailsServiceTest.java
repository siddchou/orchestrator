package com.novakai.orchestrator.security;

import com.novakai.orchestrator.domain.entity.AppUser;
import com.novakai.orchestrator.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomUserDetailsServiceTest {

    @Autowired
    private CustomUserDetailsService service;

    @Autowired
    private AppUserRepository userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepo.deleteAll();
        AppUser user = AppUser.builder()
                .username("testuser")
                .passwordHash(passwordEncoder.encode("testpass"))
                .role("ADMIN")
                .enabled("Y")
                .passwordExpired("N")
                .build();
        userRepo.save(user);
    }

    @Test
    void loadUserByUsername_success() {
        UserDetails userDetails = service.loadUserByUsername("testuser");
        assertEquals("testuser", userDetails.getUsername());
        assertFalse(userDetails.getAuthorities().isEmpty());
    }

    @Test
    void loadUserByUsername_not_found() {
        assertThrows(RuntimeException.class, () -> service.loadUserByUsername("nonexistent"));
    }

    @Test
    void loadUserByUsername_returns_correct_role() {
        UserDetails userDetails = service.loadUserByUsername("testuser");
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void isPasswordExpired_not_expired() {
        assertFalse(service.isPasswordExpired("testuser"));
    }

    @Test
    void isPasswordExpired_is_expired() {
        AppUser user = userRepo.findByUsername("testuser").orElseThrow();
        user.setPasswordExpired("Y");
        userRepo.save(user);

        assertTrue(service.isPasswordExpired("testuser"));
    }

    @Test
    void isPasswordExpired_nonexistent_user() {
        assertFalse(service.isPasswordExpired("ghost"));
    }
}
