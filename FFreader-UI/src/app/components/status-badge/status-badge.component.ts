import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { FanficStatus } from '../../models/fanfic.model';

const STATUS_DISPLAY: Record<FanficStatus, { label: string; icon: string }> = {
  PENDING: { label: 'Queued', icon: 'schedule' },
  PARSING: { label: 'Parsing', icon: 'sync' },
  PARSED: { label: 'Ready', icon: 'check_circle' },
  PARSED_PARTIALLY: { label: 'Partial', icon: 'warning' },
  FAILED: { label: 'Failed', icon: 'error' }
};

/** Pairs each colour with an icon and a label, so status never depends on colour alone. */
@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
  template: `
    <span class="badge" [class]="'badge badge--' + status().toLowerCase()">
      <mat-icon aria-hidden="true">{{ display().icon }}</mat-icon>
      {{ display().label }}
    </span>
  `,
  styleUrl: './status-badge.component.scss'
})
export class StatusBadgeComponent {
  readonly status = input.required<FanficStatus>();
  readonly display = computed(() => STATUS_DISPLAY[this.status()]);
}
