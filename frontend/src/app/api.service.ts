import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChatReply, WorldEntity } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private readonly http: HttpClient) {}

  listCharacterNames(): Observable<string[]> {
    return this.http.get<string[]>('/characters');
  }

  getCharacter(name: string): Observable<WorldEntity> {
    return this.http.get<WorldEntity>(`/characters/${encodeURIComponent(name)}`);
  }

  createCharacter(name: string, characterClass: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(
      `/create_new_character/${encodeURIComponent(name)}/${encodeURIComponent(characterClass)}`,
      {}
    );
  }

  updateCharacter(name: string, body: Record<string, unknown>): Observable<{ message: string }> {
    return this.http.put<{ message: string }>(`/update_character/${encodeURIComponent(name)}`, body);
  }

  listPersonaNames(): Observable<string[]> {
    return this.http.get<string[]>('/personas');
  }

  getPersona(name: string): Observable<WorldEntity> {
    return this.http.get<WorldEntity>(`/personas/${encodeURIComponent(name)}`);
  }

  createPersona(name: string, characterClass: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(
      `/create_new_persona/${encodeURIComponent(name)}/${encodeURIComponent(characterClass)}`,
      {}
    );
  }

  updatePersona(name: string, body: Record<string, unknown>): Observable<{ message: string }> {
    return this.http.put<{ message: string }>(`/update_persona/${encodeURIComponent(name)}`, body);
  }

  sendChat(body: {
    message: string;
    character: string;
    persona: string;
    conversation_id?: string;
    reply_length?: 'short' | 'medium' | 'long';
  }): Observable<ChatReply> {
    return this.http.post<ChatReply>('/chat', body);
  }

  deleteConversation(conversationId: string): Observable<void> {
    return this.http.delete<void>(`/conversations/${encodeURIComponent(conversationId)}`);
  }
}
