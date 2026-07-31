import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { FormsModule } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TeamSummary } from '@app/core/models/api-response.model';
import { TeamService, ActiveTeamResponse } from '@app/core/services/team.service';
import { AuthService } from '@app/core/services/auth.service';
import { FormGuardService } from '@app/core/services/form-guard.service';

const MAX_LOAD_RETRIES = 3;

@Component({
  selector: 'app-team-switcher',
  imports: [CommonModule, MatSelectModule, MatButtonModule, MatIconModule, FormsModule],
  templateUrl: './team-switcher.html',
  styleUrl: './team-switcher.scss',
})
export class TeamSwitcherComponent implements OnInit {
  private readonly teamService = inject(TeamService);
  private readonly auth = inject(AuthService);
  private readonly formGuard = inject(FormGuardService, { optional: true });
  private readonly snackBar = inject(MatSnackBar);

  teams: TeamSummary[] = [];
  activeTeamId: number | null = null;
  loading = true;
  isViewer = false;

  // E12: API failure state — retry button visible after load fails
  loadError = false;
  private retryCount = 0;

  ngOnInit(): void {
    this.loadTeams();
  }

  private loadTeams(): void {
    this.loading = true;
    this.loadError = false;
    this.teamService.listMyTeams().subscribe({
      next: teams => {
        this.teams = teams;
        // Cache last-known-good in sessionStorage (E12 fallback)
        try { sessionStorage.setItem('teams_cache', JSON.stringify(teams)); } catch { /* quota */ }
        this.loading = false;
        this.loadError = false;
        this.retryCount = 0;
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
      error: () => {
        this.loading = false;
        this.retryCount++;
        if (this.retryCount >= MAX_LOAD_RETRIES) {
          this.loadError = true;
          // Fallback to cached teams from previous load
          try {
            const cached = sessionStorage.getItem('teams_cache');
            if (cached) {
              this.teams = JSON.parse(cached);
              if (this.teams.length > 0) {
                this.setActive(this.teams[0].teamId);
              }
            }
          } catch { /* ignore */ }
          this.snackBar.open('Failed to load teams after multiple attempts', 'Reload page', {
            duration: 8000,
          });
        } else {
          // Auto-retry once more silently
          setTimeout(() => this.loadTeams(), 1500 * this.retryCount);
        }
      },
    });
  }

  /** E12: Manual retry when load fails */
  retryLoad(): void {
    this.retryCount = 0;
    this.loadError = false;
    this.loadTeams();
  }

  onTeamChange(teamId: number): void {
    if (teamId === this.activeTeamId) return;

    // E2: warn if any form in the app has unsaved changes
    if (this.formGuard?.hasUnsavedChanges && !confirm('You have unsaved changes. Switching teams will discard them. Continue?')) {
      return;
    }

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
