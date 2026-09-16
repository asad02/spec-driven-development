export interface FieldError { field: string; message: string; }

export interface ApiError {
  code: string;
  message: string;
  correlationId?: string;
  fieldErrors?: FieldError[];
}

export interface AppError {
  status: number;
  code: string;
  message: string;
  correlationId?: string;
  fieldErrors?: FieldError[];
}
