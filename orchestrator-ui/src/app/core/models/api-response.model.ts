export interface ApiResponse<T> {
  status: 'SUCCESS' | 'ERROR';
  data: T;
  error: string | null;
  timestamp: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
