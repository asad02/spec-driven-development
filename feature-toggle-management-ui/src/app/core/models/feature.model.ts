/** Mirrors FeatureView on the server. `isProtected` is server-computed. */
export interface Feature {
  uid: string;
  enabled: boolean;
  description: string | null;
  roles: string[];
  properties: Record<string, string | null>;
  isProtected: boolean;
  protectedReason: string | null;
}

export interface FeatureUpsert {
  uid: string;
  description: string | null;
  enabled: boolean;
  roles: string[];
}

export interface AuditEntry {
  at: string;
  user: string;
  action: string;
  feature: string;
  detail: string | null;
}

/** The only two roles this system uses (technical spec D-7). */
export const KNOWN_ROLES = ['ROLE_ADMIN', 'ROLE_VIEWER'] as const;
