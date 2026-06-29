export interface HealthStatus {
  database: string;
  threadPoolUtilization: number;
  activeRuns: number;
}

export interface EnvValidationResult {
  valid: boolean;
  message: string;
}

export interface CronValidationResult {
  valid: boolean;
  nextFireTimes: string[];
}
