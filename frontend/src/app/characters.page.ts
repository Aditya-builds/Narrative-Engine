import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from './api.service';
import { EntityCardComponent } from './entity-card';
import { WorldEntity } from './models';

@Component({
  selector: 'app-characters-page',
  imports: [RouterLink, EntityCardComponent],
  templateUrl: './characters.page.html'
})
export class CharactersPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly characters = signal<WorldEntity[]>([]);
  readonly error = signal('');

  ngOnInit(): void {
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
