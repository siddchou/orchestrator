import { ChangeDetectorRef, Component, Input, OnInit, Output, EventEmitter, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { JobService } from '@app/core/services/job.service';
import { JsonDiffService } from '@app/core/services/json-diff.service';
import { JobVersionSummary } from '@app/core/models/job.model';
import { forkJoin } from 'rxjs';
import { RollbackConfirmDialogComponent, RollbackDialogData } from './rollback-confirm-dialog.component';

@Component({
  selector: 'app-version-history',
  standalone: true,
  imports: [
    CommonModule, MatButtonModule, MatIconModule, MatSelectModule,
    MatFormFieldModule, MatProgressSpinnerModule, MatDialogModule,
  ],
  templateUrl: './version-history.component.html',
  styleUrl: './version-history.component.scss',
})
export class VersionHistoryComponent implements OnInit {
  @Input() jobId!: number;
  @Output() versionLoaded = new EventEmitter<void>();

  private jobService = inject(JobService);
  private jsonDiffService = inject(JsonDiffService);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  private cd = inject(ChangeDetectorRef);

  versions: JobVersionSummary[] = [];
  isLoading = true;

  compareA: number = 0;
  compareB: number = 0;
  diffHtml = '';
  isComparing = false;
  showDiff = false;
  noDiff = false;

  ngOnInit(): void {
    this.loadVersions();
  }

  loadVersions(): void {
    this.isLoading = true;
    this.cd.markForCheck();
    this.jobService.listVersions(this.jobId).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS' && Array.isArray(res.data)) {
          this.versions = res.data.sort((a, b) => b.versionNumber - a.versionNumber);
        } else {
          console.error('[VersionHistory] Unexpected response:', res);
          this.versions = [];
        }
        this.isLoading = false;
        this.cd.markForCheck();
      },
      error: (err) => {
        console.error('[VersionHistory] Error loading versions:', err);
        this.isLoading = false;
        this.cd.markForCheck();
      },
    });
  }

  get currentVersionId(): number {
    return this.versions.length > 0 ? this.versions[0].versionId : 0;
  }

  compareVersions(): void {
    if (this.compareA === 0 || this.compareB === 0 || this.compareA === this.compareB) return;

    this.isComparing = true;
    this.showDiff = false;
    this.noDiff = false;

    forkJoin({
      left: this.jobService.getVersionSnapshot(this.jobId, this.compareA),
      right: this.jobService.getVersionSnapshot(this.jobId, this.compareB),
    }).subscribe({
      next: ({ left, right }) => {
        const diffResult = this.jsonDiffService.compare(left, right);
        if (diffResult === undefined) {
          this.noDiff = true;
          this.diffHtml = '';
        } else {
          this.noDiff = false;
          this.diffHtml = diffResult;
        }
        this.showDiff = true;
        this.isComparing = false;
        this.cd.markForCheck();
      },
      error: () => {
        this.snackBar.open('Failed to compare versions', 'Dismiss', { duration: 3000 });
        this.isComparing = false;
        this.cd.markForCheck();
      },
    });
  }

  closeDiff(): void {
    this.showDiff = false;
    this.diffHtml = '';
    this.noDiff = false;
  }

  rollback(version: JobVersionSummary): void {
    const data: RollbackDialogData = {
      versionNumber: version.versionNumber,
      createdBy: version.createdBy,
      createdAt: version.createdAt,
      hasActiveRun: false,
    };

    this.dialog.open(RollbackConfirmDialogComponent, { data }).afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;

      this.jobService.rollbackToVersion(this.jobId, version.versionId).subscribe({
        next: (res) => {
          if (res.status === 'SUCCESS') {
            this.snackBar.open(`Rolled back to v${version.versionNumber}`, 'Dismiss', { duration: 3000 });
            this.loadVersions();
            this.versionLoaded.emit();
            this.cd.markForCheck();
          }
        },
        error: () => {
          this.snackBar.open('Rollback failed', 'Dismiss', { duration: 3000, panelClass: 'error-snackbar' });
          this.cd.markForCheck();
        },
      });
    });
  }

  getLabel(versionId: number): string {
    const v = this.versions.find((x) => x.versionId === versionId);
    return v ? String(v.versionNumber) : '?';
  }

  formatDate(isoString: string): string {
    try {
      return new Date(isoString).toLocaleString();
    } catch {
      return isoString;
    }
  }
}
