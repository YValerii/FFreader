import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { Fanfic } from '../../models/fanfic.model';
import { FanficCardComponent } from '../fanfic-card/fanfic-card.component';

@Component({
  selector: 'app-fanfic-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, MatPaginatorModule, MatProgressBarModule, FanficCardComponent],
  templateUrl: './fanfic-list.component.html',
  styleUrl: './fanfic-list.component.scss'
})
export class FanficListComponent {
  readonly fanfics = input.required<Fanfic[]>();
  readonly totalElements = input.required<number>();
  readonly pageIndex = input.required<number>();
  readonly pageSize = input.required<number>();
  readonly loading = input.required<boolean>();
  readonly failed = input.required<boolean>();

  readonly pageChange = output<PageEvent>();
  readonly retry = output<void>();
  readonly download = output<Fanfic>();
  readonly delete = output<Fanfic>();

  readonly pageSizeOptions = [5, 10, 25, 50];
  readonly skeletons = [1, 2, 3];

  readonly countLabel = computed(() => {
    const total = this.totalElements();
    return total === 1 ? '1 story' : `${total} stories`;
  });
}
