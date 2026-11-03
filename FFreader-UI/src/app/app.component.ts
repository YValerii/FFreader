import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { PageEvent } from '@angular/material/paginator';
import { finalize } from 'rxjs';

import { FanficService } from './services/fanfic.service';
import { Fanfic } from './models/fanfic.model';
import { FanficTableComponent } from './components/fanfic-table/fanfic-table.component';

const ACTIVE_STATUSES = new Set(['PENDING', 'PARSING']);
const POLL_INTERVAL_MS = 4000;

@Component({
  selector: 'app-root',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    CommonModule,
    FormsModule,
    MatToolbarModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatSnackBarModule,
    FanficTableComponent
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit, OnDestroy {
  urlInput = '';
  submitting = false;
  loadingList = false;

  fanfics: Fanfic[] = [];
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;

  private pollHandle?: ReturnType<typeof setInterval>;

  constructor(private fanficService: FanficService, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.loadPage();
    this.pollHandle = setInterval(() => {
      if (this.fanfics.some((f) => ACTIVE_STATUSES.has(f.status))) {
        this.loadPage(true);
      }
    }, POLL_INTERVAL_MS);
  }

  ngOnDestroy(): void {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
    }
  }

  submitUrl(): void {
    const url = this.urlInput.trim();
    if (!url) {
      return;
    }
    this.submitting = true;
    this.fanficService.create(url)
      .pipe(finalize(() => (this.submitting = false)))
      .subscribe({
        next: () => {
          this.urlInput = '';
          this.snackBar.open('Fanfic queued for parsing', 'Dismiss', { duration: 3000 });
          this.pageIndex = 0;
          this.loadPage();
        },
        error: (err) => {
          const message = err?.error?.message || 'Could not queue that URL for parsing';
          this.snackBar.open(message, 'Dismiss', { duration: 5000 });
        }
      });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadPage();
  }

  onDownload(fanfic: Fanfic): void {
    this.fanficService.download(fanfic.id).subscribe({
      next: (blob) => {
        const link = document.createElement('a');
        const objectUrl = URL.createObjectURL(blob);
        link.href = objectUrl;
        link.download = `${fanfic.title ?? fanfic.externalId}.pdf`;
        link.click();
        URL.revokeObjectURL(objectUrl);
      },
      error: () => this.snackBar.open('PDF is not ready yet', 'Dismiss', { duration: 3000 })
    });
  }

  onDelete(fanfic: Fanfic): void {
    if (!confirm(`Delete "${fanfic.title ?? fanfic.sourceUrl}"?`)) {
      return;
    }
    this.fanficService.delete(fanfic.id).subscribe({
      next: () => {
        this.snackBar.open('Fanfic deleted', 'Dismiss', { duration: 3000 });
        this.loadPage();
      },
      error: (err) => this.snackBar.open(err?.error?.message || 'Could not delete fanfic', 'Dismiss', { duration: 5000 })
    });
  }

  private loadPage(silent = false): void {
    if (!silent) {
      this.loadingList = true;
    }
    this.fanficService.list(this.pageIndex, this.pageSize)
      .pipe(finalize(() => (this.loadingList = false)))
      .subscribe({
        next: (page) => {
          this.fanfics = page.content;
          this.totalElements = page.totalElements;
        },
        error: () => {
          if (!silent) {
            this.snackBar.open('Could not load fanfics', 'Dismiss', { duration: 3000 });
          }
        }
      });
  }
}
