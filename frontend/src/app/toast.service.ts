import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly message = signal('');
  private timer: ReturnType<typeof setTimeout> | undefined;

  show(text: string): void {
    const trimmed = text.trim();
    if (!trimmed) {
      return;
    }
    this.message.set(trimmed);
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.message.set(''), 5000);
  }

  dismiss(): void {
    clearTimeout(this.timer);
    this.message.set('');
  }
}
