package com.novakai.orchestrator.api.controller;

import com.novakai.orchestrator.api.dto.ActiveTeamResponse;
import com.novakai.orchestrator.api.dto.ApiResponse;
import com.novakai.orchestrator.api.dto.TeamSummary;
import com.novakai.orchestrator.api.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@Tag(name = "Teams", description = "Team memberships and active team selection")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @Operation(summary = "List current user's team memberships")
    @GetMapping("/my-teams")
    public ApiResponse<List<TeamSummary>> myTeams(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ApiResponse.error("Authentication required");
        }
        return ApiResponse.success(teamService.listUserTeams(userDetails.getUsername()));
    }

    @Operation(summary = "Set active team for the current user")
    @PostMapping("/active/{teamId}")
    public ApiResponse<ActiveTeamResponse> setActiveTeam(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long teamId) {
        if (userDetails == null) {
            return ApiResponse.error("Authentication required");
        }
        return ApiResponse.success(teamService.validateMembership(userDetails.getUsername(), teamId));
    }

    @Operation(summary = "Get current active team")
    @GetMapping("/active")
    public ApiResponse<ActiveTeamResponse> getActiveTeam(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "X-Team-Id", required = false) Long teamId) {
        if (userDetails == null) {
            return ApiResponse.error("Authentication required");
        }
        if (teamId == null) {
            // No active team set — check if user has exactly one team
            List<TeamSummary> teams = teamService.listUserTeams(userDetails.getUsername());
            if (teams.size() == 1) {
                TeamSummary t = teams.get(0);
                return ApiResponse.success(new ActiveTeamResponse(t.teamId(), t.teamName()));
            }
            return ApiResponse.error("No active team set");
        }
        return ApiResponse.success(teamService.validateMembership(userDetails.getUsername(), teamId));
    }
}
