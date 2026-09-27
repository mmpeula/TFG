# ---------- Etapa 1: compilacion ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Se copia primero el pom para aprovechar la cache de capas de Docker:
# las dependencias solo se vuelven a descargar si cambia el pom.xml.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Los tests de integracion (*IT) necesitan Docker, por eso no se ejecutan aqui.
RUN mvn -B -q clean package -DskipTests

# ---------- Etapa 2: ejecucion ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# curl lo usa el healthcheck de docker-compose
RUN apk add --no-cache curl \
 && addgroup -S kanban && adduser -S kanban -G kanban

COPY --from=build /build/target/*.jar app.jar

# El directorio de adjuntos se crea con el usuario correcto: al montar encima un
# volumen vacio, Docker hereda estos permisos y la aplicacion puede escribir.
RUN mkdir -p /app/uploads && chown -R kanban:kanban /app

USER kanban

EXPOSE 8080
# preferIPv4Stack: sin esto, la JVM prueba primero la direccion IPv6 de Groq y,
# en redes Docker sin salida IPv6 de verdad, la primera llamada falla o se hace
# lenta antes de caer a IPv4.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.net.preferIPv4Stack=true"
ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
