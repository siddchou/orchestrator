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
  template: `
    <h2 mat-dialog-title>SSH Key Generated</h2>
    <mat-dialog-content class="key-dialog-content">
      <p><strong>Fingerprint:</strong> {{data.fingerprint}}</p>
      <p><strong>Algorithm:</strong> {{data.algorithm}}</p>

      <div class="key-container">
        <div class="key-section private-key-section">
          <h4 mat-subheader>Private Key</h4>
          <textarea class="key-textarea" readonly>{{data.privateKey}}</textarea>
          <div class="button-group">
            <button mat-button color="primary" (click)="copyPrivateKey()">
              <mat-icon>content_copy</mat-icon> Copy to Clipboard
            </button>
            <button mat-button color="accent" (click)="downloadPrivateKey()">
              <mat-icon>download</mat-icon> Download Private Key
            </button>
          </div>
        </div>

        <div class="key-section public-key-section">
          <h4 mat-subheader>Public Key</h4>
          <textarea class="key-textarea" readonly>{{data.publicKey}}</textarea>
          <p class="note">Store this public key as a credential in the system.</p>
        </div>
      </div>

      <mat-divider style="margin: 16px 0"></mat-divider>

      <div class="security-warning">
        <mat-icon color="warn">warning</mat-icon>
        <p><strong>Important:</strong> Your private key will only be shown once. Make sure to save it securely. If you lose it, you'll need to generate a new key pair.</p>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Done</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .key-dialog-content {
      min-width: 800px;
      max-height: 90vh;
    }
    h4 {
      margin-top: 12px;
      font-size: 14px;
      color: #666;
    }
    .key-container {
      display: flex;
      gap: 24px;
    }
    .key-section {
      flex: 1;
      display: flex;
      flex-direction: column;
    }
    .private-key-section {
      min-width: 50%;
    }
    .public-key-section {
      min-width: 30%;
    }
    .key-textarea {
      width: 100%;
      height: 280px;
      padding: 12px;
      font-family: 'Courier New', monospace;
      font-size: 12px;
      border: 1px solid #ddd;
      border-radius: 4px;
      background: #fafafa;
      resize: vertical;
    }
    .note {
      color: #666;
      font-size: 12px;
      margin-top: 8px;
    }
    .security-warning {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      background: #fff3e0;
      padding: 12px;
      border-radius: 4px;
    }
    .security-warning mat-icon {
      margin-top: 4px;
    }
    .security-warning p {
      color: #d84315;
      font-size: 13px;
      margin: 0;
      flex: 1;
    }
    .button-group {
      display: flex;
      gap: 8px;
      margin-top: 8px;
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
