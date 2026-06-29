import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormGroup, FormControl } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { RunService } from '../../../core/services/run.service';
import { StatusBadge } from '../../../shared/components/status-badge/status-badge';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { JobRunSummary } from '../../../core/models/run.model';
import { RunStatus } from '../../../core/models/job.model';

@Component({
  selector: 'app-run-list',
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule,
    MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatSelectModule, MatTooltipModule,
    MatDatepickerModule, MatProgressSpinnerModule,
    StatusBadge, DurationPipe,
  ],
  templateUrl: './run-list.component.html',
  styleUrl: './run-list.component.scss',
})
export class RunListComponent implements OnInit {
  private runService = inject(RunService);
  private router = inject(Router);

  runs: JobRunSummary[] = [];
  displayedColumns = ['jobName', 'status', 'triggerType', 'triggeredBy', 'startedAt', 'duration', 'actions'];
  totalElements = 0;
  page = 0;
  size = 20;
  loading = false;

  filterForm = new FormGroup({
    jobId: new FormControl<number | null>(null),
    status: new FormControl<RunStatus | null>(null),
    from: new FormControl<Date | null>(null),
    to: new FormControl<Date | null>(null),
  });

  ngOnInit() {
    this.loadRuns(0);
  }

  loadRuns(page: number) {
    this.page = page;
    this.loading = true;
    const f = this.filterForm.value;

    this.runService.listRuns(
      page,
      this.size,
      f.jobId ?? undefined,
      f.status ?? undefined,
      f.from ? this.formatDate(f.from) : undefined,
      f.to ? this.formatDate(f.to) : undefined
    ).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.runs = res.data.content;
          this.totalElements = res.data.totalElements;
        }
        this.loading = false;
      },
      error: () => { this.loading = false; },
    });
  }

  applyFilters(): void {
    this.loadRuns(0);
  }

  clearFilters(): void {
    this.filterForm.reset({ jobId: null, status: null, from: null, to: null });
    this.loadRuns(0);
  }

  onPage(event: PageEvent): void {
    this.loadRuns(event.pageIndex);
  }

  viewRun(runId: number): void {
    this.router.navigate(['/runs', runId]);
  }

  get allStatuses(): RunStatus[] {
    return ['PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'PARTIAL', 'CANCELLED'];
  }

  private formatDate(d: Date): string {
    return d.toISOString().split('T')[0];
  }
}
