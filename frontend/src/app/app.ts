import { Component, HostListener, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs/operators';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly router = inject(Router);
  readonly profileOpen = signal(false);
  readonly query = signal('');

  readonly chatMode = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => /\/chat(?:\?|$)/.test(event.urlAfterRedirects)),
      startWith(/\/chat(?:\?|$)/.test(this.router.url))
    ),
    { initialValue: /\/chat(?:\?|$)/.test(this.router.url) }
  );

  constructor() {
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.closeProfile();
        this.query.set(this.router.parseUrl(event.urlAfterRedirects).queryParams['q'] ?? '');
      });
  }

  search(): void {
    const q = this.query().trim();
    void this.router.navigate(['/'], { queryParams: q ? { q } : {} });
  }

  toggleProfile(): void {
    this.profileOpen.update((open) => !open);
  }

  closeProfile(): void {
    this.profileOpen.set(false);
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    this.closeProfile();
  }
}
