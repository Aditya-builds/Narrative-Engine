import { AfterViewChecked, Component, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from './api.service';
import { ChatMessage, WorldEntity } from './models';
import { PortraitComponent } from './portrait';

@Component({
  selector: 'app-chat-page',
  imports: [FormsModule, RouterLink, PortraitComponent],
  templateUrl: './chat.page.html'
})
export class ChatPage implements OnInit, AfterViewChecked {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private shouldScroll = false;

  @ViewChild('thread') private thread?: ElementRef<HTMLElement>;

  readonly character = signal<WorldEntity | null>(null);
  readonly persona = signal<WorldEntity | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly error = signal('');
  readonly busy = signal(false);
  readonly showProfile = signal(false);
  draft = '';
  characterNotes = '';
  personaNotes = '';

  ngOnInit(): void {
    const characterName = this.route.snapshot.paramMap.get('characterName') ?? '';
    const personaName = this.route.snapshot.paramMap.get('personaName') ?? '';
    forkJoin({
      character: this.api.getCharacter(characterName),
      persona: this.api.getPersona(personaName)
    }).subscribe({
      next: ({ character, persona }) => {
        this.character.set(character);
        this.persona.set(persona);
        this.characterNotes = character.description ?? '';
        this.personaNotes = persona.description ?? '';
        const opening =
          character.openingMessage?.trim() ||
          `${character.name} turns toward you and waits for you to speak.`;
        this.messages.set([this.line('character', character.name, opening)]);
        this.shouldScroll = true;
      },
      error: () => this.error.set('Could not load this conversation. Go back and select both again.')
    });
  }

  ngAfterViewChecked(): void {
    if (!this.shouldScroll) {
      return;
    }
    this.shouldScroll = false;
    const el = this.thread?.nativeElement;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }

  send(): void {
    const text = this.draft.trim();
    const persona = this.persona();
    if (!text || !persona) {
      return;
    }
    this.messages.update((list) => [...list, this.line('persona', persona.name, text)]);
    this.draft = '';
    this.shouldScroll = true;
  }

  onComposerKey(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  clock(at: Date): string {
    return at.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  }

  saveNotes(): void {
    const character = this.character();
    const persona = this.persona();
    if (!character || !persona) {
      return;
    }
    this.busy.set(true);
    forkJoin({
      character: this.api.updateCharacter(character.name, { description: this.characterNotes }),
      persona: this.api.updatePersona(persona.name, { description: this.personaNotes })
    }).subscribe({
      next: () => {
        forkJoin({
          character: this.api.getCharacter(character.name),
          persona: this.api.getPersona(persona.name)
        }).subscribe({
          next: (loaded) => {
            this.character.set(loaded.character);
            this.persona.set(loaded.persona);
            this.busy.set(false);
            this.showProfile.set(false);
          },
          error: () => this.busy.set(false)
        });
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.error ?? 'Could not save notes.');
      }
    });
  }

  private line(speaker: ChatMessage['speaker'], name: string, text: string): ChatMessage {
    return { speaker, name, text, at: new Date() };
  }
}
