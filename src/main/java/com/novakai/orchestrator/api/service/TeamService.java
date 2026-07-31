package com.novakai.orchestrator.api.service;

import com.novakai.orchestrator.api.dto.ActiveTeamResponse;
import com.novakai.orchestrator.api.dto.TeamSummary;
import com.novakai.orchestrator.domain.entity.AppUser;
import com.novakai.orchestrator.domain.entity.UserTeam;
import com.novakai.orchestrator.repository.AppUserRepository;
import com.novakai.orchestrator.repository.TeamRepository;
import com.novakai.orchestrator.repository.UserTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final UserTeamRepository userTeamRepo;
    private final TeamRepository teamRepo;
    private final AppUserRepository appUserRepo;

    /** List all teams the given user belongs to, with per-team role */
    @Transactional(readOnly = true)
    public List<TeamSummary> listUserTeams(String username) {
        AppUser user = appUserRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        return userTeamRepo.findByUserUserIdWithTeams(user.getUserId())
                .stream()
                .map(ut -> new TeamSummary(
                        ut.getTeam().getTeamId(),
                        ut.getTeam().getTeamName(),
                        ut.getRole()))
                .toList();
    }

    /** Validate that the user is a member of the given team */
    @Transactional(readOnly = true)
    public ActiveTeamResponse validateMembership(String username, Long teamId) {
        AppUser user = appUserRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        UserTeam membership = userTeamRepo.findByUserUserIdAndTeamTeamId(user.getUserId(), teamId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User " + username + " is not a member of team " + teamId));

        return new ActiveTeamResponse(membership.getTeam().getTeamId(), membership.getTeam().getTeamName());
    }

    /** Check if user is a global admin (bypasses team scoping) */
    @Transactional(readOnly = true)
    public boolean isGlobalAdmin(String username) {
        return appUserRepo.findByUsername(username)
                .map(u -> "ADMIN".equals(u.getRole()))
                .orElse(false);
    }

    /** Get the user ID for a username (used by team-scoped queries) */
    @Transactional(readOnly = true)
    public Long getUserId(String username) {
        return appUserRepo.findByUsername(username)
                .map(AppUser::getUserId)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    /** If the user has no team memberships, auto-enroll them to the Default team as MEMBER */
    @Transactional
    public void enrollToDefaultIfNone(String username) {
        AppUser user = appUserRepo.findByUsername(username).orElse(null);
        if (user == null) return;

        List<UserTeam> memberships = userTeamRepo.findByUserUserId(user.getUserId());
        if (!memberships.isEmpty()) return; // already has teams

        teamRepo.findByTeamName("Default").ifPresent(defaultTeam -> {
            UserTeam membership = new UserTeam();
            membership.setUser(user);
            membership.setTeam(defaultTeam);
            membership.setRole("MEMBER");
            userTeamRepo.save(membership);
        });
    }
}
