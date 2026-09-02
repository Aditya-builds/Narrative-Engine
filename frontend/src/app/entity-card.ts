import { Component, input, output } from '@angular/core';
import { WorldEntity } from './models';
import { metaLine, traitsOf } from './entity-info';
import { PortraitComponent } from './portrait';

@Component({
  selector: 'app-entity-card',
  imports: [PortraitComponent],
  template: `
    <button type="button" class="entity-card" (click)="choose.emit(entity())">
      <app-portrait [kind]="kind()" [name]="entity().name" size="card" />
      <div class="entity-card-body">
        <strong>{{ entity().name }}</strong>
        <span class="card-kicker">{{ kicker }}</span>
        <p class="entity-card-copy">{{ entity().description || 'No description yet.' }}</p>
        @if (tags.length) {
          <div class="entity-chips">
            @for (tag of tags; track tag) {
              <span>{{ tag }}</span>
            }
          </div>
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

  get kicker(): string {
    return metaLine(this.entity()) || 'unknown';
  }

  get tags(): string[] {
    const entity = this.entity();
    return [entity.class, entity.gender, entity.rank ? `rank ${entity.rank}` : '', ...traitsOf(entity).slice(0, 2)]
      .filter((part): part is string => Boolean(part && part.trim()));
  }
}
