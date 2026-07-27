import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { FormsModule } from '@angular/forms';
import { TeamSummary } from '@app/core/models/api-response.model';
import { TeamService, ActiveTeamResponse } from '@app/core/services/team.service';
import { AuthService } from '@app/core/services/auth.service';

@Component({
  selector: 'app-team-switcher',
  imports: [CommonModule, MatSelectModule, MatButtonModule, MatIconModule, FormsModule],
  templateUrl: './team-switcher.html',
  styleUrl: './team-switcher.scss',
})
export class TeamSwitcherComponent implements OnInit {
  private readonly teamService = inject(TeamService);
  private readonly auth = inject(AuthService);

  teams: TeamSummary[] = [];
  activeTeamId: number | null = null;
  loading = true;
  isViewer = false;

  ngOnInit(): void {
    this.loadTeams();
  }

  private loadTeams(): void {
    this.teamService.listMyTeams().subscribe({
      next: teams => {
        this.teams = teams;
        this.loading = false;
        this.isViewer = teams.length > 0 && teams[0]?.role?.toUpperCase() !== 'ADMIN';

        // Load or resolve active team
        if (this.teams.length === 1) {
          this.setActive(this.teams[0].teamId);
        } else {
          this.teamService.getActiveTeam().subscribe({
            next: active => this.setActive(active.teamId),
            error: () => {
              // If no active team set, pick first
              if (this.teams.length > 0) {
                this.setActive(this.teams[0].teamId);
              }
            },
          });
        }
      },
      error: () => (this.loading = false),
    });
  }

  onTeamChange(teamId: number): void {
    if (teamId === this.activeTeamId) return;
    this.teamService.setActiveTeam(teamId).subscribe({
      next: (resp: ActiveTeamResponse) => {
        this.auth.setActiveTeamId(resp.teamId);
        window.location.reload();
      },
    });
  }

  private setActive(teamId: number): void {
    this.activeTeamId = teamId;
    this.auth.setActiveTeamId(teamId);
  }

  get selectedTeamName(): string {
    const t = this.teams.find(x => x.teamId === this.activeTeamId);
    return t?.teamName ?? '';
  }
}
