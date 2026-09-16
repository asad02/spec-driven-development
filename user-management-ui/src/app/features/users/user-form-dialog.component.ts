import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AppError } from '../../core/models/api-error.model';
import { User, UserRequest } from '../../core/models/user.model';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/confirm-dialog.component';
import { UserService } from './user.service';

export interface UserFormDialogData {
  mode: 'create' | 'edit';
  user?: User;
}

/** Mirrors FR-1 exactly. The server re-enforces all of it — this is convenience, not security. */
const PHONE_PATTERN = /^\+?[0-9 ()-]{7,20}$/;

@Component({
  selector: 'app-user-form-dialog',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './user-form-dialog.component.html',
  styleUrl: './user-form-dialog.component.scss',
})
export class UserFormDialogComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly userService = inject(UserService);
  private readonly dialog = inject(MatDialog);
  readonly dialogRef = inject<MatDialogRef<UserFormDialogComponent, User | undefined>>(MatDialogRef);
  readonly data = inject<UserFormDialogData>(MAT_DIALOG_DATA);

  readonly submitting = signal(false);
  readonly formError = signal<string | null>(null);
  readonly isEdit = this.data.mode === 'edit';

  readonly form = this.fb.group({
    firstName: [
      this.data.user?.firstName ?? '',
      [Validators.required, Validators.maxLength(50)],
    ],
    lastName: [
      this.data.user?.lastName ?? '',
      [Validators.required, Validators.maxLength(50)],
    ],
    email: [
      this.data.user?.email ?? '',
      [Validators.required, Validators.email, Validators.maxLength(254)],
    ],
    phone: [
      this.data.user?.phone ?? '',
      [Validators.pattern(PHONE_PATTERN)],
    ],
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.formError.set(null);

    const raw = this.form.getRawValue();
    const request: UserRequest = {
      firstName: raw.firstName.trim(),
      lastName: raw.lastName.trim(),
      email: raw.email.trim(),
      // An empty phone is sent as null so the server clears it (FR-3).
      phone: raw.phone.trim() === '' ? null : raw.phone.trim(),
    };

    const call = this.isEdit
      ? this.userService.update(this.data.user!.id, request)
      : this.userService.create(request);

    call.subscribe({
      next: (saved) => this.dialogRef.close(saved),
      error: (error: AppError) => this.handleError(error),
    });
  }

  cancel(): void {
    if (!this.form.dirty) {
      this.dialogRef.close(undefined);
      return;
    }

    const data: ConfirmDialogData = {
      title: 'Discard changes?',
      message: 'This user has unsaved changes. Closing now will lose them.',
      confirmLabel: 'Discard',
      cancelLabel: 'Keep editing',
    };

    this.dialog
      .open(ConfirmDialogComponent, { data, width: '400px' })
      .afterClosed()
      .subscribe((discard) => {
        if (discard) {
          this.dialogRef.close(undefined);
        }
      });
  }

  private handleError(error: AppError): void {
    this.submitting.set(false);

    if (error.status === 409) {
      // The dialog stays open with everything the operator typed; the message
      // attaches to the field that caused it (functional spec §6.3).
      this.form.controls.email.setErrors({ duplicate: true });
      this.form.controls.email.markAsTouched();
      return;
    }

    if (error.status === 400 && error.fieldErrors.length > 0) {
      for (const fieldError of error.fieldErrors) {
        const control = this.form.get(fieldError.field);
        if (control) {
          control.setErrors({ server: fieldError.message });
          control.markAsTouched();
        }
      }
      this.formError.set('Please correct the highlighted fields.');
      return;
    }

    this.formError.set(error.message);
  }
}
