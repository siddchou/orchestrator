import { Component, AfterViewInit, ViewChild, ChangeDetectorRef, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink, NavigationEnd, Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, filter } from 'rxjs/operators';
import { Subject, Subscription } from 'rxjs';
import { JobService } from '@app/core/services/job.service';
import { StatusBadge } from '@app/shared/components/status-badge/status-badge';
import { ConfirmDialog } from '@app/shared/components/confirm-dialog/confirm-dialog';
import { RunJobDialog } from '@app/shared/components/run-job-dialog/run-job-dialog';
import { JobDefinition } from '@app/core/models/job.model';
import { downloadFile } from '@app/core/utils/file-utils';

@Component({
  selector: 'app-job-list',
  imports: [
    CommonModule, MatCardModule, MatTableModule, MatPaginatorModule, MatSortModule,
    MatInputModule, MatFormFieldModule, MatButtonModule, MatIconModule,
    MatChipsModule, MatSnackBarModule, MatDialogModule, MatProgressSpinnerModule,
    RouterLink,
  ],
  templateUrl: './job-list.component.html',
  styleUrl: './job-list.component.scss',
})
export class JobListComponent implements AfterViewInit, OnDestroy {
  private jobService = inject(JobService);
  private dialog = inject(MatDialog);
  private router = inject(Router);
  private cd = inject(ChangeDetectorRef);

  jobs: JobDefinition[] = [];
  displayedColumns = ['jobName', 'description', 'enabled', 'steps', 'schedule', 'actions'];
  totalElements = 0;
  page = 0;
  size = 20;

  isLoading = false;

  @ViewChild('searchInput', { read: HTMLInputElement })
  searchInput?: HTMLInputElement;

  private searchSubject = new Subject<string>();
  private routerSub?: Subscription;

  ngAfterViewInit() {
    this.loadJobs();

    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(() => {
      this.page = 0;
      this.loadJobs();
    });

    // Reload when navigating back from job detail
    this.routerSub = this.router.events.pipe(
      filter(e => e instanceof NavigationEnd)
    ).subscribe(() => this.loadJobs());
  }

  onSearchInput(value: string) {
    this.searchSubject.next(value);
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
    this.routerSub?.unsubscribe();
  }

  loadJobs() {
    const search = this.searchInput?.value || '';
    this.isLoading = true;
    this.cd.markForCheck();

    this.jobService.listJobs(this.page, this.size, search).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.jobs = res.data.content;
          this.totalElements = res.data.totalElements;
        }
        this.isLoading = false;
        this.cd.detectChanges();
      },
    });
  }

  search() {
    this.searchSubject.next(this.searchInput?.value || '');
  }

  setPage(index: number) {
    this.page = index;
    this.loadJobs();
  }

  toggleEnabled(job: JobDefinition) {
    this.jobService.toggleEnabled(job.jobId).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          job.enabled = res.data.enabled;
        }
        this.cd.detectChanges();
      },
    });
  }

  deleteJob(job: JobDefinition) {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Job',
        message: `Delete "${job.jobName}"? This cannot be undone.`,
        confirmButton: 'Delete',
      },
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.jobService.deleteJob(job.jobId).subscribe({
        next: () => this.loadJobs(),
      });
    });
  }

  triggerRun(job: JobDefinition) {
    this.dialog.open(RunJobDialog, {
      data: { job },
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.jobService.triggerRun(job.jobId).subscribe();
    });
  }

  exportJob(job: JobDefinition): void {
    const ext = 'json';
    this.isLoading = true;
    this.cd.markForCheck();
    this.jobService.exportJob(job.jobId, ext).subscribe({
      next: (blob) => {
        downloadFile(blob, `${job.jobName}.${ext}`);
        this.isLoading = false;
        this.cd.detectChanges();
      },
      error: () => {
        this.isLoading = false;
        this.cd.detectChanges();
      },
    });
  }

  get hasPrevious() {
    return this.page > 0;
  }

  get hasNext() {
    return this.page * this.size + this.jobs.length < this.totalElements;
  }

  getTotalPages(): number {
    return Math.ceil(this.totalElements / this.size) || 1;
  }
}
