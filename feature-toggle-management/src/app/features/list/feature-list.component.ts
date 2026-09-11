import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { FeatureAdminService } from '../../core/features/feature-admin.service';
import { AppError } from '../../core/models/api-error.model';
import { Feature, KNOWN_ROLES } from '../../core/models/feature.model';

@Component({
  selector: 'app-feature-list',
  imports: [
    ReactiveFormsModule, RouterLink, MatToolbarModule, MatCardModule, MatButtonModule,
    MatIconModule, MatChipsModule, MatSlideToggleModule, MatFormFieldModule,
    MatInputModule, MatProgressBarModule, MatDialogModule, MatTooltipModule,
  ],
  templateUrl: './feature-list.component.html',
  styleUrl: './feature-list.component.scss',
})
export class FeatureListComponent implements OnInit {
  private readonly api = inject(FeatureAdminService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);

  readonly roles = KNOWN_ROLES;
  readonly features = signal<Feature[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly creating = signal(false);

  readonly total = computed(() => this.features().length);

  readonly createForm = this.fb.nonNullable.group({
    uid: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]+$/)]],
    description: [''],
    enabled: [false],
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.list().subscribe({
      next: (features) => { this.features.set(features); this.loading.set(false); },
      error: (error: AppError) => { this.errorMessage.set(error.message); this.loading.set(false); },
    });
  }

  toggle(feature: Feature, enabled: boolean): void {
    this.api.update(feature.uid, {
      uid: feature.uid, description: feature.description, enabled, roles: feature.roles,
    }).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.snackBar.open(`${updated.uid} is now ${enabled ? 'on' : 'off'}`, 'Dismiss', { duration: 3000 });
      },
      error: (error: AppError) => this.refuse(error),
    });
  }

  hasRole(feature: Feature, role: string): boolean {
    return feature.roles.includes(role);
  }

  toggleRole(feature: Feature, role: string, grant: boolean): void {
    const call = grant ? this.api.grantRole(feature.uid, role) : this.api.revokeRole(feature.uid, role);
    call.subscribe({
      next: (updated) => {
        this.replace(updated);
        this.snackBar.open(`${grant ? 'Granted' : 'Revoked'} ${role} on ${updated.uid}`, 'Dismiss', { duration: 3000 });
      },
      error: (error: AppError) => this.refuse(error),
    });
  }

  create(): void {
    if (this.createForm.invalid || this.creating()) return;
    this.creating.set(true);
    const { uid, description, enabled } = this.createForm.getRawValue();
    this.api.create({ uid, description, enabled, roles: [] }).subscribe({
      next: (created) => {
        this.creating.set(false);
        this.createForm.reset({ uid: '', description: '', enabled: false });
        this.features.update((list) => [...list, created].sort((a, b) => a.uid.localeCompare(b.uid)));
        this.snackBar.open(`Created ${created.uid}. It has no effect until code reads it.`, 'Dismiss', { duration: 6000 });
      },
      error: (error: AppError) => { this.creating.set(false); this.refuse(error); },
    });
  }

  remove(feature: Feature): void {
    if (!confirm(`Delete feature "${feature.uid}"? This cannot be undone.`)) return;
    this.api.delete(feature.uid).subscribe({
      next: () => {
        this.features.update((list) => list.filter((f) => f.uid !== feature.uid));
        this.snackBar.open(`Deleted ${feature.uid}`, 'Dismiss', { duration: 3000 });
      },
      error: (error: AppError) => this.refuse(error),
    });
  }

  propertyEntries(feature: Feature): { key: string; value: string | null }[] {
    return Object.entries(feature.properties ?? {}).map(([key, value]) => ({ key, value }));
  }

  logout(): void { this.auth.logout(); }

  private replace(updated: Feature): void {
    this.features.update((list) => list.map((f) => (f.uid === updated.uid ? updated : f)));
  }

  /**
   * A guardrail refusal is not a failure to apologise for — it is the system
   * telling the operator what would have broken. Show it prominently and for
   * long enough to read.
   */
  private refuse(error: AppError): void {
    const guardrail = error.code === 'FEATURE_PROTECTED';
    this.snackBar.open(error.message, 'Dismiss', { duration: guardrail ? 10000 : 5000 });
    this.reload();
  }
}
