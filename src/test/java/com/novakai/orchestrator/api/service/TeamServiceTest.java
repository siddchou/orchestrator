package com.novakai.orchestrator.api.service;

import com.novakai.orchestrator.api.dto.ActiveTeamResponse;
import com.novakai.orchestrator.api.dto.TeamSummary;
import com.novakai.orchestrator.domain.entity.AppUser;
import com.novakai.orchestrator.domain.entity.Team;
import com.novakai.orchestrator.domain.entity.UserTeam;
import com.novakai.orchestrator.repository.AppUserRepository;
import com.novakai.orchestrator.repository.TeamRepository;
import com.novakai.orchestrator.repository.UserTeamRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TeamServiceTest {

    @Autowired
    private TeamService teamService;

    @Autowired
    private AppUserRepository userRepo;

    @Autowired
    private TeamRepository teamRepo;

    @Autowired
    private UserTeamRepository userTeamRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AppUser adminUser;
    private AppUser regularUser;
    private Team defaultTeam;
    private Team engineeringTeam;

    @BeforeEach
    void setUp() {
        userTeamRepo.deleteAll();
        teamRepo.deleteAll();
        userRepo.deleteAll();

        defaultTeam = teamRepo.save(Team.builder().teamName("Default").build());
        engineeringTeam = teamRepo.save(Team.builder().teamName("Engineering").build());

        adminUser = userRepo.save(AppUser.builder()
                .username("tst_admin")
                .passwordHash(passwordEncoder.encode("pass"))
                .role("ADMIN")
                .enabled("Y")
                .passwordExpired("N")
                .build());

        regularUser = userRepo.save(AppUser.builder()
                .username("tst_alice")
                .passwordHash(passwordEncoder.encode("pass"))
                .role("OPERATOR")
                .enabled("Y")
                .passwordExpired("N")
                .build());
    }

    @Test
    void listUserTeams_empty_when_no_memberships() {
        List<TeamSummary> teams = teamService.listUserTeams("tst_alice");
        assertTrue(teams.isEmpty());
    }

    @Test
    void listUserTeams_returns_memberships() {
        userTeamRepo.save(UserTeam.builder().user(regularUser).team(defaultTeam).role("MEMBER").build());
        userTeamRepo.save(UserTeam.builder().user(regularUser).team(engineeringTeam).role("ADMIN").build());

        List<TeamSummary> teams = teamService.listUserTeams("tst_alice");
        assertEquals(2, teams.size());
    }

    @Test
    void listUserTeams_includes_role() {
        userTeamRepo.save(UserTeam.builder().user(regularUser).team(defaultTeam).role("MEMBER").build());

        List<TeamSummary> teams = teamService.listUserTeams("tst_alice");
        assertEquals(1, teams.size());
        assertEquals("MEMBER", teams.get(0).role());
    }

    @Test
    void listUserTeams_throws_for_unknown_user() {
        assertThrows(RuntimeException.class, () -> teamService.listUserTeams("ghost"));
    }

    @Test
    void validateMembership_success() {
        userTeamRepo.save(UserTeam.builder().user(regularUser).team(defaultTeam).role("MEMBER").build());

        ActiveTeamResponse resp = teamService.validateMembership("tst_alice", defaultTeam.getTeamId());
        assertEquals(defaultTeam.getTeamId(), resp.teamId());
        assertEquals("Default", resp.teamName());
    }

    @Test
    void validateMembership_throws_for_non_member() {
        userTeamRepo.save(UserTeam.builder().user(regularUser).team(defaultTeam).role("MEMBER").build());

        assertThrows(IllegalArgumentException.class, () ->
                teamService.validateMembership("tst_alice", engineeringTeam.getTeamId()));
    }

    @Test
    void validateMembership_throws_for_nonexistent_team() {
        assertThrows(IllegalArgumentException.class, () ->
                teamService.validateMembership("tst_alice", 999L));
    }

    @Test
    void validateMembership_throws_for_unknown_user() {
        assertThrows(RuntimeException.class, () ->
                teamService.validateMembership("ghost", defaultTeam.getTeamId()));
    }

    @Test
    void isGlobalAdmin_returns_true_for_admin_role() {
        assertTrue(teamService.isGlobalAdmin("tst_admin"));
    }

    @Test
    void isGlobalAdmin_returns_false_for_operator() {
        assertFalse(teamService.isGlobalAdmin("tst_alice"));
    }

    @Test
    void isGlobalAdmin_returns_false_for_unknown_user() {
        assertFalse(teamService.isGlobalAdmin("ghost"));
    }

    @Test
    void getUserId_returns_id_for_existing_user() {
        Long id = teamService.getUserId("tst_alice");
        assertEquals(regularUser.getUserId(), id);
    }

    @Test
    void getUserId_throws_for_unknown_user() {
        assertThrows(RuntimeException.class, () -> teamService.getUserId("ghost"));
    }

    @Test
    void enrollToDefaultIfNone_enrolls_new_user() {
        teamService.enrollToDefaultIfNone("tst_alice");

        List<UserTeam> memberships = userTeamRepo.findByUserUserId(regularUser.getUserId());
        assertEquals(1, memberships.size());
        assertEquals(defaultTeam.getTeamId(), memberships.get(0).getTeam().getTeamId());
        assertEquals("MEMBER", memberships.get(0).getRole());
    }

    @Test
    void enrollToDefaultIfNone_skips_user_with_existing_membership() {
        userTeamRepo.save(UserTeam.builder().user(regularUser).team(engineeringTeam).role("ADMIN").build());

        teamService.enrollToDefaultIfNone("tst_alice");

        List<UserTeam> memberships = userTeamRepo.findByUserUserId(regularUser.getUserId());
        assertEquals(1, memberships.size());
    }

    @Test
    void enrollToDefaultIfNone_skips_nonexistent_user() {
        teamService.enrollToDefaultIfNone("ghost_user");
    }
}
