import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
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

/** Maps common keywords in step type names to Material icons */
function iconFor(stepType: string): string {
  const upper = stepType.toUpperCase();
  if (upper.includes('ENV') || upper.includes('SETUP')) return 'settings_applications';
  if (upper.includes('LOG') || upper.includes('CLEAN')) return 'delete_sweep';
  if (upper.includes('JAVA')) return 'language_java';
  if (upper.includes('SFTP') || upper.includes('TRANSFER')) return 'cloud_upload';
  if (upper.includes('ARCHIVE') || upper.includes('ZIP')) return 'folder_zip';
  if (upper.includes('HTTP') || upper.includes('CALL') || upper.includes('API')) return 'language';
  if (upper.includes('SHELL') || upper.includes('EXEC') || upper.includes('CMD')) return 'terminal';
  if (upper.includes('DB') || upper.includes('QUERY') || upper.includes('SQL')) return 'storage';
  return 'play_arrow';
}

function descriptionFor(schema: StepConfigSchema): string {
  return schema.description ?? `${schema.displayName} step`;
}

@Component({
  selector: 'app-step-palette',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatButtonModule, MatIconModule, MatInputModule, MatFormFieldModule, MatProgressSpinnerModule],
  templateUrl: './step-palette.html',
  styleUrl: './step-palette.scss',
})
export class StepPaletteComponent implements OnInit {
  private jobService = inject(JobService);
  private cd = inject(ChangeDetectorRef);
  dialogRef = inject(MatDialogRef<StepPaletteComponent>);

  schemas: StepConfigSchema[] = [];
  loading = true;
  error = false;
  filter = '';

  loadSchemas(): void {
    this.loading = true;
    this.error = false;
    this.jobService.listStepTypes().subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.schemas = res.data.sort((a, b) => a.displayName.localeCompare(b.displayName));
        } else {
          this.error = true;
        }
        this.loading = false;
        this.cd.detectChanges();
      },
      error: () => { this.error = true; this.loading = false; this.cd.detectChanges(); },
    });
  }

  ngOnInit(): void {
    this.loadSchemas();
  }

  get filtered(): StepConfigSchema[] {
    if (!this.filter.trim()) return this.schemas;
    const q = this.filter.toLowerCase();
    return this.schemas.filter(
      (s) =>
        s.displayName.toLowerCase().includes(q) ||
        s.stepType.toLowerCase().includes(q) ||
        descriptionFor(s).toLowerCase().includes(q),
    );
  }

  select(schema: StepConfigSchema): void {
    this.dialogRef.close({ stepType: schema.stepType });
  }

  iconFor = iconFor;
  descriptionFor = descriptionFor;
}
