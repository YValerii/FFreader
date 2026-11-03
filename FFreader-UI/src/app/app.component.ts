import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { PageEvent } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { filter, switchMap } from 'rxjs';

import { AddFanficFormComponent } from './components/add-fanfic-form/add-fanfic-form.component';
import { ConfirmDialogComponent, ConfirmDialogData } from './components/confirm-dialog/confirm-dialog.component';
import { FanficListComponent } from './components/fanfic-list/fanfic-list.component';
import { Fanfic } from './models/fanfic.model';
import { FanficStore } from './services/fanfic-store.service';
import { ThemeService } from './services/theme.service';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, MatTooltipModule, AddFanficFormComponent, FanficListComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly themeService = inject(ThemeService);

  readonly store = inject(FanficStore);

  readonly nextThemeLabel = computed(() =>
    this.themeService.theme() === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'
  );
  readonly themeIcon = computed(() => (this.themeService.theme() === 'dark' ? 'light_mode' : 'dark_mode'));

  toggleTheme(): void {
    this.themeService.toggle();
  }

  onPageChange(event: PageEvent): void {
    this.store.setPage(event.pageIndex, event.pageSize);
  }

  onDownload(fanfic: Fanfic): void {
    this.store.download(fanfic.id).subscribe({
      next: (blob) => {
        const link = document.createElement('a');
        const objectUrl = URL.createObjectURL(blob);
        link.href = objectUrl;
        link.download = `${fanfic.title ?? fanfic.externalId}.pdf`;
        link.click();
        URL.revokeObjectURL(objectUrl);
      },
      error: () => this.notify('The PDF is not ready yet')
    });
  }

  onDelete(fanfic: Fanfic): void {
    const data: ConfirmDialogData = {
      title: 'Delete this story?',
      message: `“${fanfic.title ?? fanfic.sourceUrl}” and its PDF will be permanently removed.`,
      confirmLabel: 'Delete'
    };
    this.dialog
      .open(ConfirmDialogComponent, { data, width: '420px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => this.store.remove(fanfic.id))
      )
      .subscribe({
        next: () => this.notify('Story deleted'),
        error: (err) => this.notify(err?.error?.message || 'Could not delete the story')
      });
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'Dismiss', { duration: 5000 });
  }
}
