import { RunStatus, TriggerType } from '@app/core/models/job.model';

export interface JobRunSummary {
  runId: number;
  jobId: number;
  jobName: string;
  status: RunStatus;
  triggerType: TriggerType;
  triggeredBy: string;
  startedAt: string;
  endedAt: string;
  durationSeconds: number;
}

export interface RunStepDetail {
  runStepId: number;
  stepName: string;
  stepType: string;
  stepOrder: number;
  status: RunStatus;
  exitCode: number | null;
  startedAt: string;
  endedAt: string;
  durationSeconds: number;
}

export interface JobRunDetail extends Omit<JobRunSummary, 'steps'> {
  steps: RunStepDetail[];
}

export interface DashboardSummary {
  totalJobs: number;
  runsToday: number;
  successRate: number;
  runningNow: number;
}
