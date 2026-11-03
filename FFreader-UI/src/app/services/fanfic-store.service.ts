import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, filter, finalize, interval, Observable, Subject, switchMap, tap } from 'rxjs';

import { Fanfic } from '../models/fanfic.model';
import { FanficService } from './fanfic.service';

const POLL_INTERVAL_MS = 4000;

/** Holds the current page of stories and refreshes it while any of them is still being parsed. */
@Injectable({ providedIn: 'root' })
export class FanficStore {
  private readonly api = inject(FanficService);
  private readonly reload = new Subject<{ silent: boolean }>();

  readonly fanfics = signal<Fanfic[]>([]);
  readonly totalElements = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly loading = signal(true);
  readonly failed = signal(false);

  private readonly hasActiveJobs = computed(() =>
    this.fanfics().some((f) => f.status === 'PENDING' || f.status === 'PARSING')
  );

  constructor() {
    const destroyRef = inject(DestroyRef);

    // switchMap drops an older response that would otherwise overwrite a newer one.
    this.reload
      .pipe(
        switchMap(({ silent }) => this.fetchPage(silent)),
        takeUntilDestroyed(destroyRef)
      )
      .subscribe();

    interval(POLL_INTERVAL_MS)
      .pipe(
        filter(() => this.hasActiveJobs()),
        takeUntilDestroyed(destroyRef)
      )
      .subscribe(() => this.reload.next({ silent: true }));

    this.refresh();
  }

  refresh(): void {
    this.reload.next({ silent: false });
  }

  setPage(pageIndex: number, pageSize: number): void {
    this.pageIndex.set(pageIndex);
    this.pageSize.set(pageSize);
    this.refresh();
  }

  add(url: string): Observable<Fanfic> {
    return this.api.create(url).pipe(
      tap(() => {
        this.pageIndex.set(0);
        this.refresh();
      })
    );
  }

  remove(id: string): Observable<void> {
    return this.api.delete(id).pipe(tap(() => this.refresh()));
  }

  download(id: string): Observable<Blob> {
    return this.api.download(id);
  }

  private fetchPage(silent: boolean): Observable<unknown> {
    if (!silent) {
      this.loading.set(true);
    }
    return this.api.list(this.pageIndex(), this.pageSize()).pipe(
      tap((page) => {
        // Deleting the only story on the last page leaves this page empty.
        if (page.content.length === 0 && page.page > 0) {
          this.pageIndex.set(Math.max(page.totalPages - 1, 0));
          this.refresh();
          return;
        }
        this.fanfics.set(page.content);
        this.totalElements.set(page.totalElements);
        this.failed.set(false);
      }),
      catchError(() => {
        // A failed background refresh keeps showing the last good data.
        if (!silent) {
          this.failed.set(true);
        }
        return EMPTY;
      }),
      finalize(() => {
        if (!silent) {
          this.loading.set(false);
        }
      })
    );
  }
}
