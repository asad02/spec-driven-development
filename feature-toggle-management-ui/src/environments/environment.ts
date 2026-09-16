export const environment = {
  production: false,
  // Relative: the dev server proxies /api to springboot-backend (proxy.conf.json),
  // and nginx does the same in the container. The SPA is never cross-origin.
  adminApiBaseUrl: '/api/v1/admin',
  authApiBaseUrl: '/api/v1',
};
