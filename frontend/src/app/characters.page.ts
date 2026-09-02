import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from './api.service';
import { EntityCardComponent } from './entity-card';
import { WorldEntity } from './models';

@Component({
  selector: 'app-characters-page',
  imports: [EntityCardComponent],
  templateUrl: './characters.page.html'
})
export class CharactersPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly characters = signal<WorldEntity[]>([]);
  readonly error = signal('');
  readonly query = signal('');
  readonly visible = computed(() => {
    const q = this.query().trim().toLowerCase();
    if (!q) {
      return this.characters();
    }
    return this.characters().filter((character) =>
      [character.name, character.class, character.description].some((part) => part?.toLowerCase().includes(q))
    );
  });

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((params) => this.query.set(params.get('q') ?? ''));
    this.api.listCharacterNames().subscribe({
      next: (names) => this.loadEntities(names),
      error: () => this.error.set('Could not load characters. Is the backend running on port 8080?')
    });
  }

  talk(character: WorldEntity): void {
    void this.router.navigate(['/characters', character.name, 'personas']);
  }

  private loadEntities(names: string[]): void {
    if (names.length === 0) {
      this.characters.set([]);
      return;
    }
    forkJoin(
      names.map((name) => this.api.getCharacter(name).pipe(catchError(() => of(null))))
    ).subscribe((loaded) => {
      this.characters.set(loaded.filter((item): item is WorldEntity => item !== null));
    });
  }
}
