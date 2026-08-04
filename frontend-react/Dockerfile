FROM node:20-alpine AS build

WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

FROM nginx:1.27-alpine

ENV OPENTALKING_UPSTREAM=host.docker.internal:8210 \
    NGINX_ENVSUBST_FILTER=OPENTALKING_UPSTREAM

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --chmod=755 nginx/15-resolve-opentalking-ipv4.envsh /docker-entrypoint.d/15-resolve-opentalking-ipv4.envsh
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80
