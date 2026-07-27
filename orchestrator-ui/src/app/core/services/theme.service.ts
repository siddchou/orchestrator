import { Injectable, signal, effect } from '@angular/core';

const STORAGE_KEY = 'orchestrator-theme';
type Theme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>('light');

  constructor() {
    this.init();

    // Sync data-theme attribute whenever theme changes
    effect(() => {
      document.documentElement.setAttribute('data-theme', this.theme());
      localStorage.setItem(STORAGE_KEY, this.theme());
    });
  }

  private init(): void {
    const stored = localStorage.getItem(STORAGE_KEY) as Theme | null;
    if (stored && (stored === 'light' || stored === 'dark')) {
      this.theme.set(stored);
      return;
    }
    // Fall back to system preference
    if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
      this.theme.set('dark');
    }
  }

  toggle(): void {
    this.theme.update(current => (current === 'light' ? 'dark' : 'light'));
  }
}
