import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSort, MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { EMPTY, Subject, catchError, debounceTime, distinctUntilChanged, switchMap, tap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { BACKEND_LABELS, BackendProvider } from '../../core/features/feature.model';
import { FeatureService } from '../../core/features/feature.service';
import { AppError } from '../../core/models/api-error.model';
import { SortDirection, SortField, User, UserQuery } from '../../core/models/user.model';
import { UserFormDialogComponent, UserFormDialogData } from './user-form-dialog.component';
import { UserService } from './user.service';

@Component({
  selector: 'app-user-list',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatToolbarModule,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressBarModule,
    MatDialogModule,
    MatTooltipModule,
  ],
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.scss',
})
export class UserListComponent implements OnInit {
  private readonly userService = inject(UserService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);
  readonly features = inject(FeatureService);

  readonly displayedColumns = ['firstName', 'lastName', 'email', 'phone', 'createdAt'] as const;
  readonly pageSizeOptions = [10, 20, 50, 100];

  readonly users = signal<User[]>([]);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly query = signal<UserQuery>({
    page: 0,
    size: 20,
    sort: 'createdAt',
    direction: 'desc',
    search: '',
  });

  /** The term the currently displayed results were fetched with. */
  readonly appliedSearch = signal('');

  readonly isEmpty = computed(() => !this.loading() && !this.errorMessage() && this.users().length === 0);
  readonly isFilteredEmpty = computed(() => this.isEmpty() && this.appliedSearch().length > 0);

  readonly searchControl = new FormControl('', { nonNullable: true });

  private readonly load$ = new Subject<UserQuery>();

  ngOnInit(): void {
    this.features
      .load()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        // The indicator is informational; failing to read it must not block the page.
        error: () => this.features.activeBackend.set(null),
      });

    this.load$
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.errorMessage.set(null);
        }),
        // switchMap cancels a request whose term is already stale, so results
        // can never arrive out of order and overwrite a newer search.
        switchMap((query) =>
          this.userService.list(query).pipe(
            catchError((error: AppError) => {
              this.loading.set(false);
              this.errorMessage.set(error.message);
              // Never leave stale rows on screen looking current.
              this.users.set([]);
              this.totalElements.set(0);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => {
        this.users.set(page.content);
        this.totalElements.set(page.totalElements);
        this.appliedSearch.set(this.query().search.trim());
        this.loading.set(false);
      });

    this.searchControl.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((term) => {
        // A new filter invalidates the current page position (FR-5).
        this.query.update((q) => ({ ...q, search: term, page: 0 }));
        this.reload();
      });

    this.reload();
  }

  reload(): void {
    this.load$.next(this.query());
  }

  onPage(event: PageEvent): void {
    const sizeChanged = event.pageSize !== this.query().size;
    this.query.update((q) => ({
      ...q,
      size: event.pageSize,
      page: sizeChanged ? 0 : event.pageIndex,
    }));
    this.reload();
  }

  onSort(sort: Sort): void {
    if (!sort.direction) {
      // Material's third click clears the sort; fall back to the default order.
      this.query.update((q) => ({ ...q, sort: 'createdAt', direction: 'desc', page: 0 }));
    } else {
      this.query.update((q) => ({
        ...q,
        sort: sort.active as SortField,
        direction: sort.direction as SortDirection,
        page: 0,
      }));
    }
    this.reload();
  }

  clearSearch(): void {
    this.searchControl.setValue('');
  }

  openCreate(): void {
    this.openDialog({ mode: 'create' });
  }

  openEdit(user: User): void {
    this.openDialog({ mode: 'edit', user });
  }

  logout(): void {
    // Clear the cached rights first: the next operator may have different ones,
    // and a stale canToggle would briefly offer a control they cannot use.
    this.features.reset();
    this.auth.logout();
  }

  /**
   * Flips the flag, then reloads. The reload is the point: it proves the
   * request was served by the newly selected backend rather than just recolouring
   * a control.
   */
  switchBackend(provider: BackendProvider): void {
    if (provider === this.features.activeBackend()) {
      return;
    }
    this.features
      .switchTo(provider)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.snackBar.open(`Now serving from ${BACKEND_LABELS[provider]}`, 'Dismiss', {
            duration: 4000,
          });
          this.reload();
        },
        error: () =>
          this.snackBar.open('Could not switch backend. The flag was left unchanged.', 'Dismiss', {
            duration: 6000,
          }),
      });
  }

  private openDialog(data: UserFormDialogData): void {
    this.dialog
      .open(UserFormDialogComponent, {
        data,
        width: '520px',
        disableClose: true,
        autoFocus: 'first-tabbable',
        restoreFocus: true,
      })
      .afterClosed()
      .subscribe((saved?: User) => {
        if (!saved) {
          return;
        }
        this.snackBar.open(
          data.mode === 'create' ? 'User created.' : 'User updated.',
          'Dismiss',
          { duration: 4000 },
        );
        this.reload();
      });
  }
}
