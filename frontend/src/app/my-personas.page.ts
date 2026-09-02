import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from './api.service';
import { EntityCardComponent } from './entity-card';
import { WorldEntity } from './models';

@Component({
  selector: 'app-my-personas-page',
  imports: [RouterLink, EntityCardComponent],
  template: `
    <section class="page">
      <div class="page-head">
        <div class="page-copy">
          <p class="eyebrow">Profile</p>
          <h1>My Personas</h1>
          <p class="lede">These are the identities you can speak as when a chat starts.</p>
        </div>
        <a class="btn primary" routerLink="/personas/new">Create a Persona</a>
      </div>
      @if (error()) {
        <p class="banner">{{ error() }}</p>
      }
      <div class="cast-grid">
        @for (persona of personas(); track persona.name) {
          <app-entity-card kind="persona" [entity]="persona" actionLabel="Your persona" />
        }
      </div>
    </section>
  `
})
export class MyPersonasPage implements OnInit {
  private readonly api = inject(ApiService);
  readonly personas = signal<WorldEntity[]>([]);
  readonly error = signal('');

  ngOnInit(): void {
    this.api.listPersonaNames().subscribe({
      next: (names) => this.load(names),
      error: () => this.error.set('Could not load personas. Is the backend running on port 8080?')
    });
  }

  private load(names: string[]): void {
    if (names.length === 0) {
      this.personas.set([]);
      return;
    }
    forkJoin(names.map((name) => this.api.getPersona(name).pipe(catchError(() => of(null))))).subscribe((loaded) => {
      this.personas.set(loaded.filter((item): item is WorldEntity => item !== null));
    });
  }
}
