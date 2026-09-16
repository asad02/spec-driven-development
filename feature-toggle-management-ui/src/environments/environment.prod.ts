export const environment = {
  production: true,
  // Relative, and split by path: /api/v1/auth goes to a product backend because only
  // those issue tokens, everything else to the feature service. proxy.conf.json does
  // this in dev, nginx in the container. The SPA is never cross-origin.
  adminApiBaseUrl: '/api/v1/admin',
  authApiBaseUrl: '/api/v1',
};
