/** A stored user record as returned by the API. */
export interface User {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * The editable state of a user. Update is a full replacement (FR-3), so this
 * carries every editable field, and a null phone clears the stored one.
 */
export interface UserRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
}

export type SortField = 'firstName' | 'lastName' | 'email' | 'createdAt' | 'updatedAt';
export type SortDirection = 'asc' | 'desc';

export interface UserQuery {
  page: number;
  size: number;
  sort: SortField;
  direction: SortDirection;
  search: string;
}
