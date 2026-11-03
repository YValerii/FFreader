import { computed, Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'ffreader-theme';

/** Follows the operating system until the user picks a theme, then remembers their choice. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly systemPreference = window.matchMedia('(prefers-color-scheme: dark)');
  private readonly systemTheme = signal<Theme>(this.systemPreference.matches ? 'dark' : 'light');
  private readonly chosenTheme = signal<Theme | null>(this.readChosenTheme());

  readonly theme = computed(() => this.chosenTheme() ?? this.systemTheme());

  constructor() {
    this.systemPreference.addEventListener('change', (event) =>
      this.systemTheme.set(event.matches ? 'dark' : 'light')
    );
  }

  toggle(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.chosenTheme.set(next);
    document.documentElement.dataset['theme'] = next;
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Storage can be blocked; the theme still applies until the page is reloaded.
    }
  }

  private readChosenTheme(): Theme | null {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      return stored === 'light' || stored === 'dark' ? stored : null;
    } catch {
      return null;
    }
  }
}
