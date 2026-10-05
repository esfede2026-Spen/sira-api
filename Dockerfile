# Etapa 1: compilar SIRA API con Maven y Java 21
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copiar primero el POM para aprovechar la cache de dependencias
COPY pom.xml ./
RUN mvn -B dependency:go-offline

# Copiar el codigo fuente y generar el JAR ejecutable
COPY src ./src
RUN mvn -B clean package -DskipTests

# Etapa 2: imagen liviana para ejecutar la API
FROM eclipse-temurin:21-jre

WORKDIR /app

# Usuario sin privilegios
RUN useradd --system --uid 10001 sira

# Copiar el JAR generado en la etapa anterior
COPY --from=build /app/target/*.jar /app/app.jar

# Render asigna el puerto mediante la variable PORT
ENV PORT=10000
EXPOSE 10000

USER sira

# Spring Boot escuchara en el puerto entregado por Render
CMD ["sh", "-c", "java -Dserver.address=0.0.0.0 -Dserver.port=${PORT} -jar /app/app.jar"]
