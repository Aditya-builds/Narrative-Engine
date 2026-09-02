import { Injectable } from '@angular/core';

const STORAGE_KEY = 'narrative-openai-api-key';

@Injectable({ providedIn: 'root' })
export class OpenAiKeyStore {
  get(): string {
    return (sessionStorage.getItem(STORAGE_KEY) || '').trim();
  }

  has(): boolean {
    return this.get().length > 0;
  }

  set(key: string): void {
    const trimmed = key.trim();
    if (!trimmed) {
      this.clear();
      return;
    }
    sessionStorage.setItem(STORAGE_KEY, trimmed);
  }

  clear(): void {
    sessionStorage.removeItem(STORAGE_KEY);
  }

  looksValid(key: string): boolean {
    const trimmed = key.trim();
    return trimmed.startsWith('sk-') && trimmed.length >= 20;
  }
}
