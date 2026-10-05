# Datos demo reales

Las migraciones `020-real-demo-seed.sql` y `021-simple-demo-users.sql` dejan una base PostgreSQL usable para demo sin depender de datos mock del frontend ni IDs legacy.

## Credenciales de personal

Cada cuenta usa una contrasena simple igual a su rol para facilitar el acceso durante la demo.

| Rol | Email | Contrasena |
| --- | --- | --- |
| Administracion | `admin@aurora.test` | `admin` |
| Recepcion | `recepcion@aurora.test` | `recepcion` |
| Limpieza | `limpieza@aurora.test` | `limpieza` |
| Conserjeria | `conserjeria@aurora.test` | `conserjeria` |
| Room Service | `roomservice@aurora.test` | `roomservice` |

## Accesos de huesped

Los huespedes no usan `/api/v1/auth/login`; entran por `POST /api/v1/guest/auth/link` con codigo de enlace.

| Huesped | Reserva | Codigo de huesped |
| --- | --- | --- |
| Ana Morales | `AUR-DEMO-001` | `HUESPED-DEMO-UNO` |
| Carlos Reyes | `AUR-DEMO-002` | `HUESPED-DEMO-DOS` |

Ambas reservas estan en estado `checked_in`, usan UUID reales y tienen folio abierto.

## Datos incluidos

- Roles y permisos para administracion, recepcion, limpieza, conserjeria y room service.
- Tipos de habitacion, habitaciones, tarifas, amenities y promocion demo.
- Dos huespedes con reservas activas y codigos de acceso distintos.
- Folios, deposito, pago, cargos, room service, inventario y caja demo.
- Solicitudes de housekeeping, concierge y mantenimiento, incluyendo una solicitud operativa sin reserva.

## Variables locales esperadas

PostgreSQL local por defecto:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/pms_hotel_db
SPRING_DATASOURCE_USERNAME=pms_user
SPRING_DATASOURCE_PASSWORD=pms_password
JWT_SECRET=01234567890123456789012345678901
```

Las contrasenas demo anteriores no son secretos productivos y deben cambiarse fuera de entornos de prueba.
