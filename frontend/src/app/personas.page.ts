import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from './api.service';
import { ChatStore } from './chat-store';
import { EntityCardComponent } from './entity-card';
import { WorldEntity } from './models';

@Component({
  selector: 'app-personas-page',
  imports: [RouterLink, EntityCardComponent],
  templateUrl: './personas.page.html'
})
export class PersonasPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly store = inject(ChatStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly characterName = signal('');
  readonly character = signal<WorldEntity | null>(null);
  readonly personas = signal<WorldEntity[]>([]);
  readonly resumePersona = signal('');
  readonly error = signal('');

  ngOnInit(): void {
    const name = this.route.snapshot.paramMap.get('characterName') ?? '';
    this.characterName.set(name);
    this.resumePersona.set(this.store.latestPersona(name));
    this.api.getCharacter(name).subscribe({
      next: (loaded) => this.character.set(loaded),
      error: () => this.error.set(`Could not load character ${name}.`)
    });
    this.api.listPersonaNames().subscribe({
      next: (names) => this.loadEntities(names),
      error: () => this.error.set('Could not load personas. Is the backend running on port 8080?')
    });
  }

  enterChat(persona: WorldEntity): void {
    const locked = this.resumePersona();
    if (locked && persona.name !== locked) {
      return;
    }
    void this.router.navigate([
      '/characters',
      this.characterName(),
      'personas',
      locked || persona.name,
      'chat'
    ]);
  }

  cardAction(persona: WorldEntity): string {
    const locked = this.resumePersona();
    if (!locked) {
      return `Enter chat as ${persona.name}`;
    }
    return persona.name === locked ? `Continue chat as ${persona.name}` : `Locked to ${locked}`;
  }

  private loadEntities(names: string[]): void {
    if (names.length === 0) {
      this.personas.set([]);
      return;
    }
    forkJoin(
      names.map((name) => this.api.getPersona(name).pipe(catchError(() => of(null))))
    ).subscribe((loaded) => {
      this.personas.set(loaded.filter((item): item is WorldEntity => item !== null));
    });
  }
}
