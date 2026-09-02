import { Component, HostListener, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from './api.service';
import { ChatStore, ChatThreadPreview } from './chat-store';
import { PortraitComponent } from './portrait';

@Component({
  selector: 'app-chats-page',
  imports: [PortraitComponent],
  templateUrl: './chats.page.html'
})
export class ChatsPage implements OnInit {
  private readonly store = inject(ChatStore);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly chats = signal<ChatThreadPreview[]>([]);
  readonly menuFor = signal('');

  ngOnInit(): void {
    this.refresh();
  }

  open(chat: ChatThreadPreview): void {
    this.menuFor.set('');
    void this.router.navigate(['/characters', chat.character, 'personas', chat.personaName, 'chat']);
  }

  toggleMenu(chat: ChatThreadPreview, event: Event): void {
    event.stopPropagation();
    this.menuFor.update((open) => (open === chat.character ? '' : chat.character));
  }

  remove(chat: ChatThreadPreview, event: Event): void {
    event.stopPropagation();
    if (!window.confirm(`Delete your chat with ${chat.character}? This cannot be undone.`)) {
      return;
    }
    const conversationId = this.store.remove(chat.character);
    if (conversationId) {
      this.api.deleteConversation(conversationId).subscribe({ error: () => undefined });
    }
    this.menuFor.set('');
    this.refresh();
  }

  @HostListener('document:click')
  closeMenu(): void {
    this.menuFor.set('');
  }

  private refresh(): void {
    this.chats.set(this.store.list());
  }
}
