import { Component, OnInit, inject } from '@angular/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { JobService } from '@app/core/services/job.service';
import { StepConfigSchema } from '@app/core/models/job.model';

const STEP_TYPE_META: Record<string, { icon: string; description: string }> = {
  ARCHIVE: { icon: 'archive', description: 'Compress files into ZIP or TAR.GZ archives' },
  DB_QUERY: { icon: 'database', description: 'Run SQL queries against a database' },
  ENV_SETUP: { icon: 'settings_applications', description: 'Configure JAVA_HOME, classpath, and environment variables' },
  HTTP_CALL: { icon: 'cloud_upload', description: 'Make HTTP requests to external services' },
  JAVA_EXEC: { icon: 'language_java', description: 'Execute a Java main class or JAR file' },
  LOG_CLEANUP: { icon: 'cleanup', description: 'Delete old log files matching patterns' },
  SFTP: { icon: 'cloud_sync', description: 'Transfer files via SFTP/SCP' },
  SHELL_EXEC: { icon: 'terminal', description: 'Run shell commands or script files' },
};

@Component({
  selector: 'app-step-palette',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatButtonModule, MatIconModule, MatInputModule, MatFormFieldModule, MatProgressSpinnerModule],
  templateUrl: './step-palette.html',
  styleUrl: './step-palette.scss',
})
export class StepPaletteComponent implements OnInit {
  private jobService = inject(JobService);
  dialogRef = inject(MatDialogRef<StepPaletteComponent>);

  schemas: StepConfigSchema[] = [];
  loading = true;
  filter = '';

  ngOnInit(): void {
    this.jobService.listStepTypes().subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.schemas = res.data.sort((a, b) => a.displayName.localeCompare(b.displayName));
        }
        this.loading = false;
      },
      error: () => { this.loading = false; },
    });
  }

  get filtered(): StepConfigSchema[] {
    if (!this.filter.trim()) return this.schemas;
    const q = this.filter.toLowerCase();
    return this.schemas.filter(
      (s) =>
        s.displayName.toLowerCase().includes(q) ||
        s.stepType.toLowerCase().includes(q) ||
        this.metaFor(s.stepType).description.toLowerCase().includes(q),
    );
  }

  select(schema: StepConfigSchema): void {
    this.dialogRef.close({ stepType: schema.stepType });
  }

  metaFor(stepType: string): { icon: string; description: string } {
    return STEP_TYPE_META[stepType] ?? { icon: 'play_arrow', description: `Run a ${stepType} step` };
  }
}
