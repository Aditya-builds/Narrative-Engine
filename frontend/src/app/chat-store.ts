import { Injectable } from '@angular/core';
import { ChatMessage, RemoteChatPreview, RemoteChatThread, ReplyLength } from './models';

export interface ChatThreadPreview {
  character: string;
  personaName: string;
  conversationId: string;
  preview: string;
  at: Date;
}

export interface StoredChat {
  conversationId: string;
  personaName: string;
  replyLength: ReplyLength;
  updatedAt?: Date;
  messages: ChatMessage[];
}

interface StoredThread {
  conversationId: string;
  personaName: string;
  replyLength?: ReplyLength;
  updatedAt?: string;
  messages: Array<Omit<ChatMessage, 'at'> & { at: string }>;
}

@Injectable({ providedIn: 'root' })
export class ChatStore {
  load(character: string): StoredChat | null {
    return this.read(this.key(character)) ?? this.migrate(character);
  }

  save(
    character: string,
    personaName: string,
    conversationId: string,
    messages: ChatMessage[],
    replyLength: ReplyLength = 'medium'
  ): void {
    localStorage.setItem(
      this.key(character),
      JSON.stringify({
        conversationId,
        personaName,
        replyLength,
        updatedAt: new Date().toISOString(),
        messages: messages.map((message) => ({
          ...message,
          at: message.at.toISOString()
        }))
      })
    );
  }

  latestPersona(character: string): string {
    return this.previewOf(character)?.personaName ?? '';
  }

  list(): ChatThreadPreview[] {
    const names = new Set<string>();
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (!key) {
        continue;
      }
      if (key.startsWith('narrative-chat-thread:')) {
        names.add(key.slice('narrative-chat-thread:'.length).split(':')[0] ?? '');
      } else if (key.startsWith('narrative-chat:')) {
        names.add(key.slice('narrative-chat:'.length));
      }
    }
    return [...names]
      .map((name) => this.previewOf(name))
      .filter((item): item is ChatThreadPreview => item !== null)
      .sort((left, right) => right.at.getTime() - left.at.getTime());
  }

  private previewOf(character: string): ChatThreadPreview | null {
    if (!character) {
      return null;
    }
    const stored = this.load(character);
    if (!stored?.messages.some((message) => message.speaker === 'persona')) {
      return null;
    }
    const last = stored.messages[stored.messages.length - 1];
    return {
      character,
      personaName: stored.personaName,
      conversationId: stored.conversationId,
      preview: last?.text?.trim() || 'Open this chat',
      at: last?.at ?? new Date()
    };
  }

  remove(character: string): string {
    const stored = this.load(character);
    localStorage.removeItem(this.key(character));
    const prefix = `narrative-chat-thread:${character}:`;
    const stale: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key?.startsWith(prefix)) {
        stale.push(key);
      }
    }
    for (const key of stale) {
      localStorage.removeItem(key);
    }
    return stored?.conversationId ?? '';
  }

  toRemote(character: string, stored: StoredChat): RemoteChatThread {
    return {
      conversation_id: stored.conversationId,
      character,
      persona_name: stored.personaName,
      reply_length: stored.replyLength,
      updated_at: new Date().toISOString(),
      messages: stored.messages.map((message) => ({
        speaker: message.speaker,
        name: message.name,
        text: message.text,
        at: message.at.toISOString()
      }))
    };
  }

  fromRemote(thread: RemoteChatThread): StoredChat {
    const length = thread.reply_length;
    return {
      conversationId: thread.conversation_id || '',
      personaName: thread.persona_name || '',
      replyLength: length === 'short' || length === 'long' ? length : 'medium',
      updatedAt: thread.updated_at ? new Date(thread.updated_at) : undefined,
      messages: (thread.messages || []).map((message) => ({
        speaker: message.speaker,
        name: message.name,
        text: message.text,
        at: new Date(message.at)
      }))
    };
  }

  previewFromRemote(item: RemoteChatPreview): ChatThreadPreview {
    return {
      character: item.character,
      personaName: item.persona_name,
      conversationId: item.conversation_id,
      preview: item.preview,
      at: item.at ? new Date(item.at) : new Date()
    };
  }

  prefer(remote: StoredChat | null, local: StoredChat | null): StoredChat | null {
    if (remote && local) {
      const remoteAt = remote.updatedAt?.getTime() ?? 0;
      const localAt = local.updatedAt?.getTime() ?? 0;
      if (remoteAt !== localAt) {
        return remoteAt > localAt ? remote : local;
      }
      return remote.messages.length >= local.messages.length ? remote : local;
    }
    return remote ?? local;
  }

  mergePreviews(remote: ChatThreadPreview[], local: ChatThreadPreview[]): ChatThreadPreview[] {
    const byCharacter = new Map<string, ChatThreadPreview>();
    for (const item of [...local, ...remote]) {
      const previous = byCharacter.get(item.character);
      if (!previous || item.at.getTime() >= previous.at.getTime()) {
        byCharacter.set(item.character, item);
      }
    }
    return [...byCharacter.values()].sort((left, right) => right.at.getTime() - left.at.getTime());
  }

  private migrate(character: string): StoredChat | null {
    const prefix = `narrative-chat-thread:${character}:`;
    let best: StoredChat | null = null;
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (!key?.startsWith(prefix)) {
        continue;
      }
      const loaded = this.read(key);
      if (!loaded?.messages.length) {
        continue;
      }
      if (!best || loaded.messages.length >= best.messages.length) {
        best = {
          ...loaded,
          personaName: loaded.personaName || key.slice(prefix.length)
        };
      }
    }
    if (best) {
      this.save(character, best.personaName, best.conversationId, best.messages, best.replyLength);
    }
    return best;
  }

  private read(key: string): StoredChat | null {
    const raw = localStorage.getItem(key);
    if (!raw) {
      return null;
    }
    try {
      const stored = JSON.parse(raw) as StoredThread;
      return {
        conversationId: stored.conversationId || '',
        personaName: stored.personaName || '',
        replyLength: stored.replyLength === 'short' || stored.replyLength === 'long' ? stored.replyLength : 'medium',
        updatedAt: stored.updatedAt ? new Date(stored.updatedAt) : undefined,
        messages: (stored.messages || []).map((message) => ({
          speaker: message.speaker,
          name: message.name,
          text: message.text,
          at: new Date(message.at)
        }))
      };
    } catch {
      return null;
    }
  }

  private key(character: string): string {
    return `narrative-chat:${character}`;
  }
}
