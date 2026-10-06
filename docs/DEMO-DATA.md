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

El flujo principal de autenticación para huéspedes es `POST /api/v1/guest/auth/login` con correo y contraseña:

| Huesped | Email | Contrasena | Reserva | Codigo de enlace (deprecado) |
| --- | --- | --- | --- | --- |
| Ana Morales | `ana.demo@aurora.test` | `huesped1` | `AUR-DEMO-001` | `HUESPED-DEMO-UNO` |
| Carlos Reyes | `carlos.demo@aurora.test` | `huesped2` | `AUR-DEMO-002` | `HUESPED-DEMO-DOS` |

Ambas reservas están en estado `checked_in`, usan UUID reales y tienen folio abierto.

> **Nota:** El acceso por código (`POST /api/v1/guest/auth/link`) se mantiene temporalmente por compatibilidad pero se encuentra **deprecado**.

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
