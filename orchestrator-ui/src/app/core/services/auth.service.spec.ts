import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { AuthService, AuthUser } from './auth.service';
import { TeamSummary } from '../models/api-response.model';

describe('AuthService - Team features', () => {
  let service: AuthService;
  let routerSpy: any;

  const baseUser: AuthUser = {
    token: 'test-token',
    role: 'OPERATOR',
    passwordExpired: false,
  };

  beforeEach(() => {
    sessionStorage.clear();
    routerSpy = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AuthService,
        { provide: Router, useValue: routerSpy },
      ],
    });
    service = TestBed.inject(AuthService);
  });

  // --- Active team ID ---

  it('returns null when no team set', () => {
    expect(service.getActiveTeamId()).toBeNull();
  });

  it('stores and retrieves activeTeamId', () => {
    service['_currentUser'].next({ ...baseUser, activeTeamId: undefined });

    service.setActiveTeamId(5);
    expect(service.getActiveTeamId()).toBe(5);
  });

  it('team ID persists across loadFromStorage', () => {
    sessionStorage.clear();
    const userWithTeam: AuthUser = {
      ...baseUser,
      activeTeamId: 3,
    };
    sessionStorage.setItem('orch_auth', JSON.stringify(userWithTeam));

    // Create a fresh service that reads from storage on init
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AuthService,
        { provide: Router, useValue: routerSpy },
      ],
    });
    service = TestBed.inject(AuthService);

    expect(service.getActiveTeamId()).toBe(3);
  });

  // --- loadTeams ---

  it('loadTeams sets teams array and single-team active ID', () => {
    service['_currentUser'].next({ ...baseUser });

    const teams: TeamSummary[] = [{ teamId: 42, teamName: 'OnlyTeam' }];
    service.loadTeams(teams);

    expect(service.getTeams().length).toBe(1);
    expect(service.getTeams()[0].teamName).toBe('OnlyTeam');
    // Single team should auto-set as active
    expect(service.getActiveTeamId()).toBe(42);
  });

  it('loadTeams with explicit activeTeamId overrides auto-select', () => {
    service['_currentUser'].next({ ...baseUser });

    const teams: TeamSummary[] = [
      { teamId: 1, teamName: 'Alpha' },
      { teamId: 2, teamName: 'Beta' },
    ];
    service.loadTeams(teams, 2);

    expect(service.getTeams().length).toBe(2);
    expect(service.getActiveTeamId()).toBe(2);
  });

  // --- Existing auth features (baseline) ---

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('isLoggedIn returns false when no user', () => {
    expect(service.isLoggedIn()).toBe(false);
  });

  it('getToken returns null when not logged in', () => {
    expect(service.getToken()).toBeNull();
  });

  it('getUserRole returns null when not logged in', () => {
    expect(service.getUserRole()).toBeNull();
  });

  it('logout clears user and navigates to login', () => {
    service['_currentUser'].next(baseUser);
    expect(service.isLoggedIn()).toBe(true);

    service.logout();

    expect(service.isLoggedIn()).toBe(false);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
    expect(sessionStorage.getItem('orch_auth')).toBeNull();
  });

  it('isRole matches case-insensitively', () => {
    service['_currentUser'].next({ ...baseUser, role: 'admin' });
    expect(service.isRole('ADMIN')).toBe(true);
    expect(service.isRole('admin')).toBe(true);
    expect(service.isRole('OPERATOR')).toBe(false);
  });

  it('getTeams returns empty array when no teams loaded', () => {
    service['_currentUser'].next(baseUser);
    expect(service.getTeams()).toEqual([]);
  });
});
