# Auditoría final de contratos frontend-backend

Issue: #72 `[BACKEND][INT-FINAL] Contratos faltantes para retirar db.ts y mocks restantes`.

Fecha de la auditoría: 2026-10-05.

Versiones auditadas:

- Backend: `develop` en `423306c` (`fix: seed simple users and guest portal permissions`).
- Frontend (`DougGM/pms-hotel-boutique`): `develop` en `c290614` (merge del PR #141).

Todas las rutas de este documento cuelgan de `/api/v1` y se verificaron en los
controllers del backend. No se agregaron endpoints ni datos mock como parte de
#72; el único cambio de código fue la regla de autorización de
`POST /bookings/{bookingId}/deposits/{depositId}/apply` (ver "HTTP y
seguridad").

## Objetivo

Documentar el resultado de la auditoría realizada para retirar definitivamente
la dependencia productiva de `db.ts`, mocks y persistencia local del frontend, y
dejar registrado qué contrato backend cubre cada flujo.

## Estado de datos locales

En el frontend auditado:

- `src/data/db.ts` ya no existe. Lo eliminó el PR #136 y la prueba
  `scripts/test-e2e-integration.mjs` verifica que siga sin existir y que nadie
  lo importe.
- No hay imports productivos de `mockPersistence` ni de `shared/mocks`.
- `localStorage` y `sessionStorage` solo se usan para la sesión
  (`src/services/authService.ts`), no como persistencia de datos del PMS.
- `src/services/mockUtils.ts` solo simula latencia y errores forzados
  (`?mockError=true`); no es una fuente de datos.
- Los fixtures de `tests/` y `scripts/fixtures/` son exclusivos de pruebas y no
  cuentan como dependencia productiva.

## Contratos backend disponibles

Los contratos protegidos exigen JWT de personal y el permiso indicado, o
`ROLE_ADMIN`, según `SecurityConfig`.

| Área | Contrato existente | Permiso |
| --- | --- | --- |
| API pública | `GET /public/room-types`, `GET /public/rates`, `GET /public/availability`, `POST /public/bookings` | Sin JWT (#62) |
| Habitaciones | `GET/POST /rooms`, `GET/PUT /rooms/{id}` | `rooms.read` / `rooms.write` |
| Tipos de habitación | `GET/POST /room-types`, `GET/PUT /room-types/{id}`; `GET /room-features` | `room-types.*`, `room-features.read` |
| Tarifas | `GET/POST /rates`, `PUT /rates/{id}` | `rates.read` / `rates.write` |
| Huéspedes | `GET/POST /guests`, `GET/PUT /guests/{id}` | `guests.read` / `guests.write` |
| Acompañantes | `GET/POST /bookings/{bookingId}/companions`, `PUT/DELETE /bookings/{bookingId}/companions/{companionId}` | `booking-companions.*` |
| Reservas | `GET/POST /bookings`, `GET/PUT /bookings/{id}` | `bookings.read` / `bookings.write` |
| Confirmación y cancelación | `POST /bookings/{id}/confirm`, `POST /bookings/{id}/cancel` | `bookings.write` |
| Check-in y check-out | `POST /bookings/{id}/check-in`, `POST /bookings/{id}/check-out` | `bookings.check-in` / `bookings.check-out` |
| Folio | `GET /bookings/{bookingId}/folio`, `POST /bookings/{bookingId}/folio/open`; listado global `GET /guest-accounts` | `folios.read` / `folios.write` |
| Cargos | `GET/POST /bookings/{bookingId}/charges`; listado global `GET /charges` | `charges.read` / `charges.write` |
| Anulación de cargos | `POST /bookings/{bookingId}/charges/{chargeId}/void` | `charges.write` |
| Pagos | `GET/POST /bookings/{bookingId}/payments`; listado global `GET /payments` | `payments.read` / `payments.write` |
| Depósitos | `GET/POST /bookings/{bookingId}/deposits`, `POST .../deposits/{depositId}/refund`, `POST .../deposits/{depositId}/apply`; listado global `GET /deposits` | `deposits.read` / `deposits.write` (ver nota en "HTTP y seguridad") |
| Caja | `POST /cash-sessions/open`, `GET /cash-sessions/current`, `GET/POST /cash-sessions/{id}/movements`, `POST /cash-sessions/{id}/close` | `cash.read` / `cash.write` |
| Housekeeping | `GET /housekeeping/rooms`, `GET /housekeeping/rooms/{roomId}`, `POST /housekeeping/rooms/{roomId}/start`, `/complete`, `/inspect`, `/stayover-cleanings`; `GET /housekeeping/rooms/stayover-cleanings`, `POST .../stayover-cleanings/{requestId}/start`, `/complete` | `housekeeping.read` / `housekeeping.write` |
| Room Service | `GET /room-service/products`, `GET/POST /room-service/orders`, `GET /room-service/orders/{orderId}`, `POST .../{orderId}/status`, `PATCH .../{orderId}/notes` | `room-service.read` / `room-service.write` |
| Conserjería | `GET/POST /concierge/requests`, `GET/PUT /concierge/requests/{requestId}`, `POST .../{requestId}/status` | `concierge.read` / `concierge.write` |
| Solicitudes de servicio y mantenimiento | `GET/POST /service-requests`, `GET /service-requests/{id}`, `POST /service-requests/{id}/status` | `service-requests.read` / `service-requests.write` |
| Inventario | `GET /inventory/items`, `GET /inventory/items/{itemId}`, `GET/POST /inventory/items/{itemId}/movements`; alta y edición `POST /admin/inventory/items`, `PUT /admin/inventory/items/{id}` | `inventory.read` / `inventory.write` |
| Administración | `/admin/users` (`GET`, `POST`, `GET/PUT /{id}`), `/admin/roles` (`GET`, `POST`, `PUT /{id}`, `PUT /{id}/permissions`), `GET /admin/permissions`, `/admin/amenities`, `/admin/promotions`, `/admin/room-service/products` | `ROLE_ADMIN`; amenidades `rooms.*`, promociones `rates.write`, productos `room-service.*` |
| Reportes | `GET /admin/reports/operations`, `GET /admin/audit-logs`, `GET /admin/bookings/{bookingId}/receipt` | `ROLE_ADMIN`; comprobante `folios.read` |
| Portal del huésped | `POST /guest/auth/link`; `GET /guest/stay`; `/guest/amenities`; `/guest/room-service/products` y `/orders`; `/guest/housekeeping/requests`; `/guest/concierge/requests`; `/guest/notifications` | `ROLE_GUEST` (JWT de huésped) |

## Resultado de la auditoría

| Flujo | Contrato backend | Estado | Acción |
| --- | --- | --- | --- |
| `db.ts`, mocks y persistencia local | — | Resuelto | Ninguna (PRs #133 y #136 del frontend) |
| Web pública: catálogo, disponibilidad y reserva | `/public/*` | Resuelto | Ninguna (#62 backend, #127/PR #141 frontend) |
| Recepción operativa (rol recepción: reservas, check-in/out, folio) | `/bookings`, `/bookings/{id}/check-in`, `/bookings/{id}/check-out`, `/bookings/{bookingId}/folio`, `/bookings/{bookingId}/charges`, `/bookings/{bookingId}/payments`, `/bookings/{bookingId}/deposits` | Resuelto | Ninguna |
| Vista previa de Recepción del ADMIN (nueva reserva, edición, cambio de habitación, check-in/out, cancelar, anular, cargos, pagos, depósitos, bloqueos) | Los mismos contratos de reservas y folio; bloqueos vía estado de habitación y `/service-requests` | Backend disponible | Integración pendiente en el frontend |
| "Completar tarea" en el resumen operativo | `POST /service-requests/{id}/status`, `POST /room-service/orders/{orderId}/status` | Backend disponible | Integración pendiente en el frontend |
| Alta de artículo de inventario | `POST /admin/inventory/items` | Backend disponible | Integración pendiente en el frontend |
| Portal huésped: perfil, otras reservas, folio detallado y recibo | No hay contratos `/guest/*` para esas vistas | Fuera del alcance actual | El portal muestra "Consulta en recepción" |
| Tarifas dinámicas y análisis comercial | Sin contrato | Fuera del alcance actual | Retiradas del menú del frontend |

## Decisiones de alcance

1. **No se crea un endpoint adicional de "anular reserva".** El contrato de
   cancelación (`POST /bookings/{id}/cancel`) cubre el alcance actual. Si negocio
   necesita diferenciar anulación de cancelación, debe tratarse en un ticket
   independiente.
2. **No se crea una entidad ni endpoint de bloqueo temporal de habitación con
   rango de fechas dentro de #72.** El PMS dispone de estados de habitación
   (`maintenance`, `out_of_service` mediante `PUT /rooms/{id}`) y de
   solicitudes de mantenimiento (`/service-requests`). Un bloqueo programado con
   fechas debe definirse como funcionalidad nueva en un ticket separado.
3. **Los pendientes de la vista previa administrativa son de integración
   frontend** y no justifican duplicar endpoints en el backend.

## HTTP y seguridad

- Los contratos protegidos usan la autenticación JWT existente
  (`JwtAuthenticationFilter`) y las reglas de `SecurityConfig`:
  `hasAnyAuthority(ROLE_ADMIN, <permiso>)` para personal, `ROLE_GUEST` para
  `/guest/**` y `permitAll` solo para salud, autenticación, Swagger y los cuatro
  contratos públicos exactos.
- Los permisos son las claves de `SecurityPermissions`: `rooms.*`,
  `room-types.*`, `room-features.read`, `rates.*`, `guests.*`, `bookings.*`
  (incluye `bookings.check-in` y `bookings.check-out`), `booking-companions.*`,
  `housekeeping.*`, `room-service.*`, `payments.*`, `deposits.*`, `folios.*`,
  `charges.*`, `inventory.*`, `cash.*`, `concierge.*`, `service-requests.*` y
  `guest-portal.*`.
- Los errores usan el formato común `ApiErrorResponse` mediante
  `GlobalExceptionHandler` (`400`, `401`, `403`, `404`, `409`); el detalle por
  módulo está en [`REGLAS-NEGOCIO.md`](REGLAS-NEGOCIO.md).
- **Hallazgo corregido en #72:** la auditoría detectó que
  `POST /bookings/{bookingId}/deposits/{depositId}/apply` no tenía una regla de
  permiso propia en `SecurityConfig` y caía en `anyRequest().authenticated()`:
  cualquier usuario autenticado del personal podía aplicar un depósito al folio.
  Fue la única ruta, de las 128 revisadas, en esa situación. Se agregó la regla
  `hasAnyAuthority(ROLE_ADMIN, deposits.write)`, la misma que ya protegen crear
  (`POST /bookings/{bookingId}/deposits`) y reembolsar
  (`POST .../deposits/{depositId}/refund`) un depósito; en los datos actuales,
  `deposits.write` lo tienen `admin` y `reception` (migración
  `008-security-permissions.sql`). La prueba
  `PaymentDepositControllerTest#applyDepositRequiresDepositsWritePermission`
  cubre sin token (`401`), sin `deposits.write` (`403`, sin cambiar depósito ni
  saldo), con `deposits.write` (`200`) y con solo `ROLE_ADMIN` (`200`).

## Datos demo y configuración

Los datos demo reales (migraciones `020-real-demo-seed.sql` y
`021-simple-demo-users.sql`), las credenciales del personal, los códigos de
acceso de huésped y las variables locales esperadas están en
[`DEMO-DATA.md`](DEMO-DATA.md). La configuración por defecto vive en
`src/main/resources/application.yaml` y `docker-compose.yml` (ver `README.md`).

## Verificación

`mvn clean install` sobre `develop` (`423306c`), antes de la corrección:

```text
BUILD SUCCESS
Tests run: 645, Failures: 0, Errors: 0, Skipped: 0
```

`mvn clean install` con la regla de `apply` y su prueba de regresión:

```text
BUILD SUCCESS
Tests run: 646, Failures: 0, Errors: 0, Skipped: 0
```

## Criterios de aceptación de #72

| Criterio | Estado | Evidencia |
| --- | --- | --- |
| Lista documentada de cada uso restante de `db.ts` y su endpoint equivalente | Cumplido | "Estado de datos locales" y "Resultado de la auditoría" |
| Endpoints faltantes implementados o documentados como fuera de alcance | Cumplido | No falta ningún endpoint para los flujos actuales; lo fuera de alcance está en "Resultado de la auditoría" y "Decisiones de alcance" |
| Ningún flujo principal necesita datos mock para guardar o consultar información real | Cumplido en el backend | Todos los flujos tienen contrato real; las integraciones pendientes son del frontend ("Pendientes externos") |
| Validaciones y errores HTTP consistentes | Cumplido | `GlobalExceptionHandler` y `REGLAS-NEGOCIO.md` |
| Permisos por rol cubiertos | Cumplido | Las 128 rutas tienen regla específica en `SecurityConfig`; la de `apply` se corrigió en #72 |
| Tests para los contratos | Cumplido | Pruebas de controller y seguridad existentes, más la prueba de regresión de `apply` |
| Documentación de variables y datos demo | Cumplido | `DEMO-DATA.md` |
| `mvn clean install` pasa | Cumplido | 646 pruebas, 0 fallos y 0 errores |

## Pendientes externos (frontend)

Observaciones para el repositorio web; no se implementan aquí:

- Conectar las acciones locales de la vista previa de Recepción del ADMIN a los
  contratos existentes de reservas y folio.
- Persistir "Completar tarea" con los contratos de estado de solicitudes y
  pedidos.
- Conectar el alta de inventario con `POST /admin/inventory/items`.
