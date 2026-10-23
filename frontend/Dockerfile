FROM node:24.22.1-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# public settings baked into the bundle; pass the production Keycloak URL as a build argument
ARG VITE_KEYCLOAK_URL=http://localhost:8180
ARG VITE_KEYCLOAK_REALM=nio
ARG VITE_KEYCLOAK_CLIENT_ID=nio-frontend
ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL \
    VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM \
    VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID
RUN npm run build

# runs as an unprivileged user and listens on 8080
FROM nginxinc/nginx-unprivileged:1.31.7-alpine
COPY nginx/ /etc/nginx/templates/
COPY --from=build /app/dist /usr/share/nginx/html
# where the CSP lets the page reach; override for production
ENV NIO_AUTH_ORIGIN=http://localhost:8180 \
    NIO_MEDIA_ORIGIN=http://localhost:8333
EXPOSE 8080
