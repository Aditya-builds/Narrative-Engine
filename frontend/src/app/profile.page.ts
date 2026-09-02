import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ChatStore } from './chat-store';

@Component({
  selector: 'app-profile-page',
  imports: [RouterLink],
  template: `
    <section class="page">
      <p class="eyebrow">Profile</p>
      <h1>You</h1>
      <p class="lede">Jump back to your characters, personas, or saved chats.</p>
      <div class="chat-list">
        <a class="chat-card" routerLink="/api-key">
          <span class="chat-card-copy">
            <strong>OpenAI API key</strong>
            <small>Required before chatting. Stored in this browser tab only.</small>
          </span>
          <span class="chat-card-chevron">›</span>
        </a>
        <a class="chat-card" routerLink="/">
          <span class="chat-card-copy">
            <strong>My Characters</strong>
            <small>Browse and start a conversation</small>
          </span>
          <span class="chat-card-chevron">›</span>
        </a>
        <a class="chat-card" routerLink="/chats">
          <span class="chat-card-copy">
            <strong>My Chats</strong>
            <small>{{ chatCount }} saved {{ chatCount === 1 ? 'chat' : 'chats' }}</small>
          </span>
          <span class="chat-card-chevron">›</span>
        </a>
        <a class="chat-card" routerLink="/personas">
          <span class="chat-card-copy">
            <strong>My Personas</strong>
            <small>Who you speak as</small>
          </span>
          <span class="chat-card-chevron">›</span>
        </a>
      </div>
    </section>
  `
})
export class ProfilePage {
  readonly chatCount = inject(ChatStore).list().length;
}
