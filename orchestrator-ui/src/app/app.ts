import { Component, OnInit } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { CommonModule } from '@angular/common';
import { AuthService } from './core/services/auth.service';

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
export class App implements OnInit {
  userRole = '';
  isLoginPage = false;

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
  ) {
    this.router.events.subscribe(() => {
      this.isLoginPage = this.router.url.includes('/login');
    });
  }

  ngOnInit(): void {
    this.auth.currentUser.subscribe(user => {
      if (user) {
        this.userRole = user.role;
      } else {
        this.userRole = '';
      }
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
