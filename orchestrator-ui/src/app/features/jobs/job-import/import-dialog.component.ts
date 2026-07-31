import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormsModule } from '@angular/forms';
import { JobService } from '@app/core/services/job.service';
import { JobImportRequest } from '@app/core/models/job.model';

export interface ImportDialogData {
  /** Pre-filled content (optional) */
  initialContent?: string;
}

@Component({
  selector: 'app-import-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatProgressSpinnerModule, FormsModule],
  templateUrl: './import-dialog.component.html',
  styleUrl: './import-dialog.component.scss',
})
export class ImportDialogComponent {
  private dialogRef = inject(MatDialogRef<ImportDialogComponent>);
  private jobService = inject(JobService);

  content = '';
  format: 'json' | 'yaml' = 'json';
  isDragging = false;
  isLoading = false;
  error = '';
  fileName = '';

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.readFiles(input.files);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = true;
  }

  onDragLeave(): void {
    this.isDragging = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = false;
    if (event.dataTransfer?.files.length) {
      this.readFiles(event.dataTransfer.files);
    }
  }

  private readFiles(files: FileList): void {
    const file = files[0];
    if (!file) return;

    const ext = file.name.split('.').pop()?.toLowerCase();
    if (ext === 'yaml' || ext === 'yml') {
      this.format = 'yaml';
    } else {
      this.format = 'json';
    }

    this.fileName = file.name;
    const reader = new FileReader();
    reader.onload = (e) => {
      this.content = e.target?.result as string;
      this.error = '';
    };
    reader.onerror = () => {
      this.error = 'Failed to read file';
    };
    reader.readAsText(file);
  }

  import(): void {
    if (!this.content.trim()) {
      this.error = 'No content to import. Drop a file or paste JSON/YAML below.';
      return;
    }

    this.isLoading = true;
    this.error = '';

    const body: JobImportRequest = {
      format: this.format,
      content: this.content.trim(),
    };

    this.jobService.importJob(body).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.dialogRef.close(res.data);
        } else {
          this.error = res.message || 'Import failed';
          this.isLoading = false;
        }
      },
      error: () => {
        this.error = 'Import failed — check the console for details.';
        this.isLoading = false;
      },
    });
  }

  cancel(): void {
    this.dialogRef.close(null);
  }
}
