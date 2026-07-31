import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface RollbackDialogData {
  versionNumber: number;
  createdBy: string;
  createdAt: string;
  hasActiveRun?: boolean;
}

@Component({
  selector: 'app-rollback-confirm-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './rollback-confirm-dialog.component.html',
  styleUrl: './rollback-confirm-dialog.component.scss',
})
export class RollbackConfirmDialogComponent {
  private dialogRef = inject(MatDialogRef<RollbackConfirmDialogComponent>);
  data = inject<RollbackDialogData>('MAT_DIALOG_DATA');

  confirm(): void {
    this.dialogRef.close(true);
  }

  cancel(): void {
    this.dialogRef.close(false);
  }

  formatDate(isoString: string): string {
    try {
      return new Date(isoString).toLocaleString();
    } catch {
      return isoString;
    }
  }
}
