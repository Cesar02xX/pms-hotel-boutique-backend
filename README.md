# pms-hotel-boutique-backend

Backend del PMS Hotel Boutique Aurora construido con Spring Boot, PostgreSQL y Liquibase.

## Requisitos

- Java 21
- Maven Wrapper incluido en el repositorio
- Docker y Docker Compose

## Levantar PostgreSQL

```bash
docker compose up -d
```

PostgreSQL queda disponible en `localhost:5432` con la base `pms_hotel_db`.

## Ejecutar la API

En Windows:

```bash
mvnw.cmd spring-boot:run
```

En macOS/Linux:

```bash
./mvnw spring-boot:run
```

El backend se ejecuta en `http://localhost:8080`.

## Endpoints de desarrollo

- Health: `GET http://localhost:8080/api/v1/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Pruebas

En Windows:

```bash
mvnw.cmd test
```

En macOS/Linux:

```bash
./mvnw test
```

## Arquitectura base

El proyecto sigue una arquitectura en capas:

```text
HTTP
 ↓
Controller
 ↓
Service
 ↓
ServiceImpl
 ↓
Repository
 ↓
Model
 ↓
PostgreSQL
```

Estructura principal:

```text
src/main/java/com/aurora/pms/
├── config
├── controller
├── dto
│   ├── request
│   └── response
├── exception
├── mapper
├── model
├── repository
├── security
└── service
    └── impl
```

Liquibase es responsable de gestionar el esquema de base de datos. Hibernate queda configurado con `ddl-auto: validate`.
