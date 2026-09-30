# Reglas de negocio — PMS Hotel Boutique Aurora (backend)

Resumen de las reglas de negocio **implementadas actualmente** en el backend,
separadas por módulo. Cada regla sale del código (services, DTOs y queries),
no de la planificación. Si cambias una regla en el código, actualiza este
documento.

Todas las rutas cuelgan de `/api/v1`.

Al final, la sección **13. Decisiones por confirmar** lista los comportamientos
que hoy funcionan de una forma pero que el equipo debería validar.

---

## 0. Reglas generales (aplican a toda la API)

- **Seguridad:** todos los endpoints requieren JWT (`Authorization: Bearer <token>`),
  excepto `/health`, `/auth/login`, `/auth/refresh`, `/auth/logout` y Swagger.
  Sin token o con token inválido → `401`.
- **Formato de error único:** todos los errores usan `ApiErrorResponse` a través de
  `GlobalExceptionHandler`.
- **Códigos HTTP:**
  - `400`: validación de DTO fallida (incluye `errors` por campo), regla de
    negocio incumplida, JSON mal formado, enum/fecha/UUID inválido en el body
    o UUID inválido en la ruta.
  - `404`: el recurso principal de la ruta no existe.
  - `401`: sin autenticación.
  - `500`: error inesperado (mensaje genérico, sin detalles internos).
- **Referencias en el body:** cuando un ID enviado en el body no existe (por
  ejemplo `roomTypeId` al crear una habitación), la respuesta es `400`, no
  `404`. El `404` se reserva para el recurso de la ruta.
- **Dinero:** siempre en **centavos enteros** (`Long`). La moneda es siempre
  `GTQ`. En pagos, depósitos y caja, los montos con decimales (`1.5`) se
  **rechazan** con `400` en lugar de truncarse.
- **Campos controlados por el servidor:** IDs, estados iniciales, moneda,
  timestamps (`createdAt`, `updatedAt`, `paidAt`, `openedAt`, etc.) y usuario
  responsable los asigna el backend. Si el cliente los envía en el body, se
  **ignoran**.
- **Textos:** los nombres, códigos y conceptos se guardan sin espacios al inicio
  ni al final (`trim`).
- **Entidades JPA:** nunca se exponen directamente; siempre se usan DTOs de
  request/response.
- **Transacciones:** las operaciones que modifican datos son transaccionales.
  Las que pueden sufrir concurrencia (check-in, folio, pagos, reembolsos,
  caja) usan bloqueo pesimista de fila.

---

## 1. Autenticación (`/auth`)

- **Login:** email (formato válido) y contraseña son obligatorios. Ante
  cualquier fallo (usuario inexistente, contraseña incorrecta, usuario
  deshabilitado) se responde siempre `"Invalid email or password"`, para no
  revelar qué dato falló.
- **Usuario inactivo:** un usuario con `status = inactive` queda deshabilitado
  y no puede autenticarse.
- **Respuesta del login:** `accessToken` (JWT), `refreshToken`, tipo `Bearer` y
  la expiración en segundos.
- **Permisos:** el token incluye el rol como `ROLE_<CODIGO_ROL>` y las
  *keys* de los permisos asociados al rol.
- **Refresh tokens:**
  - Son valores aleatorios de 64 bytes. En la BD solo se guarda su **hash
    SHA-256**, nunca el valor original.
  - **Rotación:** cada `/auth/refresh` revoca el token usado y emite uno nuevo.
    Un token no se puede reutilizar.
  - Un token es inválido si está revocado, expirado, sin usuario o si el
    usuario no está `active`.
  - `/auth/logout` revoca el refresh token enviado.
  - Expiraciones configurables: access token de 30 min y refresh token de
    7 días por defecto.

---

## 2. Tipos de habitación (`/room-types`)

- Campos obligatorios: `code`, `name` y `capacity` (mayor que 0).
- **`code` único:** no puede repetirse al crear ni al actualizar.
- **Características (`roomFeatureIds`):**
  - Todas deben existir; si falta alguna → `400` con la lista de IDs faltantes.
  - Los IDs repetidos se ignoran (no se duplican asociaciones).
  - Al actualizar, si se envía la lista, **reemplaza** las asociaciones: quita
    las que ya no están y agrega las nuevas. Si no se envía, no se tocan.
- La actualización es parcial: solo cambian los campos enviados, y los
  campos de texto enviados no pueden estar en blanco.

## 3. Características de habitación (`/room-features`)

- Catálogo de solo lectura: se listan ordenadas por nombre.

## 4. Habitaciones (`/rooms`)

- Campos obligatorios: `roomNumber` y `roomTypeId`. El tipo debe existir.
- **`roomNumber` único** al crear y al actualizar.
- Estados por defecto al crear: `status = available` y
  `housekeepingStatus = dirty`.
- La actualización es parcial; si se envía `roomNumber`, no puede estar en
  blanco.

## 5. Tarifas (`/rates`)

- Campos obligatorios: `roomTypeId` (debe existir), `name`, `validFrom`,
  `priceCents` (≥ 0) y `minimumNights` (> 0).
- `currency` solo acepta `GTQ`.
- **Vigencia:** `validTo` (opcional) debe ser igual o posterior a `validFrom`.
  En una actualización parcial se valida contra los valores resultantes.

---

## 6. Huéspedes (`/guests`)

- Campos obligatorios: `firstName` y `lastName`. Si se envía `email`, debe
  tener formato válido.
- `createdAt` nunca cambia al actualizar.
- Listado ordenado por apellido y nombre.

## 7. Reservas (`/bookings`)

### Creación y edición
- Campos obligatorios: `guestId`, `roomTypeId`, `checkIn`, `checkOut`,
  `adults` (≥ 1) y `children` (≥ 0). `roomId` y `rateId` son opcionales.
- Todas las referencias (huésped, tipo, habitación, tarifa) deben existir.
- **Fechas:** `checkIn` debe ser anterior a `checkOut`.
- **Capacidad:** `adults + children` no puede superar la capacidad del tipo de
  habitación.
- **Coherencia:** la habitación y la tarifa, si se envían, deben pertenecer al
  mismo tipo de habitación de la reserva.
- **Disponibilidad:** una habitación no puede tener dos reservas activas
  (`pending`, `confirmed`, `checked_in`) con fechas que se crucen. El día de
  salida de una reserva puede ser el de entrada de la siguiente.
- **Total:** `totalAmountCents = precio de la tarifa × noches`. Sin tarifa, el
  total es 0. Se recalcula en cada actualización.
- Valores asignados al crear: `status = pending`, `currency = GTQ` y
  dos códigos únicos aleatorios, `confirmationCode` (`BKG-XXXXXXXX`) y
  `guestLinkCode` (`GL-XXXXXXXX`). Los códigos usan caracteres sin ambigüedad
  (sin 0/O ni 1/I).
- La actualización es parcial y todas las validaciones se aplican de nuevo
  sobre el resultado final.

### Check-in (`POST /bookings/{id}/check-in`)
- Solo reservas en estado `confirmed`. Si ya está `checked_in`, se responde
  `"Booking is already checked in"`.
- **Fecha:** solo dentro de la estadía, es decir `checkIn ≤ hoy < checkOut`.
  "Hoy" se calcula en la zona horaria del hotel (`America/Guatemala`).
- La reserva debe tener una habitación asignada que:
  - pertenezca al tipo de la reserva;
  - esté `available`;
  - esté limpia (`clean` o `inspected`);
  - no tenga otra reserva activa que se cruce.
- **Composición:** huésped principal + acompañantes debe coincidir
  **exactamente** con los `adults` y `children` declarados, y no superar la
  capacidad.
- Resultado: la reserva pasa a `checked_in` y la habitación a `occupied`.
- Se bloquea la reserva para evitar dos check-ins simultáneos.

## 8. Acompañantes (`/bookings/{bookingId}/companions`)

- Campos obligatorios: `firstName`, `lastName` y `guestType` (`adult`/`child`).
- **El huésped principal no puede registrarse como acompañante.** Se detecta
  por el mismo número de documento o por el mismo nombre y apellido
  (ignorando mayúsculas y espacios).
- **Composición:** el huésped principal cuenta como 1 adulto. Con los
  acompañantes:
  - los adultos no pueden superar los `adults` declarados en la reserva;
  - los niños no pueden superar los `children` declarados;
  - el total no puede superar la capacidad del tipo de habitación.
- El acompañante debe pertenecer a la reserva de la ruta; si no → `404`.

---

## 9. Folio / cuenta del huésped (`/bookings/{bookingId}/folio`, `/charges`)

- **Apertura idempotente:** `POST /folio/open` crea la cuenta (`201`) o
  devuelve la existente sin cambios (`200`). Hay una sola cuenta por reserva,
  asociada al huésped principal.
- No se puede abrir folio si la reserva está `checked_out`, `cancelled` o
  `no_show`.
- **Saldo inicial:** al abrir, el saldo se calcula con los cargos no anulados
  menos los pagos completados que ya tuviera la reserva.
- **Saldo:** `balance = cargos no anulados − pagos completados`. Se mantiene
  actualizado en cada movimiento.
- **Cargos:**
  - Requieren un folio **abierto** (`404` si no existe, `400` si no está
    abierto).
  - Campos obligatorios: `description`, `quantity` (> 0), `unitPriceCents`
    (≥ 0) y `category`. `productId` es opcional y, si se envía, debe existir.
  - `amountCents = quantity × unitPriceCents`, calculado en el backend. Si el
    resultado desborda → `400`.
  - Se crean con `status = posted` y suman al saldo.
- **Anular cargo:** requiere `reason`, no se puede anular dos veces y resta
  el monto del saldo.

## 10. Pagos (`/bookings/{bookingId}/payments`)

- Campos obligatorios: `amountCents` (> 0, entero) y `method`.
  `transactionReference` es opcional.
- No hay pasarela de pago: el pago se registra directamente como
  `completed`, con `paidAt` igual al momento del registro.
- Se guarda el usuario autenticado que lo procesó.
- **Efecto en el folio:**
  - con folio abierto, el pago resta del saldo;
  - sin folio, el pago queda pendiente y se descuenta cuando se abra;
  - con folio no abierto (cerrado) → `400`.
- Se bloquea la reserva para que un pago y una apertura de folio simultáneos
  no descuadren el saldo.

## 11. Depósitos (`/bookings/{bookingId}/deposits`)

- Campos obligatorios: `amountCents` (> 0, entero) y `method`. `notes` es
  opcional.
- Se crean como `held` (retenido), con `collectedAt` igual al momento del
  registro.
- **Los depósitos no afectan el saldo del folio:** son una garantía, no un
  pago.
- **Reembolso:** solo depósitos `held`. Si ya fue reembolsado, se responde
  `"Deposit is already refunded"`. El reembolso pasa el depósito a `refunded`,
  registra `refundedAt` y, si se envía un motivo, lo agrega a las notas como
  `Refund: <motivo>`.
- Se bloquea el depósito para evitar dos reembolsos simultáneos.

---

## 12. Caja (`/cash-sessions`)

- **Una sola caja abierta a la vez en todo el hotel.** El modelo no distingue
  cajas ni terminales. Una sesión puede cerrarla un usuario distinto del que
  la abrió.
- **Apertura (`POST /open`):**
  - `openingBalanceCents` es obligatorio (≥ 0, entero). `notes` es opcional.
  - Si ya hay una sesión abierta → `400`.
  - El usuario autenticado debe existir en la tabla `users`, porque queda
    como `openedByUser`; si no existe → `401`.
  - Se crea con `status = open` y `currency = GTQ`.
  - Las aperturas simultáneas se serializan con un bloqueo de PostgreSQL, así
    que nunca quedan dos sesiones abiertas.
- **Sesión actual (`GET /current`):** devuelve la sesión abierta con sus
  totales calculados en vivo. Si no hay ninguna → `404`.
- **Movimientos (`POST /{id}/movements`):**
  - Campos obligatorios: `type` (`income`/`expense`), `concept` (no vacío,
    máx. 255) y `amountCents` (> 0, entero).
  - No se registran en una sesión cerrada (`400`).
  - **Un egreso no puede superar el efectivo disponible** (apertura +
    ingresos − egresos). La caja nunca queda en negativo.
  - Se guarda el usuario responsable y `occurredAt` es el momento del
    registro.
  - Son movimientos manuales: no hay integración automática con pagos, así
    que `paymentId` queda en `null`.
- **Listado de movimientos (`GET /{id}/movements`):** ordenado
  cronológicamente. Si la sesión no existe → `404`.
- **Cierre (`POST /{id}/close`):**
  - `countedBalanceCents` es obligatorio (≥ 0, entero): lo contado
    físicamente.
  - Solo se cierra una sesión abierta; cerrar dos veces → `400`.
  - El backend calcula y guarda los totales; los que envíe el cliente se
    ignoran:
    - `expectedBalanceCents = apertura + ingresos − egresos`
    - `differenceCents = contado − esperado` (negativo = faltante,
      positivo = sobrante)
  - Registra `closedByUser` y `closedAt`. Las notas del cierre se agregan a
    las de apertura.
- Cerrar y registrar movimientos bloquea la sesión, así que un movimiento no
  puede entrar mientras la sesión se está cerrando.

---

## 13. Decisiones por confirmar

Comportamientos que **hoy funcionan así en el código** pero que no fueron
definidos explícitamente por el equipo, o que quedaron fuera de los tickets.
Cada punto indica qué hace el sistema hoy y qué hay que decidir. Cuando se
decida, se mueve la regla a su módulo y se borra de aquí.

### General / seguridad
- **Roles y permisos.** Hoy el JWT incluye el rol y los permisos, pero
  **ningún endpoint los valida**: cualquier usuario autenticado puede hacer
  todo, incluido abrir y cerrar caja, anular cargos o reembolsar depósitos.
  → ¿Qué roles pueden usar cada módulo?
- **409 Conflict.** Los conflictos (código duplicado, caja ya abierta, cargo
  ya anulado, depósito ya reembolsado…) responden `400`, porque no existe un
  manejo de `409`. → ¿Se mantiene `400` o se agrega `409` en
  `GlobalExceptionHandler`?

### Catálogo (habitaciones, tipos, tarifas)
- **Cambio manual del estado de una habitación.** `PUT /rooms/{id}` permite
  cambiar `status` y `housekeepingStatus` libremente; por ejemplo, poner
  `available` una habitación ocupada. → ¿Se restringen las transiciones?
- **Tarifas que se cruzan.** Se pueden crear varias tarifas del mismo tipo de
  habitación con vigencias que se cruzan. → ¿Se permite?
- **Reducir la capacidad de un tipo.** Se puede bajar `capacity` aunque haya
  reservas que ya la superan. → ¿Se valida?

### Huéspedes
- **Duplicados.** No se valida que el documento o el email sean únicos, así
  que el mismo huésped puede registrarse varias veces. → ¿Se valida?

### Reservas y check-in
- **Estado editable libremente.** `PUT /bookings/{id}` acepta `status` sin
  validar transiciones: se puede pasar de `pending` a `checked_out`, o
  reactivar una `cancelled`. → ¿Cuál es el flujo permitido de estados?
- **Validez de la tarifa.** Al reservar no se valida que la tarifa esté
  vigente en las fechas de la estadía, que esté `active` ni que se cumpla
  `minimumNights`. → ¿Se validan?
- **Estado de la habitación al reservar.** Una habitación en `maintenance` u
  `out_of_service` se puede asignar a una reserva; solo se bloquea en el
  check-in. → ¿Se valida al reservar?
- **Edición después del check-in.** Una reserva `checked_in` se puede editar
  (fechas, habitación, huéspedes) sin restricciones adicionales. → ¿Se
  bloquea o se limita?
- **Check-in anticipado o tardío.** Solo se permite dentro de la estadía
  (`checkIn ≤ hoy < checkOut`). → ¿Se permite con alguna tolerancia?
- **Check-out.** No está implementado: la habitación no se libera, el folio
  no se cierra y la reserva no pasa a `checked_out`.

### Acompañantes
- **Cambios después del check-in.** Se pueden agregar, editar o borrar
  acompañantes de una reserva ya `checked_in`, y también de una cancelada.
  → ¿Se bloquean según el estado de la reserva?

### Folio, pagos y depósitos
- **Cierre del folio.** Existe el estado `closed` pero no hay endpoint para
  cerrar el folio (depende del check-out).
- **Sobrepago.** Un pago puede superar el saldo pendiente, dejando el saldo
  en negativo (a favor del huésped). → ¿Se permite?
- **Pagos y depósitos en reservas cerradas.** Se pueden registrar en
  reservas `cancelled`, `no_show` o `checked_out` si no tienen folio. → ¿Se
  bloquea?
- **Reembolso de pagos.** No existe; solo se pueden reembolsar depósitos.
- **Depósito aplicado.** Existe el estado `applied` (depósito usado como pago),
  pero ningún flujo lo usa. → ¿Cuándo y cómo se aplica un depósito al folio?
- **Cargos de 0.** Se permiten cargos con `unitPriceCents = 0` (cortesías).
  → ¿Es intencional?

### Caja
- **Una caja o una por cajero.** Hoy hay una sola sesión abierta en todo el
  hotel, y cualquier usuario puede cerrarla. → ¿Se confirma o se pasa a una
  sesión por usuario?
- **`GET /current` sin caja abierta.** Responde `404`. → ¿El frontend
  prefiere `200` con `null` o `204`?
- **Egresos mayores que el efectivo.** Se rechazan para que la caja nunca
  quede en negativo. → ¿Se confirma?
- **Notas de cierre.** Se agregan a las de apertura en vez de reemplazarlas.
- **Integración con pagos.** Los pagos en efectivo **no** generan un ingreso
  automático en caja (quedó fuera del ticket #16). → ¿Se integra en un ticket
  futuro? El campo `paymentId` ya existe en `cash_movements`.
- **Reapertura y corrección.** Una sesión cerrada no se puede reabrir, y un
  movimiento no se puede anular ni editar. → ¿Se necesita algún mecanismo de
  corrección?
