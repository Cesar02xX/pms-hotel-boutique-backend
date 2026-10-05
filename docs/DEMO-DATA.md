# Datos demo reales

La migracion `020-real-demo-seed.sql` deja una base PostgreSQL usable para demo sin depender de datos mock del frontend ni IDs legacy.

## Credenciales de personal

Todas estas cuentas usan la contrasena demo `AuroraDemo123!`.

| Rol | Email |
| --- | --- |
| Administracion | `admin.demo@aurora.test` |
| Recepcion | `recepcion.demo@aurora.test` |
| Limpieza | `limpieza.demo@aurora.test` |
| Conserjeria | `conserjeria.demo@aurora.test` |
| Room Service | `roomservice.demo@aurora.test` |

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
SECURITY_JWT_SECRET=01234567890123456789012345678901
```

La contrasena demo anterior no es un secreto productivo y debe cambiarse fuera de entornos de prueba.
