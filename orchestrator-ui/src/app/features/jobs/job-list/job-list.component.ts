import { Component, OnInit, inject } from '@angular/core';
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
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { JobService } from '../../../core/services/job.service';
import { StatusBadge } from '../../../shared/components/status-badge/status-badge';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { JobDefinition } from '../../../core/models/job.model';

@Component({
  selector: 'app-job-list',
  imports: [
    CommonModule, MatCardModule, MatTableModule, MatPaginatorModule, MatSortModule,
    MatInputModule, MatFormFieldModule, MatButtonModule, MatIconModule,
    MatChipsModule, MatSnackBarModule, MatDialogModule, RouterLink,
  ],
  templateUrl: './job-list.component.html',
  styleUrl: './job-list.component.scss',
})
export class JobListComponent implements OnInit {
  private jobService = inject(JobService);
  private dialog = inject(MatDialog);

  jobs: JobDefinition[] = [];
  displayedColumns = ['jobName', 'description', 'enabled', 'steps', 'schedule', 'actions'];
  totalElements = 0;
  page = 0;
  size = 20;

  private searchSubject = new Subject<string>();

  ngOnInit() {
    this.loadJobs();

    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(() => {
      this.page = 0;
      this.loadJobs();
    });
  }

  loadJobs() {
    const search = (this.searchInput as HTMLInputElement)?.value || '';
    this.jobService.listJobs(this.page, this.size, search).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.jobs = res.data.content;
          this.totalElements = res.data.totalElements;
        }
      },
    });
  }

  search() {
    this.searchSubject.next('');
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
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Trigger Run',
        message: `Run "${job.jobName}" now?`,
        confirmButton: 'Run',
      },
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.jobService.triggerRun(job.jobId).subscribe();
    });
  }

  get hasPrevious() {
    return this.page > 0;
  }

  get hasNext() {
    return this.page * this.size + this.jobs.length < this.totalElements;
  }

  // Template helper
  get searchInput(): HTMLInputElement | undefined {
    return document.querySelector('#searchInput') as HTMLInputElement;
  }
}
