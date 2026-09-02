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

export interface LlmConfig {
  has_server_api_key: boolean;
}

export interface RemoteChatMessage {
  speaker: 'character' | 'persona';
  name: string;
  text: string;
  at: string;
}

export interface RemoteChatThread {
  conversation_id: string;
  character: string;
  persona_name: string;
  reply_length?: ReplyLength;
  updated_at?: string;
  messages: RemoteChatMessage[];
}

export interface RemoteChatPreview {
  character: string;
  persona_name: string;
  conversation_id: string;
  preview: string;
  at: string;
}
