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

      <mat-divider style="margin: 16px 0"></mat-divider>

      <div class="key-container">
        <div class="key-section private-key-section">
          <h4>Private Key</h4>
          <textarea class="key-textarea" readonly>{{data.privateKey}}</textarea>
          <div class="button-group">
            <button mat-button color="primary" (click)="copyPrivateKey()">
              Copy to Clipboard
            </button>
            <button mat-button color="accent" (click)="downloadPrivateKey()">
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

      <mat-divider style="margin: 16px 0"></mat-divider>

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
      min-width: 1200px !important;
      max-height: 95vh;
    }
    .info-row {
      display: flex;
      gap: 12px;
      margin-bottom: 4px;
      font-size: 14px;
    }
    .info-label {
      font-weight: 600;
      color: #666;
      min-width: 90px;
    }
    .info-value {
      flex: 1;
      font-family: monospace;
      word-break: break-all;
    }
    h4 {
      margin-top: 12px;
      margin-bottom: 8px;
      font-size: 14px;
      color: #333;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .key-container {
      display: flex;
      gap: 30px;
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
      padding: 12px;
      font-family: 'Courier New', monospace;
      font-size: 12px;
      border: 1px solid #ccc;
      border-radius: 8px;
      background: #f9f9f9;
      resize: vertical;
      overflow-x: auto;
    }
    .key-textarea:focus {
      outline: none;
      border-color: #3f51b5;
      box-shadow: 0 0 0 2px rgba(63, 81, 181, 0.2);
    }
    .note {
      color: #777;
      font-size: 12px;
      margin-top: 8px;
      line-height: 1.4;
    }
    .security-warning {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      background: #fff3e0;
      padding: 14px;
      border-radius: 6px;
      border-left: 4px solid #ff9800;
    }
    .security-warning mat-icon {
      margin-top: 2px;
      color: #f57c00;
    }
    .security-warning p {
      color: #e65100;
      font-size: 13px;
      margin: 0;
      flex: 1;
      line-height: 1.5;
    }
    .button-group {
      display: flex;
      gap: 8px;
      margin-top: 12px;
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
