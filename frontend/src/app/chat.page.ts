import { AfterViewChecked, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, of, firstValueFrom } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from './api.service';
import { ChatStore, StoredChat } from './chat-store';
import { ChatMessage, ReplyLength, WorldEntity } from './models';
import { OpenAiKeyStore } from './openai-key.store';
import { PortraitComponent } from './portrait';
import { messageFromHttpError } from './http-error';

@Component({
  selector: 'app-chat-page',
  imports: [FormsModule, RouterLink, PortraitComponent],
  templateUrl: './chat.page.html'
})
export class ChatPage implements OnInit, AfterViewChecked, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly store = inject(ChatStore);
  private readonly keys = inject(OpenAiKeyStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private shouldScroll = false;

  @ViewChild('thread') private thread?: ElementRef<HTMLElement>;

  readonly character = signal<WorldEntity | null>(null);
  readonly persona = signal<WorldEntity | null>(null);
  readonly personas = signal<WorldEntity[]>([]);
  readonly messages = signal<ChatMessage[]>([]);
  readonly error = signal('');
  readonly busy = signal(false);
  readonly waiting = signal(false);
  readonly typing = signal(false);
  readonly showInfo = signal(false);
  readonly showPersonas = signal(false);
  readonly replySizes: ReplyLength[] = ['short', 'medium', 'long'];
  replyLength: ReplyLength = 'medium';
  draft = '';
  characterNotes = '';
  personaNotes = '';
  private conversationId = '';
  private typeTimer?: ReturnType<typeof setInterval>;
  private destroyed = false;
  private loadGen = 0;
  private pendingReply = '';
  private saveQueue: Promise<unknown> = Promise.resolve();

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      this.openThread(params.get('characterName') ?? '', params.get('personaName') ?? '');
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

  ngOnDestroy(): void {
    this.destroyed = true;
    this.loadGen += 1;
    this.stopTyping(true);
    this.persist();
  }

  started(): boolean {
    return this.messages().some((message) => message.speaker === 'persona');
  }

  canSwitchPersona(): boolean {
    return !this.started() && !this.busy() && this.personas().length > 0;
  }

  openPersonaPicker(): void {
    if (!this.canSwitchPersona()) {
      this.showPersonas.set(false);
      return;
    }
    this.showPersonas.set(true);
  }

  pickPersona(persona: WorldEntity): void {
    if (!this.canSwitchPersona() || persona.name === this.persona()?.name) {
      this.showPersonas.set(false);
      return;
    }
    this.persona.set(persona);
    this.personaNotes = persona.description ?? '';
    this.showPersonas.set(false);
    this.persist();
    const character = this.character();
    if (character) {
      void this.router.navigate(['/characters', character.name, 'personas', persona.name, 'chat'], {
        replaceUrl: true
      });
    }
  }

  setReplyLength(size: ReplyLength): void {
    this.replyLength = size;
    this.persist();
  }

  send(): void {
    const text = this.draft.trim();
    const persona = this.persona();
    const character = this.character();
    if (!text || !persona || !character || this.busy()) {
      return;
    }
    if (!this.keys.canChat()) {
      void this.router.navigate(['/api-key'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    this.error.set('');
    this.showPersonas.set(false);
    this.busy.set(true);
    this.waiting.set(true);
    this.messages.update((list) => [...list, this.line('persona', persona.name, text)]);
    this.persist();
    this.draft = '';
    this.shouldScroll = true;
    this.api
      .sendChat({
        message: text,
        character: character.name,
        persona: persona.name,
        conversation_id: this.conversationId || undefined,
        reply_length: this.replyLength
      })
      .subscribe({
        next: (reply) => {
          if (this.destroyed) {
            return;
          }
          this.conversationId = reply.conversation_id;
          this.waiting.set(false);
          this.pendingReply = reply.response || '...';
          this.typeReply(character.name, this.pendingReply);
        },
        error: (err) => {
          this.waiting.set(false);
          this.busy.set(false);
          this.draft = text;
          this.messages.update((list) => list.slice(0, -1));
          this.persist();
          this.error.set(
            messageFromHttpError(err, 'The character could not reply. Try again in a moment.')
          );
        }
      });
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
            this.showInfo.set(false);
          },
          error: () => this.busy.set(false)
        });
      },
        error: (err) => {
        this.busy.set(false);
        this.error.set(messageFromHttpError(err, 'Could not save notes.'));
      }
    });
  }

  private openThread(characterName: string, personaName: string): void {
    const gen = ++this.loadGen;
    if (this.character()?.name === characterName && this.messages().length) {
      if (this.started() || personaName === this.persona()?.name) {
        this.showPersonas.set(false);
        this.keepPersonaRoute(characterName, this.persona()?.name ?? personaName);
        return;
      }
      this.api.getPersona(personaName).subscribe({
        next: (persona) => {
          if (gen !== this.loadGen || this.destroyed) {
            return;
          }
          this.persona.set(persona);
          this.personaNotes = persona.description ?? '';
          this.persist();
        },
        error: () => this.error.set('Could not switch persona. Try again.')
      });
      return;
    }
    this.stopTyping(true);
    this.waiting.set(false);
    this.busy.set(false);
    this.error.set('');
    forkJoin({
      character: this.api.getCharacter(characterName),
      names: this.api.listPersonaNames().pipe(catchError(() => of([] as string[])))
    }).subscribe({
      next: ({ character, names }) => {
        if (gen !== this.loadGen || this.destroyed) {
          return;
        }
        this.character.set(character);
        this.characterNotes = character.description ?? '';
        this.loadPersonas(names);
        const local = this.store.load(character.name);
        this.api
          .getChatThread(character.name)
          .pipe(catchError(() => of(null)))
          .subscribe((remote) => {
            if (gen !== this.loadGen || this.destroyed) {
              return;
            }
            const stored = this.store.prefer(remote ? this.store.fromRemote(remote) : null, local);
            this.applyStoredThread(character, personaName, stored, gen);
          });
      },
      error: () =>
        this.error.set('Could not load this conversation. Go back and pick the character again.')
    });
  }

  private loadPersonas(names: string[]): void {
    if (names.length === 0) {
      this.personas.set([]);
      return;
    }
    forkJoin(names.map((name) => this.api.getPersona(name).pipe(catchError(() => of(null))))).subscribe((loaded) => {
      this.personas.set(loaded.filter((item): item is WorldEntity => item !== null));
    });
  }

  private loadActivePersona(characterName: string, personaName: string, gen: number): void {
    this.api.getPersona(personaName).subscribe({
      next: (persona) => {
        if (gen !== this.loadGen || this.destroyed) {
          return;
        }
        this.persona.set(persona);
        this.personaNotes = persona.description ?? '';
        this.persist();
        this.shouldScroll = true;
        this.keepPersonaRoute(characterName, persona.name);
      },
      error: () =>
        this.error.set('Could not load this conversation. Go back and pick the character again.')
    });
  }

  private keepPersonaRoute(characterName: string, personaName: string): void {
    const current = this.route.snapshot.paramMap.get('personaName');
    if (!personaName || current === personaName) {
      return;
    }
    void this.router.navigate(['/characters', characterName, 'personas', personaName, 'chat'], {
      replaceUrl: true
    });
  }

  private persist(): void {
    if (this.destroyed && !this.pendingReply) {
      return;
    }
    const character = this.character();
    const persona = this.persona();
    if (!character || !persona) {
      return;
    }
    const messages = this.messagesForSave(character.name);
    this.store.save(character.name, persona.name, this.conversationId, messages, this.replyLength);
    const stored = this.store.load(character.name);
    if (!stored) {
      return;
    }
    const payload = this.store.toRemote(character.name, stored);
    this.saveQueue = this.saveQueue
      .catch(() => undefined)
      .then(() =>
        firstValueFrom(this.api.saveChatThread(character.name, payload).pipe(catchError(() => of(null))))
      );
  }

  private messagesForSave(characterName: string): ChatMessage[] {
    const messages = this.messages();
    if (!this.pendingReply) {
      return messages;
    }
    if (!messages.length) {
      return [this.line('character', characterName, this.pendingReply)];
    }
    const next = [...messages];
    const last = next[next.length - 1];
    if (last.speaker === 'character') {
      next[next.length - 1] = { ...last, text: this.pendingReply };
      return next;
    }
    return [...next, this.line('character', characterName, this.pendingReply)];
  }

  private applyStoredThread(
    character: WorldEntity,
    personaName: string,
    stored: StoredChat | null,
    gen: number
  ): void {
    this.replyLength = stored?.replyLength ?? 'medium';
    const locked =
      stored?.messages.some((message) => message.speaker === 'persona') && stored.personaName
        ? stored.personaName
        : '';
    if (stored?.messages.length) {
      this.conversationId = stored.conversationId;
      this.messages.set(stored.messages);
    } else {
      this.conversationId = '';
      const opening =
        character.openingMessage?.trim() ||
        `${character.name} turns toward you and waits for you to speak.`;
      this.messages.set([this.line('character', character.name, opening)]);
    }
    this.loadActivePersona(character.name, locked || personaName, gen);
  }

  private line(speaker: ChatMessage['speaker'], name: string, text: string): ChatMessage {
    return { speaker, name, text, at: new Date() };
  }

  private typeReply(name: string, full: string): void {
    this.stopTyping();
    const text = full || '...';
    const bubble = this.line('character', name, '');
    this.typing.set(true);
    this.messages.update((list) => [...list, bubble]);
    this.persist();
    this.shouldScroll = true;
    let shown = 0;
    const chunk = Math.max(1, Math.ceil(text.length / 120));
    this.typeTimer = setInterval(() => {
      shown = Math.min(text.length, shown + chunk);
      const typed = text.slice(0, shown);
      this.messages.update((list) => {
        const next = [...list];
        next[next.length - 1] = { ...bubble, text: typed };
        return next;
      });
      this.shouldScroll = true;
      if (shown >= text.length) {
        this.pendingReply = '';
        this.stopTyping();
        this.persist();
        this.busy.set(false);
      }
    }, 16);
  }

  private stopTyping(flush = false): void {
    if (this.typeTimer) {
      clearInterval(this.typeTimer);
      this.typeTimer = undefined;
    }
    this.typing.set(false);
    if (flush && this.pendingReply) {
      const full = this.pendingReply;
      this.messages.update((list) => {
        if (!list.length) {
          return list;
        }
        const next = [...list];
        const last = next[next.length - 1];
        if (last.speaker === 'character') {
          next[next.length - 1] = { ...last, text: full };
        }
        return next;
      });
      this.persist();
      this.pendingReply = '';
    }
  }
}
