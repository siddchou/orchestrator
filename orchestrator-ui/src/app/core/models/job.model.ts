export type StepType = 'ENV_SETUP' | 'LOG_CLEANUP' | 'JAVA_EXEC' | 'SFTP' | 'ARCHIVE';
export type RunStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'PARTIAL' | 'CANCELLED' | 'SKIPPED';
export type TriggerType = 'MANUAL' | 'SCHEDULED' | 'API';

export interface JobDefinition {
  jobId: number;
  jobName: string;
  description: string;
  workingDir: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  steps: JobStep[];
  envVars: EnvVar[];
  schedule: JobSchedule | null;
}

export interface JobStep {
  stepId: number;
  stepName: string;
  stepOrder: number;
  stepType: StepType;
  stepConfig: string;
  continueOnFailure: boolean;
  enabled: boolean;
}

export interface JobSchedule {
  scheduleId: number;
  cronExpression: string;
  enabled: boolean;
  nextFireTime: string | null;
}

export interface EnvVar {
  envVarId: number;
  key: string;
  value: string;
  isGlobal: boolean;
}
