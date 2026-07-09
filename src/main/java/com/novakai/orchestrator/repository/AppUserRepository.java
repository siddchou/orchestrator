package com.novakai.orchestrator.repository;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
}
