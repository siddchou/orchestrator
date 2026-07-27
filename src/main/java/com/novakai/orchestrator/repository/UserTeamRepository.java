package com.novakai.orchestrator.repository;

import com.novakai.orchestrator.domain.entity.UserTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserTeamRepository extends JpaRepository<UserTeam, Long> {
    List<UserTeam> findByUserUserId(Long userId);
    Optional<UserTeam> findByUserUserIdAndTeamTeamId(Long userId, Long teamId);
    boolean existsByUserUserIdAndTeamTeamId(Long userId, Long teamId);

    @Query("SELECT ut FROM UserTeam ut JOIN FETCH ut.team WHERE ut.user.userId = :userId ORDER BY ut.team.teamName")
    List<UserTeam> findByUserUserIdWithTeams(Long userId);
}
