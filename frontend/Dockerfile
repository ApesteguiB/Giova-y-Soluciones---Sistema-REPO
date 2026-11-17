FROM node:24.19.0-alpine AS build
WORKDIR /workspace/frontend

COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginx:1.29-alpine
COPY infra/nginx/nginx.conf /etc/nginx/conf.d/default.conf
COPY infra/nginx/giova /etc/nginx/giova
COPY --from=build /workspace/frontend/dist/frontend/browser /usr/share/nginx/html
COPY frontend/public-soluciones /usr/share/nginx/html/soluciones
COPY frontend/public-giova /usr/share/nginx/html/giova
# El dominio de Soluciones usa su propia portada en "/", así que la aplicación
# Angular se publica además como /app.html para servir el sistema interno.
RUN cp /usr/share/nginx/html/index.html /usr/share/nginx/html/app.html \
    && chmod -R a+rX /usr/share/nginx/html /etc/nginx/giova
EXPOSE 80
