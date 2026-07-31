import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, TeamSummary } from '@app/core/models/api-response.model';
import { map } from 'rxjs/operators';

export interface ActiveTeamResponse {
  teamId: number;
  teamName: string;
}

@Injectable({ providedIn: 'root' })
export class TeamService {
  private readonly http = inject(HttpClient);

  listMyTeams() {
    return this.http.get<ApiResponse<TeamSummary[]>>('/api/teams/my-teams').pipe(
      map(resp => resp.data),
    );
  }

  setActiveTeam(teamId: number) {
    return this.http.post<ApiResponse<ActiveTeamResponse>>(`/api/teams/active/${teamId}`, {}).pipe(
      map(resp => resp.data),
    );
  }

  getActiveTeam() {
    return this.http.get<ApiResponse<ActiveTeamResponse>>('/api/teams/active').pipe(
      map(resp => resp.data),
    );
  }
}
