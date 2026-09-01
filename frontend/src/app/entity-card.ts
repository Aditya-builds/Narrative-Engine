import { Component, HostListener, input, output, signal } from '@angular/core';
import { WorldEntity } from './models';
import { appearanceOf, metaLine, strengthsOf, traitsOf } from './entity-info';
import { PortraitComponent } from './portrait';

@Component({
  selector: 'app-entity-card',
  imports: [PortraitComponent],
  template: `
    <button
      type="button"
      class="entity-card"
      [class.entity-card-open]="open()"
      (click)="onClick()">
      <app-portrait [kind]="kind()" [name]="entity().name" size="card" />
      <div class="entity-card-face">
        <span class="card-kicker">{{ kicker }}</span>
        <strong>{{ entity().name }}</strong>
      </div>
      <div class="entity-card-more">
        <span class="card-kicker">{{ kicker }}</span>
        <strong>{{ entity().name }}</strong>
        <p class="entity-card-copy">{{ entity().description || 'No description yet.' }}</p>
        @if (details.length) {
          <p class="entity-card-facts">{{ details.join(' · ') }}</p>
        }
        @if (look) {
          <p class="entity-card-facts">{{ look }}</p>
        }
        @if (traits.length) {
          <div class="entity-chips">
            @for (trait of traits; track trait) {
              <span>{{ trait }}</span>
            }
          </div>
        }
        @if (strengths.length) {
          <p class="entity-card-facts">{{ strengths.join(' · ') }}</p>
        }
        <span class="entity-card-action">{{ actionLabel() }}</span>
      </div>
    </button>
  `
})
export class EntityCardComponent {
  readonly kind = input.required<'character' | 'persona'>();
  readonly entity = input.required<WorldEntity>();
  readonly actionLabel = input('Open');
  readonly choose = output<WorldEntity>();
  readonly open = signal(false);

  get kicker(): string {
    return metaLine(this.entity()) || 'unknown';
  }

  get traits(): string[] {
    return traitsOf(this.entity());
  }

  get look(): string {
    return appearanceOf(this.entity());
  }

  get strengths(): string[] {
    return strengthsOf(this.entity());
  }

  get details(): string[] {
    const entity = this.entity();
    return [entity.gender, entity.age ? `age ${entity.age}` : '']
      .filter((part): part is string => Boolean(part && part.trim()));
  }

  onClick(): void {
    if (this.touchDevice() && !this.open()) {
      this.open.set(true);
      return;
    }
    this.choose.emit(this.entity());
  }

  @HostListener('mouseleave')
  onLeave(): void {
    this.open.set(false);
  }

  private touchDevice(): boolean {
    return typeof window !== 'undefined' && window.matchMedia('(hover: none)').matches;
  }
}
