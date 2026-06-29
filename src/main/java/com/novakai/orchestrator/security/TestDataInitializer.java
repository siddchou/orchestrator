package com.novakai.orchestrator.security;

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
        appUserRepository.save(new AppUser(null, "admin", hash, "ADMIN", "Y", "N", null));
        appUserRepository.save(new AppUser(null, "operator", hash, "OPERATOR", "Y", "N", null));
        appUserRepository.save(new AppUser(null, "viewer", hash, "VIEWER", "Y", "N", null));
    }
}
