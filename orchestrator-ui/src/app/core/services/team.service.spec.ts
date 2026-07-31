import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TeamService, ActiveTeamResponse } from './team.service';
import { TeamSummary } from '../models/api-response.model';

describe('TeamService', () => {
  let service: TeamService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [TeamService],
    });
    service = TestBed.inject(TeamService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('listMyTeams returns typed array from API', () => {
    const teams: TeamSummary[] = [
      { teamId: 1, teamName: 'Alpha', role: 'ADMIN' },
      { teamId: 2, teamName: 'Beta', role: 'MEMBER' },
    ];

    let result: TeamSummary[] | undefined;
    service.listMyTeams().subscribe(t => { result = t; });

    const req = httpMock.expectOne('/api/teams/my-teams');
    expect(req.request.method).toBe('GET');
    req.flush(teams);

    expect(result?.length).toBe(2);
    expect(result?.[0].teamName).toBe('Alpha');
    expect(result?.[1].role).toBe('MEMBER');
  });

  it('setActiveTeam sends POST to correct URL with teamId', () => {
    const response: ActiveTeamResponse = { teamId: 7, teamName: 'Gamma' };

    let result: ActiveTeamResponse | undefined;
    service.setActiveTeam(7).subscribe(r => { result = r; });

    const req = httpMock.expectOne('/api/teams/active/7');
    expect(req.request.method).toBe('POST');
    req.flush(response);

    expect(result?.teamId).toBe(7);
    expect(result?.teamName).toBe('Gamma');
  });

  it('getActiveTeam returns current active team', () => {
    const response: ActiveTeamResponse = { teamId: 3, teamName: 'Delta' };

    let result: ActiveTeamResponse | undefined;
    service.getActiveTeam().subscribe(r => { result = r; });

    const req = httpMock.expectOne('/api/teams/active');
    expect(req.request.method).toBe('GET');
    req.flush(response);

    expect(result?.teamId).toBe(3);
  });
});
