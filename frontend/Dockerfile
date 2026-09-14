# ---------- Etapa 1: build ----------
FROM node:22-alpine AS build
WORKDIR /app

# package*.json primero para aprovechar la cache de Docker
COPY package*.json ./
RUN npm ci

COPY . .
# En Docker la API se sirve en el mismo origen a traves de Nginx.
ENV VITE_API_BASE_URL=/api
RUN npm run build

# ---------- Etapa 2: servidor estatico ----------
FROM nginx:1.27-alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
