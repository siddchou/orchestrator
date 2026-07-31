export type StepType = 'ENV_SETUP' | 'LOG_CLEANUP' | 'JAVA_EXEC' | 'SFTP' | 'ARCHIVE' | 'HTTP_CALL' | 'SHELL_EXEC' | 'DB_QUERY';

// --- Step Type Schema (from GET /api/step-types) ---

export type FieldType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'ENUM' | 'SECRET_REF' | 'FILE_PATTERN' | 'LIST_STRING';

export interface FieldDefinition {
  name: string;
  label: string;
  type: FieldType;
  required: boolean;
  defaultValue?: string | number | boolean | null;
  enumValues?: string[];
  helpText?: string;
}

export interface StepConfigSchema {
  stepType: string;
  displayName: string;
  description?: string;
  fields: FieldDefinition[];
}
export type RunStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'PARTIAL' | 'CANCELLED';
export type TriggerType = 'MANUAL' | 'SCHEDULED' | 'API';

export interface JobDefinition {
  jobId: number;
  jobName: string;
  description: string | null;
  workingDir: string;
  javaHome: string | null;
  classpathEntries: string[];
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

/** Edge condition for DAG step dependencies */
export type EdgeCondition = 'ON_SUCCESS' | 'ON_FAILURE' | 'ALWAYS';

/** A dependency from one step to another in the DAG */
export interface StepDependency {
  stepDependencyId?: number;
  dependsOnStepId: number;
  dependsOnStepName: string;
  edgeCondition: EdgeCondition;
}

/** JobStep with its outgoing dependencies loaded */
export interface JobStepWithDependencies extends JobStep {
  dependencies: StepDependency[];
}

export interface EnvVar {
  envVarId: number;
  key: string;
  value: string;
  isGlobal: boolean;
}

export interface JobSchedule {
  scheduleId: number;
  cronExpression: string;
  enabled: boolean;
  nextFireTime: string | null;
}

// Step config shapes — parsed from JobStep.stepConfig JSON
export interface EnvSetupConfig {
  javaHome: string;
  classpathEntries: string[];
  extraEnvVars: Record<string, string>;
}

export interface LogCleanupConfig {
  directory: string;
  filePattern: string;
  extraPatterns?: string[];
}

export interface JavaExecConfig {
  mainClass?: string;
  jarPath?: string;
  args: string[];
  jvmArgs: string[];
  timeoutMinutes: number | null;
}

export interface SftpConfig {
  host: string;
  port: number;
  username: string;
  credentialRef: string;
  remoteDir: string;
  filePattern: string;
  direction: 'UPLOAD' | 'DOWNLOAD';
  remoteFileName?: string;
  connectionTimeoutSeconds?: number;
  authTimeoutSeconds?: number;
}

export interface ArchiveConfig {
  sourceDir: string;
  filePatterns: string[];
  archiveDir: string;
  archiveFormat: 'ZIP' | 'TAR_GZ';
  deleteOriginal?: boolean;
}
