import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Fanfic } from '../../models/fanfic.model';

@Component({
  selector: 'app-fanfic-table',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatTooltipModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './fanfic-table.component.html',
  styleUrl: './fanfic-table.component.scss'
})
export class FanficTableComponent {
  @Input() fanfics: Fanfic[] = [];
  @Input() totalElements = 0;
  @Input() pageIndex = 0;
  @Input() pageSize = 10;
  @Input() loading = false;

  @Output() pageChange = new EventEmitter<PageEvent>();
  @Output() download = new EventEmitter<Fanfic>();
  @Output() deleteFanfic = new EventEmitter<Fanfic>();

  readonly displayedColumns = ['title', 'status', 'chapters', 'words', 'published', 'actions'];

  onPage(event: PageEvent): void {
    this.pageChange.emit(event);
  }

  statusColor(status: string): string {
    switch (status) {
      case 'PARSED': return 'primary';
      case 'FAILED': return 'warn';
      default: return '';
    }
  }

  isBusy(fanfic: Fanfic): boolean {
    return fanfic.status === 'PENDING' || fanfic.status === 'PARSING';
  }
}
