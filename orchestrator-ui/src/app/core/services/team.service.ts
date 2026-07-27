import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TeamSummary } from '@app/core/models/api-response.model';

export interface ActiveTeamResponse {
  teamId: number;
  teamName: string;
}

@Injectable({ providedIn: 'root' })
export class TeamService {
  private readonly http = inject(HttpClient);

  listMyTeams() {
    return this.http.get<TeamSummary[]>('/api/teams/my-teams');
  }

  setActiveTeam(teamId: number) {
    return this.http.post<ActiveTeamResponse>(`/api/teams/active/${teamId}`, {});
  }

  getActiveTeam() {
    return this.http.get<ActiveTeamResponse>('/api/teams/active');
  }
}
