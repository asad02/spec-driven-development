import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { FeatureAdminService } from '../../core/features/feature-admin.service';
import { AppError } from '../../core/models/api-error.model';
import { AuditEntry } from '../../core/models/feature.model';

@Component({
  selector: 'app-audit-log',
  imports: [
    DatePipe, RouterLink, MatToolbarModule, MatTableModule, MatButtonModule,
    MatIconModule, MatProgressBarModule,
  ],
  templateUrl: './audit-log.component.html',
  styleUrl: './audit-log.component.scss',
})
export class AuditLogComponent implements OnInit {
  private readonly api = inject(FeatureAdminService);
  readonly auth = inject(AuthService);

  readonly columns = ['at', 'user', 'action', 'feature', 'detail'] as const;
  readonly entries = signal<AuditEntry[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loading.set(true);
    this.api.audit(200).subscribe({
      next: (entries) => { this.entries.set(entries); this.loading.set(false); },
      error: (error: AppError) => { this.errorMessage.set(error.message); this.loading.set(false); },
    });
  }

  /** A refused attempt is as interesting as a successful change — mark it. */
  isRefusal(entry: AuditEntry): boolean {
    return entry.action.endsWith('_REFUSED');
  }

  logout(): void { this.auth.logout(); }
}
