export type BackendProvider = 'micronaut' | 'springboot';

/** Shape returned by GET /api/v1/features on the Spring Boot service. */
export interface FeatureState {
  activeBackend: BackendProvider;
  upstreamHost: string;
  flag: string;
  store: string;
  /** Caller holds the flag's adminRole custom property. */
  canToggle: boolean;
  /** Caller's roles satisfy the user-data-access policy. */
  canAccessData: boolean;
  /** Caller's roles are all listed in the readOnlyRoles custom property. */
  readOnly: boolean;
  /** The ACL itself, for display. */
  dataRoles: string[];
}

export const BACKEND_LABELS: Record<BackendProvider, string> = {
  micronaut: 'Micronaut',
  springboot: 'Spring Boot',
};
