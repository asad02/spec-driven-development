# ---- build ----
FROM node:22-alpine AS build
WORKDIR /app

# npm ci needs both files and installs exactly what the lockfile pins.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# angular.json sets production as the default configuration, which applies the
# environment.prod.ts file replacement.
RUN npm run build

# ---- runtime ----
FROM nginx:1.29-alpine AS runtime

COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
  CMD ["sh", "-c", "wget -q -O /dev/null http://127.0.0.1/ || exit 1"]
