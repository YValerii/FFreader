import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';

import { Fanfic } from '../../models/fanfic.model';
import { StatusBadgeComponent } from '../status-badge/status-badge.component';

@Component({
  selector: 'app-fanfic-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    StatusBadgeComponent
  ],
  templateUrl: './fanfic-card.component.html',
  styleUrl: './fanfic-card.component.scss'
})
export class FanficCardComponent {
  readonly fanfic = input.required<Fanfic>();

  readonly download = output<void>();
  readonly delete = output<void>();

  readonly tags = computed(() => {
    const { rating, language, genre } = this.fanfic();
    return [rating && `Rated ${rating}`, language, genre].filter(Boolean).join(' · ');
  });

  readonly chapters = computed(() => {
    const { loadedChaptersCount: loaded, chaptersCount: total } = this.fanfic();
    if (total == null) {
      return null;
    }
    const label = total === 1 ? 'chapter' : 'chapters';
    return loaded != null && loaded !== total ? `${loaded} of ${total} ${label}` : `${total} ${label}`;
  });

  readonly downloadHint = computed(() =>
    this.fanfic().downloadReady ? 'Download PDF' : 'The PDF will be available once parsing finishes'
  );
}
