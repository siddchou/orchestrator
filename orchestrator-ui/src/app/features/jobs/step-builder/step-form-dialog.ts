import { Component, inject, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { StepType, EnvSetupConfig, LogCleanupConfig, JavaExecConfig, SftpConfig, ArchiveConfig } from '../../../core/models/job.model';

export interface StepFormData {
  stepId?: number;
  stepName: string;
  stepOrder: number;
  stepType: StepType;
  stepConfig: string;
  continueOnFailure: boolean;
  enabled: boolean;
}

@Component({
  selector: 'app-step-form-dialog',
  imports: [
    CommonModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatSelectModule, MatSlideToggleModule, MatDialogModule, MatTooltipModule,
  ],
  templateUrl: './step-form-dialog.html',
  styleUrl: './step-form-dialog.scss',
})
export class StepFormDialog implements OnDestroy {
  private fb = inject(FormBuilder);
  private dialogRef = inject(MatDialogRef<StepFormDialog>);
  data = inject<StepFormData>(MAT_DIALOG_DATA);

  stepTypes: StepType[] = ['LOG_CLEANUP', 'JAVA_EXEC', 'SFTP', 'ARCHIVE'];

  form: FormGroup;
  private typeChangeSub?: Subscription;

  constructor() {
    this.form = this.fb.group({
      stepName: [this.data.stepName, Validators.required],
      stepType: [this.data.stepType, Validators.required],
      continueOnFailure: [this.data.continueOnFailure],
      enabled: [this.data.enabled],
      // ENV_SETUP
      javaHome: [''],
      classpath: [''],
      // LOG_CLEANUP
      cleanupDir: [''],
      filePattern: [''],
      extraPatterns: [''],
      // JAVA_EXEC
      mainClass: [''],
      jarPath: [''],
      jvmArgs: [''],
      args: [''],
      timeoutMinutes: [null],
      // SFTP
      host: [''],
      port: [22],
      username: [''],
      credentialRef: [''],
      remoteDir: [''],
      sftp_filePattern: [''],
      direction: ['UPLOAD'],
      remoteFileName: [''],
      // ARCHIVE
      sourceDir: [''],
      archiveDir: [''],
      archivePatterns: [''],
      archiveFormat: ['ZIP'],
      deleteOriginal: [false],
    });

    // Pre-fill from stepConfig JSON if editing
    if (this.data.stepConfig) {
      const configValues = this.configToFormValues(this.data.stepType, this.data.stepConfig);
      this.form.patchValue(configValues);
    }

    // Clear old-type fields when step type changes
    this.typeChangeSub = this.form.get('stepType')?.valueChanges.subscribe(type => {
      this.form.patchValue({
        javaHome: '', classpath: '',
        cleanupDir: '', filePattern: '', extraPatterns: '',
        mainClass: '', jarPath: '', jvmArgs: '', args: '', timeoutMinutes: null,
        host: '', port: 22, username: '', credentialRef: '', remoteDir: '', sftp_filePattern: '', direction: 'UPLOAD', remoteFileName: '',
        sourceDir: '', archiveDir: '', archivePatterns: '', archiveFormat: 'ZIP', deleteOriginal: false,
      });
    });
  }

  ngOnDestroy(): void {
    this.typeChangeSub?.unsubscribe();
  }

  get selectedType(): StepType {
    return this.form.value.stepType as StepType;
  }

  onSubmit() {
    if (this.form.invalid) return;
    const v = this.form.value;

    const stepConfig = JSON.stringify(this.formValuesToConfig(v));

    this.dialogRef.close({
      stepId: this.data.stepId,
      stepName: v.stepName,
      stepOrder: this.data.stepOrder,
      stepType: v.stepType,
      stepConfig,
      continueOnFailure: v.continueOnFailure,
      enabled: v.enabled,
    });
  }

  onCancel() {
    this.dialogRef.close();
  }

  private configToFormValues(type: StepType, configJson: string): Record<string, unknown> {
    try {
      const c = JSON.parse(configJson);
      switch (type) {
        case 'ENV_SETUP':
          return { javaHome: (c as EnvSetupConfig).javaHome, classpath: ((c as EnvSetupConfig).classpathEntries ?? []).join(',') };
        case 'LOG_CLEANUP':
          return { cleanupDir: (c as LogCleanupConfig).directory, filePattern: (c as LogCleanupConfig).filePattern, extraPatterns: ((c as LogCleanupConfig).extraPatterns ?? []).join(',') };
        case 'JAVA_EXEC':
          return {
            mainClass: (c as JavaExecConfig).mainClass ?? '',
            jarPath: (c as JavaExecConfig).jarPath ?? '',
            jvmArgs: ((c as JavaExecConfig).jvmArgs ?? []).join(' '),
            args: ((c as JavaExecConfig).args ?? []).join(' '),
            timeoutMinutes: (c as JavaExecConfig).timeoutMinutes ?? null,
          };
        case 'SFTP':
          return {
            host: (c as SftpConfig).host,
            port: (c as SftpConfig).port,
            username: (c as SftpConfig).username,
            credentialRef: (c as SftpConfig).credentialRef,
            remoteDir: (c as SftpConfig).remoteDir,
            sftp_filePattern: (c as SftpConfig).filePattern,
            direction: (c as SftpConfig).direction,
            remoteFileName: (c as SftpConfig).remoteFileName,
          };
        case 'ARCHIVE':
          return {
            sourceDir: (c as ArchiveConfig).sourceDir,
            archiveDir: (c as ArchiveConfig).archiveDir,
            archivePatterns: ((c as ArchiveConfig).filePatterns ?? []).join(','),
            archiveFormat: (c as ArchiveConfig).archiveFormat,
            deleteOriginal: (c as ArchiveConfig).deleteOriginal ?? false,
          };
      }
    } catch {
      return {};
    }
    return {};
  }

  private formValuesToConfig(v: typeof this.form.value): Record<string, unknown> {
    switch (v.stepType) {
      case 'ENV_SETUP':
        return {
          javaHome: v.javaHome,
          classpathEntries: (v.classpath ?? '').split(',').map((s: string) => s.trim()).filter(Boolean),
          extraEnvVars: {},
        };
      case 'LOG_CLEANUP':
        return {
          directory: v.cleanupDir,
          filePattern: v.filePattern,
          extraPatterns: (v.extraPatterns ?? '').split(',').map((s: string) => s.trim()).filter(Boolean),
        };
      case 'JAVA_EXEC':
        return {
          mainClass: v.mainClass || null,
          jarPath: v.jarPath || null,
          jvmArgs: (v.jvmArgs ?? '').split(' ').filter(Boolean),
          args: (v.args ?? '').split(' ').filter(Boolean),
          timeoutMinutes: v.timeoutMinutes || null,
        };
      case 'SFTP':
        return {
          host: v.host,
          port: v.port,
          username: v.username,
          credentialRef: v.credentialRef,
          remoteDir: v.remoteDir,
          filePattern: v.sftp_filePattern,
          direction: v.direction,
          remoteFileName: v.remoteFileName || null,
        };
      case 'ARCHIVE':
        return {
          sourceDir: v.sourceDir,
          archiveDir: v.archiveDir,
          filePatterns: (v.archivePatterns ?? '').split(',').map((s: string) => s.trim()).filter(Boolean),
          archiveFormat: v.archiveFormat,
          deleteOriginal: v.deleteOriginal ?? false,
        };
      default:
        return {};
    }
  }
}
