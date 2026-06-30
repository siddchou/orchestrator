import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { CommonModule } from '@angular/common';
import { AuthService } from './core/services/auth.service';
import { interval } from 'rxjs';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    CommonModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  userRole = '';
  isLoginPage = false;
  pageTitle = '';
  currentTime = '';
  private timeSub?: any;

  private readonly pageTitles: Map<string, string> = new Map([
    ['/dashboard', 'Dashboard'],
    ['/jobs', 'Jobs'],
    ['/jobs/new', 'New Job'],
    ['/runs', 'Run History'],
    ['/config', 'Configuration'],
  ]);

  constructor() {
    this.router.events.subscribe(() => {
      this.isLoginPage = this.router.url.includes('/login');
      this.updatePageTitle();
    });

    // Update clock every minute
    this.timeSub = interval(60000).subscribe(() => this.updateTime());
  }

  ngOnInit(): void {
    this.auth.currentUser.subscribe(user => {
      this.userRole = user?.role ?? '';
    });

    this.updateTime();
    this.updatePageTitle();
  }

  ngOnDestroy(): void {
    this.timeSub?.unsubscribe();
  }

  private updatePageTitle(): void {
    const url = this.router.url.replace('#', '').split('?')[0];
    // Check exact match first, then prefix match for routes with IDs
    let title = '';
    for (const [path, label] of this.pageTitles) {
      if (url.startsWith(path)) {
        title = label;
        break;
      }
    }
    this.pageTitle = title || 'Orchestrator';
  }

  private updateTime(): void {
    this.currentTime = new Date().toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  get showConfig(): boolean {
    return this.userRole === 'ADMIN';
  }

  get navItems() {
    const base = [
      { label: 'Dashboard', icon: 'dashboard', route: '/dashboard' },
      { label: 'Jobs', icon: 'work', route: '/jobs' },
      { label: 'Runs', icon: 'history', route: '/runs' },
    ];
    if (this.showConfig) {
      base.push({ label: 'Config', icon: 'settings', route: '/config' });
    }
    return base;
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
