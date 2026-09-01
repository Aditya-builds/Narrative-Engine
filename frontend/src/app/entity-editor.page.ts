import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs/operators';
import { ApiService } from './api.service';
import { EntityClass, EntityDraft, applyClassDefaults, createDraft, toUpdateBody } from './defaults';

@Component({
  selector: 'app-entity-editor-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './entity-editor.page.html'
})
export class EntityEditorPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly kind = signal<'character' | 'persona'>('character');
  readonly characterName = signal('');
  readonly error = signal('');
  readonly busy = signal(false);
  draft: EntityDraft = createDraft();

  ngOnInit(): void {
    const kind = this.route.snapshot.data['kind'] === 'persona' ? 'persona' : 'character';
    this.kind.set(kind);
    this.characterName.set(this.route.snapshot.paramMap.get('characterName') ?? '');
  }

  get title(): string {
    return this.kind() === 'persona' ? 'Create a persona' : 'Create a character';
  }

  get backLink(): string[] {
    return this.kind() === 'persona'
      ? ['/characters', this.characterName(), 'personas']
      : ['/'];
  }

  onNameChange(): void {
    const name = this.draft.name.trim() || 'Newcomer';
    if (this.draft.description.includes('is a newly created')) {
      this.draft.description = createDraft(name, this.draft.class).description;
    }
    if (this.draft.openingMessage.includes('turns toward you')) {
      this.draft.openingMessage = `${name} turns toward you and waits for you to speak.`;
    }
  }

  onClassChange(characterClass: EntityClass): void {
    this.draft = applyClassDefaults(this.draft, characterClass);
  }

  save(): void {
    const name = this.draft.name.trim();
    if (!name) {
      this.error.set('Give them a name first.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    const body = toUpdateBody(this.draft);
    const created$ =
      this.kind() === 'persona'
        ? this.api.createPersona(name, this.draft.class).pipe(
            switchMap(() => this.api.updatePersona(name, body))
          )
        : this.api.createCharacter(name, this.draft.class).pipe(
            switchMap(() => this.api.updateCharacter(name, body))
          );

    created$.subscribe({
      next: () => {
        this.busy.set(false);
        if (this.kind() === 'persona') {
          void this.router.navigate(['/characters', this.characterName(), 'personas']);
        } else {
          void this.router.navigate(['/characters', name, 'personas']);
        }
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.error ?? 'Could not save. Try a different name.');
      }
    });
  }
}
