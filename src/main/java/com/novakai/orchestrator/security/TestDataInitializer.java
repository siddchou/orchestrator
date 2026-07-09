package com.novakai.orchestrator.security;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.entity.AppUser;
import com.novakai.orchestrator.repository.AppUserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestDataInitializer {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataInitializer(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        String hash = passwordEncoder.encode("changeme");
        createIfMissing("admin", "ADMIN", hash);
        createIfMissing("operator", "OPERATOR", hash);
        createIfMissing("viewer", "VIEWER", hash);
    }

    private void createIfMissing(String username, String role, String passwordHash) {
        appUserRepository.findByUsername(username)
                .orElseGet(() -> appUserRepository.save(
                        new AppUser(null, username, passwordHash, role, "Y", "N", null)))
                ;
    }
}
