import { Component, Inject } from '@angular/core';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';

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
    <h2 mat-dialog-title>SSH Key Generated</h2>
    <mat-dialog-content class="key-dialog-content">
      <div class="info-row">
        <span class="info-label">Fingerprint:</span>
        <span class="info-value">{{data.fingerprint}}</span>
      </div>
      <div class="info-row">
        <span class="info-label">Algorithm:</span>
        <span class="info-value">{{data.algorithm}}</span>
      </div>

      <mat-divider style="margin: var(--spacing-md) 0;"></mat-divider>

      <div class="key-container">
        <div class="key-section private-key-section">
          <h4>Private Key</h4>
          <textarea class="key-textarea" readonly>{{data.privateKey}}</textarea>
          <div class="button-group">
            <button mat-stroked-button (click)="copyPrivateKey()">
              Copy to Clipboard
            </button>
            <button mat-flat-button color="primary" (click)="downloadPrivateKey()">
              Download Private Key
            </button>
          </div>
        </div>

        <div class="key-section public-key-section">
          <h4>Public Key</h4>
          <textarea class="key-textarea" readonly>{{data.publicKey}}</textarea>
          <p class="note">Store this public key as a credential in the system.</p>
        </div>
      </div>

      <mat-divider style="margin: var(--spacing-md) 0;"></mat-divider>

      <div class="security-warning">
        <mat-icon color="warn">warning</mat-icon>
        <p><strong>Important:</strong> Your private key will only be shown once. Make sure to save it securely. If you lose it, you'll need to generate a new key pair.</p>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .key-dialog-content {
      min-width: 600px !important;
      max-height: 95vh;
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
    .key-container {
      display: grid;
      gap: var(--spacing-md);
    }
    .key-section {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-width: 0;
    }
    .private-key-section,
    .public-key-section {
      flex: 1;
    }
    .key-textarea {
      width: 100%;
      height: 320px;
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
    .security-warning {
      display: flex;
      align-items: flex-start;
      gap: var(--spacing-sm);
      background: rgba(234, 88, 12, 0.06);
      padding: var(--spacing-md);
      border-radius: var(--radius-sm);
      border-left: 3px solid var(--accent-warning);
    }
    .security-warning mat-icon {
      margin-top: 2px;
      color: var(--accent-warning);
    }
    .security-warning p {
      color: #7c2d12;
      font-size: var(--font-size-sm);
      margin: 0;
      flex: 1;
      line-height: 1.5;
    }
    .button-group {
      display: flex;
      gap: var(--spacing-xs);
      margin-top: var(--spacing-sm);
    }
  `],
})
export class KeyDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<KeyDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: KeyDialogData
  ) {}

  copyPrivateKey() {
    navigator.clipboard.writeText(this.data.privateKey).then(() => {
      // Notification would go here if needed
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
