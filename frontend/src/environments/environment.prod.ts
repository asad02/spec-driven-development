export const environment = {
  production: true,
  // nginx serves the SPA and proxies /api to the backend, so this stays
  // relative. Point it at an absolute origin only if the API is on another host.
  apiBaseUrl: '/api/v1',
};
