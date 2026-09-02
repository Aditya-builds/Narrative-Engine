export type ReplyLength = 'short' | 'medium' | 'long';

export interface WorldEntity {
  name: string;
  class?: string;
  rank?: string;
  gender?: string;
  age?: string;
  location?: string;
  description?: string;
  openingMessage?: string;
  files?: Record<string, unknown>;
}

export interface ChatMessage {
  speaker: 'character' | 'persona';
  name: string;
  text: string;
  at: Date;
}

export interface ChatReply {
  response: string;
  conversation_id: string;
  applied_state_changes?: string[];
}
