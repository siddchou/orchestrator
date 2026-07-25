import { Component, inject } from '@angular/core';
import { MatDialogRef, MatDialogModule, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar } from '@angular/material/snack-bar';

export interface KeyDialogData {
  privateKey: string;
  publicKey: string;
  fingerprint: string;
  algorithm: string;
}

@Component({
  selector: 'app-key-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatDividerModule],
  standalone: true,
  template: `
    <div class="dialog">
      <div class="dialog-header">
        <div class="dialog-icon">
          <mat-icon>key</mat-icon>
        </div>
        <h3 class="dialog-title">SSH Key Generated</h3>
      </div>

      <mat-dialog-content>
        <div class="info-row">
          <span class="info-label">Fingerprint</span>
          <span class="info-value">{{data.fingerprint}}</span>
        </div>
        <div class="info-row">
          <span class="info-label">Algorithm</span>
          <span class="info-value">{{data.algorithm}}</span>
        </div>

        <mat-divider style="margin: var(--spacing-md) 0;"></mat-divider>

        <div class="key-section">
          <h4>Private Key</h4>
          <textarea class="key-textarea" readonly>{{data.privateKey}}</textarea>
          <div class="button-group">
            <button mat-stroked-button (click)="copyPrivateKey()">
              <mat-icon>content_copy</mat-icon> Copy
            </button>
            <button mat-flat-button color="primary" (click)="downloadPrivateKey()">
              <mat-icon>download</mat-icon> Download
            </button>
          </div>
        </div>

        <div class="key-section">
          <h4>Public Key</h4>
          <textarea class="key-textarea" readonly>{{data.publicKey}}</textarea>
          <p class="note">Store this public key as a credential in the system.</p>
        </div>

        <mat-divider style="margin: var(--spacing-md) 0;"></mat-divider>

        <div class="validation-result error">
          <mat-icon class="validation-icon">warning</mat-icon>
          <span>Your private key will only be shown once. Save it securely — generating a new pair will invalidate this key.</span>
        </div>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-stroked-button mat-dialog-close>Close</button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .dialog {
      padding: var(--spacing-md);
    }

    .dialog-header {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: var(--spacing-lg) 0 var(--spacing-sm);
    }

    .dialog-icon {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      background: rgba(37, 99, 235, 0.1);
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: var(--spacing-sm);

      mat-icon {
        color: var(--accent-primary);
        font-size: 24px;
        width: 24px;
        height: 24px;
      }
    }

    .dialog-title {
      font-size: var(--font-size-lg);
      font-weight: var(--font-weight-semibold);
      margin: 0;
      text-align: center;
    }

    .info-row {
      display: flex;
      gap: var(--spacing-sm);
      margin-bottom: 4px;
      font-size: var(--font-size-sm);
    }

    .info-label {
      font-weight: var(--font-weight-semibold);
      color: var(--mat-sys-on-surface-variant);
      min-width: 90px;
    }

    .info-value {
      flex: 1;
      font-family: var(--font-mono);
      word-break: break-all;
    }

    h4 {
      margin-top: var(--spacing-md);
      margin-bottom: var(--spacing-sm);
      font-size: var(--font-size-xs);
      color: var(--mat-sys-on-surface-variant);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .key-section {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    .key-textarea {
      width: 100%;
      height: 200px;
      padding: var(--spacing-sm);
      font-family: var(--font-mono);
      font-size: var(--font-size-xs);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-sm);
      background: rgba(15, 15, 15, 0.03);
      color: var(--mat-sys-on-surface);
      resize: vertical;
      overflow-x: auto;
    }

    .key-textarea:focus-visible {
      outline: none;
      border-color: var(--accent-primary);
      box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
    }

    .note {
      color: var(--mat-sys-on-surface-variant);
      font-size: var(--font-size-xs);
      margin-top: var(--spacing-sm);
      line-height: 1.4;
    }

    .button-group {
      display: flex;
      gap: var(--spacing-xs);
      margin-top: var(--spacing-sm);
    }
  `],
})
export class KeyDialogComponent {
  dialogRef = inject(MatDialogRef<KeyDialogComponent>);
  data = inject(MAT_DIALOG_DATA) as KeyDialogData;
  private snackBar = inject(MatSnackBar);

  copyPrivateKey() {
    navigator.clipboard.writeText(this.data.privateKey).then(() => {
      this.snackBar.open('Private key copied to clipboard', 'Close', { duration: 2000 });
    });
  }

  downloadPrivateKey() {
    const blob = new Blob([this.data.privateKey], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ssh-private-key-${new Date().toISOString().split('T')[0]}.pem`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }
}
