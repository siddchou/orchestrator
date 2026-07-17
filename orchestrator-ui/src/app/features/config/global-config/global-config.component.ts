import { Component, OnInit, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SystemService } from '@app/core/services/system.service';
import { ConfirmDialog } from '@app/shared/components/confirm-dialog/confirm-dialog';
import { EnvVar } from '@app/core/models/job.model';
import { HealthStatus } from '@app/core/models/system.model';

@Component({
  selector: 'app-global-config',
  imports: [
    CommonModule, FormsModule, MatCardModule, MatTableModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatTooltipModule,
    MatSnackBarModule, MatDialogModule, MatChipsModule, MatProgressSpinnerModule,
  ],
  templateUrl: './global-config.component.html',
  styleUrl: './global-config.component.scss',
})
export class GlobalConfigComponent implements OnInit {
  private systemService = inject(SystemService);
  private dialog = inject(MatDialog);
  private cd = inject(ChangeDetectorRef);

  globalEnvVars: EnvVar[] = [];
  health: HealthStatus | null = null;

  newKey = '';
  newValue = '';

  // Path validator
  javaHome = '';
  workingDir = '';
  validationResult: string | null = null;

  isLoading = false;

  ngOnInit() {
    this.isLoading = true;
    this.loadGlobalEnvVars();
    this.loadHealth();
  }

  loadGlobalEnvVars() {
    this.systemService.getGlobalEnvVars().subscribe({
      next: () => this.cd.markForCheck(),
    });
  }

  loadHealth() {
    this.systemService.getHealth().subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.health = res.data;
        }
        this.isLoading = false;
        this.cd.detectChanges();
      },
    });
  }

  addEnvVar() {
    if (!this.newKey.trim() || !this.newValue.trim()) return;
    this.systemService.addGlobalEnvVar({ key: this.newKey, value: this.newValue }).subscribe({
      next: () => {
        this.newKey = '';
        this.newValue = '';
        this.loadGlobalEnvVars();
      },
    });
  }

  deleteEnvVar(envId: number) {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Variable',
        message: 'Delete this global environment variable?',
        confirmButton: 'Delete',
      },
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.systemService.deleteGlobalEnvVar(envId).subscribe({
        next: () => this.loadGlobalEnvVars(),
      });
    });
  }

  validatePaths() {
    this.systemService.validateEnv(this.javaHome, this.workingDir).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.validationResult = res.data.message;
        }
      },
    });
  }
}
