import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatTabsModule } from '@angular/material/tabs';

import { CredentialService } from '@app/core/services/credential.service';
import { ConfirmDialog } from '@app/shared/components/confirm-dialog/confirm-dialog';
import { Credential, KeyGenerationRequest, KeyGenerationResponse } from './credential.model';
import { KeyDialogComponent } from './key-dialog.component';

@Component({
  selector: 'app-credentials',
  imports: [
    CommonModule, FormsModule, MatCardModule, MatTableModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatTooltipModule,
    MatSnackBarModule, MatDialogModule, MatSelectModule,
    MatProgressSpinnerModule, MatChipsModule, MatDividerModule, MatTabsModule,
  ],
  templateUrl: './credential-list.component.html',
  styleUrl: './credential-list.component.scss',
})
export class CredentialListComponent implements OnInit {
  private credentialService = inject(CredentialService);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);

  credentials: Credential[] = [];
  loading = true;
  selectedTab = 0; // 0 = list, 1 = generate key

  // Key generation form
  keyRef = '';
  keyAlgorithm: 'RSA' | 'ED25519' = 'RSA';
  rsaKeySize: 2048 | 4096 = 2048;
  generatingKey = false;

  displayedColumns = ['ref', 'type', 'createdAt', 'actions'];

  ngOnInit() {
    this.loadCredentials();
  }

  loadCredentials() {
    this.loading = true;
    this.credentialService.listCredentials().subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.credentials = res.data.sort((a, b) =>
            (b.createdAt || '').localeCompare(a.createdAt || '')
          );
        }
        this.loading = false;
      },
    });
  }

  deleteCredential(id: number) {
    const dialogRef = this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Delete Credential',
        message: 'Delete this credential?',
        confirmButton: 'Delete',
      },
    });

    dialogRef.afterClosed().subscribe((confirmed: boolean) => {
      if (!confirmed) return;

      // Update UI immediately to show item is being deleted
      this.credentials = this.credentials.filter(c => c.id !== id);

      this.credentialService.deleteCredential(id).subscribe({
        next: () => {
          this.snackBar.open('Credential deleted successfully', 'Undo', { duration: 5000 });
          // Refresh from server to get latest list
          setTimeout(() => this.loadCredentials(), 100);
        },
        error: (err) => {
          this.snackBar.open(err.message || 'Failed to delete credential', 'Close', { duration: 5000 });
          // Restore the credentials list on error
          this.loadCredentials();
        }
      });
    });
  }

  generateKeys() {
    if (!this.keyRef.trim()) {
      this.snackBar.open('Please enter a credential reference', 'Close', { duration: 3000 });
      return;
    }

    this.generatingKey = true;

    const request: KeyGenerationRequest = {
      ref: this.keyRef,
      algorithm: this.keyAlgorithm,
      rsaKeySize: this.keyAlgorithm === 'RSA' ? this.rsaKeySize : undefined,
    };

    this.credentialService.generateKeys(request).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.snackBar.open('SSH key pair generated successfully', 'Close', { duration: 3000 });
          this.showPrivateKey(res.data);
          setTimeout(() => this.loadCredentials(), 100);
          this.resetKeyForm();
        }
        this.generatingKey = false;
      },
      error: () => {
        this.snackBar.open('Failed to generate key pair', 'Close', { duration: 5000 });
        this.generatingKey = false;
      },
    });
  }

  private showPrivateKey(response: KeyGenerationResponse) {
    const dialogRef = this.dialog.open(KeyDialogComponent, {
      width: '1200px',
      maxWidth: '95vw',
      data: response,
    });

    dialogRef.afterClosed().subscribe(() => {
      // Clear clipboard after user closes dialog
      navigator.clipboard.writeText('').catch(() => {});
    });
  }

  private resetKeyForm() {
    this.keyRef = '';
    this.keyAlgorithm = 'RSA';
    this.rsaKeySize = 2048;
  }
}
