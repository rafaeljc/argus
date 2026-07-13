export interface SuccessEnvelope<T> {
  data: T;
}

export interface PaginationMeta {
  total: number;
  page: number;
  per_page: number;
  total_pages: number;
}

export interface PaginationLinks {
  self: string;
  next: string | null;
  prev: string | null;
  last: string;
}

export interface PaginatedEnvelope<T> {
  data: T[];
  meta: PaginationMeta;
  links: PaginationLinks;
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export interface ErrorEnvelope {
  error: {
    code: string;
    message: string;
    details?: FieldError[];
  };
}

export type Paginated<T> = PaginatedEnvelope<T>;
