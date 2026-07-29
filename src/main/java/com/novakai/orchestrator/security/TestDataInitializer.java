package com.novakai.orchestrator.security;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.entity.AppUser;
import com.novakai.orchestrator.domain.entity.Team;
import com.novakai.orchestrator.domain.entity.UserTeam;
import com.novakai.orchestrator.repository.AppUserRepository;
import com.novakai.orchestrator.repository.TeamRepository;
import com.novakai.orchestrator.repository.UserTeamRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestDataInitializer {

    private final AppUserRepository appUserRepository;
    private final TeamRepository teamRepository;
    private final UserTeamRepository userTeamRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataInitializer(AppUserRepository appUserRepository,
                               TeamRepository teamRepository,
                               UserTeamRepository userTeamRepository,
                               PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.teamRepository = teamRepository;
        this.userTeamRepository = userTeamRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        String hash = passwordEncoder.encode("changeme");
        AppUser admin = createIfMissing("admin", "ADMIN", hash);
        AppUser operator = createIfMissing("operator", "OPERATOR", hash);
        AppUser viewer = createIfMissing("viewer", "VIEWER", hash);

        Team testTeam = teamRepository.findByTeamName("test-team")
                .orElseGet(() -> teamRepository.save(Team.builder().teamName("test-team").build()));

        assignIfMissing(admin, testTeam, "ADMIN");
        assignIfMissing(operator, testTeam, "MEMBER");
        assignIfMissing(viewer, testTeam, "VIEWER");
    }

    private AppUser createIfMissing(String username, String role, String passwordHash) {
        return appUserRepository.findByUsername(username)
                .orElseGet(() -> appUserRepository.save(
                        new AppUser(null, username, passwordHash, role, "Y", "N", null)));
    }

    private void assignIfMissing(AppUser user, Team team, String role) {
        if (!userTeamRepository.existsByUserUserIdAndTeamTeamId(user.getUserId(), team.getTeamId())) {
            userTeamRepository.save(UserTeam.builder().user(user).team(team).role(role).build());
        }
    }
}
