import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { App } from './app';
import { AuthService } from '@app/core/services/auth.service';
import { of } from 'rxjs';

// Mock AuthService to avoid HTTP dependencies
const mockAuthService = {
  currentUser: of(null),
  isAuthenticated: () => false,
  login: () => of({ success: true }),
  logout: () => {},
};

describe('App', () => {
  beforeEach(() => {
    // jsdom doesn't have window.matchMedia — mock for ThemeService
    if (!window.matchMedia) {
      Object.defineProperty(window, 'matchMedia', {
        writable: true,
        value: (query: string) => ({
          matches: false,
          media: query,
          onchange: null,
          addListener: () => {},
          removeListener: () => {},
          addEventListener: () => {},
          removeEventListener: () => {},
          dispatchEvent: () => false,
        }),
      });
    }
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        { provide: AuthService, useValue: mockAuthService },
        { provide: ActivatedRoute, useValue: { snapshot: { data: {} } } },
        {
          provide: Router,
          useValue: {
            url: '/',
            events: of({}),
            navigate: () => Promise.resolve(true),
          },
        },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render title', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.brand-title')?.textContent).toContain('Orchestrator');
  });
});
