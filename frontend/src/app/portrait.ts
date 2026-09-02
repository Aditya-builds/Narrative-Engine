import { Component, input, signal } from '@angular/core';

@Component({
  selector: 'app-portrait',
  template: `
    <div class="portrait" [class.portrait-card]="size() === 'card'" [class.portrait-avatar]="size() === 'avatar'" [class.portrait-detail]="size() === 'detail'" [class.portrait-thumb]="size() === 'thumb'">
      @if (!broken()) {
        <img [src]="src()" [alt]="name()" (error)="broken.set(true)" />
      } @else {
        <span class="portrait-fallback">{{ initial() }}</span>
      }
    </div>
  `
})
export class PortraitComponent {
  readonly kind = input<'character' | 'persona'>('character');
  readonly name = input.required<string>();
  readonly size = input<'card' | 'avatar' | 'detail' | 'thumb'>('card');
  readonly broken = signal(false);

  src(): string {
    const root = this.kind() === 'persona' ? 'personas' : 'characters';
    return `/${root}/${encodeURIComponent(this.name())}/portrait?v=2`;
  }

  initial(): string {
    return this.name().slice(0, 1).toUpperCase();
  }
}
