import { Injectable } from '@angular/core';

const STORAGE_KEY = 'narrative-openai-api-key';
const STORAGE_SOURCE = 'narrative-openai-key-source';

export type OpenAiKeySource = 'user' | 'env' | '';

@Injectable({ providedIn: 'root' })
export class OpenAiKeyStore {
  get(): string {
    return (sessionStorage.getItem(STORAGE_KEY) || '').trim();
  }

  has(): boolean {
    return this.get().length > 0;
  }

  source(): OpenAiKeySource {
    const stored = (sessionStorage.getItem(STORAGE_SOURCE) || '').trim();
    if (stored === 'env' || stored === 'user') {
      return stored;
    }
    return this.has() ? 'user' : '';
  }

  canChat(): boolean {
    return this.source() === 'env' || this.has();
  }

  set(key: string): void {
    const trimmed = key.trim();
    if (!trimmed) {
      this.clear();
      return;
    }
    sessionStorage.setItem(STORAGE_KEY, trimmed);
    sessionStorage.setItem(STORAGE_SOURCE, 'user');
  }

  useServerKey(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    sessionStorage.setItem(STORAGE_SOURCE, 'env');
  }

  clear(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    sessionStorage.removeItem(STORAGE_SOURCE);
  }

  looksValid(key: string): boolean {
    const trimmed = key.trim();
    return trimmed.startsWith('sk-') && trimmed.length >= 20;
  }
}
