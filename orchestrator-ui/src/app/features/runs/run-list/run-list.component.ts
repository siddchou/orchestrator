import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { RunService } from '../../../core/services/run.service';
import { StatusBadge } from '../../../shared/components/status-badge/status-badge';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { JobRunSummary } from '../../../core/models/run.model';
import { RunStatus } from '../../../core/models/job.model';

@Component({
  selector: 'app-run-list',
  imports: [
    CommonModule, FormsModule, MatTableModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatSelectModule, MatTooltipModule, RouterLink,
    StatusBadge, DurationPipe,
  ],
  templateUrl: './run-list.component.html',
  styleUrl: './run-list.component.scss',
})
export class RunListComponent implements OnInit {
  private runService = inject(RunService);

  runs: JobRunSummary[] = [];
  displayedColumns = ['jobName', 'status', 'triggerType', 'startedAt', 'duration', 'actions'];
  totalElements = 0;
  page = 0;
  size = 20;

  filterStatus = '';
  filterJobId = '';

  ngOnInit() {
    this.loadRuns();
  }

  loadRuns() {
    this.runService.listRuns(this.page, this.size, undefined, this.filterStatus || undefined).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.runs = res.data.content;
          this.totalElements = res.data.totalElements;
        }
      },
    });
  }

  setPage(index: number) {
    this.page = index;
    this.loadRuns();
  }

  get hasPrevious() {
    return this.page > 0;
  }

  get hasNext() {
    return this.page * this.size + this.runs.length < this.totalElements;
  }

  get allStatuses(): RunStatus[] {
    return ['PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'PARTIAL', 'CANCELLED', 'SKIPPED'];
  }
}
