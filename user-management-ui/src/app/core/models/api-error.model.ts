export interface FieldError {
  field: string;
  message: string;
}

/** The single error envelope every backend failure uses. */
export interface ApiError {
  code: string;
  message: string;
  correlationId?: string;
  fieldErrors?: FieldError[];
}

/** What the interceptor hands components: always displayable, never a raw status. */
export interface AppError {
  status: number;
  code: string;
  message: string;
  correlationId?: string;
  fieldErrors: FieldError[];
}
