import { ChangeDetectionStrategy, Component, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  FormGroupDirective,
  ReactiveFormsModule,
  ValidationErrors
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';

import { FanficStore } from '../../services/fanfic-store.service';

// Matches CreateFanficRequest in the API.
const STORY_URL = /^https?:\/\/(www\.)?fanfiction\.net\/s\/\d+(\/.*)?$/;

function storyUrl(control: AbstractControl<string>): ValidationErrors | null {
  const url = control.value.trim();
  return url && !STORY_URL.test(url) ? { storyUrl: true } : null;
}

@Component({
  selector: 'app-add-fanfic-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './add-fanfic-form.component.html',
  styleUrl: './add-fanfic-form.component.scss'
})
export class AddFanficFormComponent {
  private readonly store = inject(FanficStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly formDirective = viewChild.required(FormGroupDirective);

  readonly url = new FormControl('', { nonNullable: true, validators: storyUrl });
  readonly form = new FormGroup({ url: this.url });

  readonly submitting = signal(false);
  readonly serverError = signal<string | null>(null);
  readonly urlValue = toSignal(this.url.valueChanges, { initialValue: '' });

  submit(): void {
    const url = this.url.value.trim();
    if (!url || this.url.hasError('storyUrl') || this.submitting()) {
      this.url.markAsTouched();
      return;
    }
    this.submitting.set(true);
    this.serverError.set(null);
    this.store
      .add(url)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          this.formDirective().resetForm({ url: '' });
          this.snackBar.open('Story added to the queue', 'Dismiss', { duration: 3000 });
        },
        error: (err) => {
          const message = err?.error?.message || 'Could not add that story. Please try again.';
          this.serverError.set(message);
          this.url.setErrors({ server: true });
        }
      });
  }
}
