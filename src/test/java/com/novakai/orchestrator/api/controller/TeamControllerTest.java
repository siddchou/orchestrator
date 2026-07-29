package com.novakai.orchestrator.api.controller;

import com.novakai.orchestrator.domain.entity.AppUser;
import com.novakai.orchestrator.domain.entity.Team;
import com.novakai.orchestrator.domain.entity.UserTeam;
import com.novakai.orchestrator.repository.AppUserRepository;
import com.novakai.orchestrator.security.JwtAuthFilter;
import com.novakai.orchestrator.repository.TeamRepository;
import com.novakai.orchestrator.repository.UserTeamRepository;
import com.novakai.orchestrator.security.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MockMvcBuilder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TeamControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserRepository userRepo;

    @Autowired
    private TeamRepository teamRepo;

    @Autowired
    private UserTeamRepository userTeamRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String operatorToken;
    private Long defaultTeamId;
    private Long engineeringTeamId;

    @BeforeEach
    void setUp() {
        JwtAuthFilter jwtFilter = new JwtAuthFilter(
                context.getBean(JwtService.class),
                context.getBean(org.springframework.security.core.userdetails.UserDetailsService.class));

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(jwtFilter)
                .build();

        // Clean up in FK-safe order
        userTeamRepo.deleteAll();
        teamRepo.deleteAll();
        userRepo.deleteAll();

        // Create teams
        Team defaultTeam = Team.builder().teamName("Default").build();
        Team engineering = Team.builder().teamName("Engineering").build();
        teamRepo.save(defaultTeam);
        teamRepo.save(engineering);
        defaultTeamId = defaultTeam.getTeamId();
        engineeringTeamId = engineering.getTeamId();

        // Create admin user with membership to both teams
        AppUser admin = AppUser.builder()
                .username("ctrl_admin")
                .passwordHash(passwordEncoder.encode("admin1234"))
                .role("ADMIN")
                .enabled("Y")
                .passwordExpired("N")
                .build();
        userRepo.save(admin);

        UserTeam adminDefault = UserTeam.builder()
                .user(admin).team(defaultTeam).role("ADMIN").build();
        UserTeam adminEng = UserTeam.builder()
                .user(admin).team(engineering).role("OPERATOR").build();
        userTeamRepo.save(adminDefault);
        userTeamRepo.save(adminEng);

        // Create operator user with membership to only Engineering team
        AppUser operator = AppUser.builder()
                .username("ctrl_operator")
                .passwordHash(passwordEncoder.encode("op1234"))
                .role("OPERATOR")
                .enabled("Y")
                .passwordExpired("N")
                .build();
        userRepo.save(operator);

        UserTeam opEng = UserTeam.builder()
                .user(operator).team(engineering).role("MEMBER").build();
        userTeamRepo.save(opEng);

        // Create viewer user with NO team memberships (gets auto-enrolled)
        AppUser viewer = AppUser.builder()
                .username("ctrl_viewer")
                .passwordHash(passwordEncoder.encode("viewer1234"))
                .role("VIEWER")
                .enabled("Y")
                .passwordExpired("N")
                .build();
        userRepo.save(viewer);

        // Generate JWT tokens directly (no HTTP needed)
        var adminUserDetails = User.withUsername("ctrl_admin")
                .password("admin1234")
                .roles("ADMIN")
                .build();
        adminToken = jwtService.generateToken(adminUserDetails);

        var operatorUserDetails = User.withUsername("ctrl_operator")
                .password("op1234")
                .roles("OPERATOR")
                .build();
        operatorToken = jwtService.generateToken(operatorUserDetails);
    }

    // ─── GET /api/teams/my-teams ──────────────────────────────

    @Test
    @Order(1)
    void getMyTeams_multiple_teams() throws Exception {
        mockMvc.perform(get("/api/teams/my-teams")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].teamName").value("Default"))
                .andExpect(jsonPath("$.data[1].teamName").value("Engineering"));
    }

    @Test
    @Order(2)
    void getMyTeams_single_team() throws Exception {
        mockMvc.perform(get("/api/teams/my-teams")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].teamName").value("Engineering"));
    }

    @Test
    @Order(3)
    void getMyTeams_without_auth() throws Exception {
        mockMvc.perform(get("/api/teams/my-teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    // ─── POST /api/teams/active/{teamId} ──────────────────────

    @Test
    @Order(4)
    void setActiveTeam_success() throws Exception {
        mockMvc.perform(post("/api/teams/active/" + defaultTeamId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.teamName").value("Default"));
    }

    @Test
    @Order(5)
    void setActiveTeam_non_member() throws Exception {
        mockMvc.perform(post("/api/teams/active/" + defaultTeamId)
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void setActiveTeam_nonexistent_team() throws Exception {
        mockMvc.perform(post("/api/teams/active/999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void setActiveTeam_without_auth() throws Exception {
        mockMvc.perform(post("/api/teams/active/" + defaultTeamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    // ─── GET /api/teams/active ────────────────────────────────

    @Test
    @Order(8)
    void getActiveTeam_with_header() throws Exception {
        mockMvc.perform(get("/api/teams/active")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Team-Id", String.valueOf(engineeringTeamId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.teamName").value("Engineering"));
    }

    @Test
    @Order(9)
    void getActiveTeam_fallback_single_team() throws Exception {
        mockMvc.perform(get("/api/teams/active")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.teamName").value("Engineering"));
    }

    @Test
    @Order(10)
    void getActiveTeam_no_header_multiple_teams() throws Exception {
        mockMvc.perform(get("/api/teams/active")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error").value("No active team set"));
    }

    @Test
    @Order(11)
    void getActiveTeam_without_auth() throws Exception {
        mockMvc.perform(get("/api/teams/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    @Order(12)
    void getActiveTeam_invalid_team_header() throws Exception {
        mockMvc.perform(get("/api/teams/active")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Team-Id", "999"))
                .andExpect(status().isBadRequest());
    }
}
