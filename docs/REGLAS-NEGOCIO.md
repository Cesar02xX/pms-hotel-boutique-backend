# Reglas de negocio — PMS Hotel Boutique Aurora (backend)

Resumen de las reglas de negocio **implementadas actualmente** en el backend,
separadas por módulo. Cada regla sale del código (services, DTOs y queries),
no de la planificación. Si cambias una regla en el código, actualiza este
documento.

Todas las rutas cuelgan de `/api/v1`.

Al final, la sección **20. Decisiones acordadas pendientes de implementación**
lista las reglas ya decididas por el equipo que todavía no deben asumirse como
implementadas en Java.

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
  - `409`: conflicto con datos existentes o con el estado financiero de la
    reserva, por ejemplo huéspedes duplicados, sobrepagos o checkout con saldo
    distinto de cero.
  - `500`: error inesperado (mensaje genérico, sin detalles internos).
- **Referencias en el body:** cuando un ID enviado en el body no existe (por
  ejemplo `roomTypeId` al crear una habitación), la respuesta es `400`, no
  `404`. El `404` se reserva para el recurso de la ruta.
  **Excepción actual:** Room Service responde `404` cuando no existen el
  `bookingId` o el `productId` del body (ver sección 17).
- **Dinero:** siempre en **centavos enteros** (`Long`). La moneda es siempre
  `GTQ`. En pagos, depósitos y caja, los montos con decimales (`1.5`) se
  **rechazan** con `400` en lugar de truncarse. Lo mismo aplica a las
  cantidades de los movimientos de inventario y de los ítems de Room Service.
- **Campos controlados por el servidor:** IDs, estados iniciales, moneda,
  timestamps (`createdAt`, `updatedAt`, `paidAt`, `openedAt`, etc.) y usuario
  responsable los asigna el backend. Si el cliente los envía en el body, se
  **ignoran**.
- **Textos:** los nombres, códigos y conceptos se guardan sin espacios al inicio
  ni al final (`trim`).
- **Entidades JPA:** nunca se exponen directamente; siempre se usan DTOs de
  request/response.
- **Transacciones:** las operaciones que modifican datos son transaccionales.
  Las que pueden sufrir concurrencia (check-in, checkout, folio, pagos,
  depósitos, reembolsos, caja, conserjería, inventario, housekeeping y estados
  de Room Service) usan bloqueo pesimista de fila.

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

### Acceso de huésped (`/guest/auth/link`, `/guest/**`)
- El login de personal se mantiene separado del acceso de huésped. El huésped
  no recibe permisos de empleado; su JWT solo lleva `ROLE_GUEST`.
- `POST /guest/auth/link` acepta `guestLinkCode` y emite access token cuando
  la reserva está `checked_in` y la fecha actual cae dentro de la estadía
  (`checkIn <= hoy < checkOut`).
- Códigos inexistentes, vencidos, aún no activos o no utilizables responden
  `400`.
- Todas las rutas `/guest/**` derivan la reserva desde el JWT. El cliente no
  puede consultar o modificar recursos de otra estadía cambiando IDs.
- `GET /guest/stay` devuelve reserva, huésped titular, habitación, tipo de
  habitación, fechas, estado, saldo de folio y moneda.

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
- **Reducción de capacidad:** no se permite bajar `capacity` si existen
  reservas activas o futuras (`pending`, `confirmed`, `checked_in`) cuyo
  `adults + children` supere la nueva capacidad. El conflicto responde `409`.

## 3. Características de habitación (`/room-features`)

- Catálogo de solo lectura: se listan ordenadas por nombre.

## 4. Habitaciones (`/rooms`)

- Campos obligatorios: `roomNumber` y `roomTypeId`. El tipo debe existir.
- **`roomNumber` único** al crear y al actualizar.
- Estados por defecto al crear: `status = available` y
  `housekeepingStatus = dirty`.
- La actualización es parcial; si se envía `roomNumber`, no puede estar en
  blanco.
- `housekeepingStatus` no puede modificarse desde `PUT /rooms/{id}`. Los
  cambios de limpieza deben pasar por Housekeeping; intentarlo desde el CRUD
  normal responde `409`.

## 5. Tarifas (`/rates`)

- Campos obligatorios: `roomTypeId` (debe existir), `name`, `validFrom`,
  `priceCents` (≥ 0) y `minimumNights` (> 0).
- `currency` solo acepta `GTQ`.
- **Vigencia:** `validTo` (opcional) debe ser igual o posterior a `validFrom`.
  En una actualización parcial se valida contra los valores resultantes.
- **Sin superposición:** no se permiten tarifas del mismo tipo de habitación
  con vigencias que se crucen. Una `validTo` vacía se trata como vigencia
  abierta. El conflicto responde `409`.

---

## 6. Huéspedes (`/guests`)

- Campos obligatorios: `firstName` y `lastName`. Si se envía `email`, debe
  tener formato válido.
- `createdAt` nunca cambia al actualizar.
- Listado ordenado por apellido y nombre.
- **Unicidad** (al crear y al actualizar; en la actualización no se compara
  al huésped consigo mismo). Un duplicado responde `409`:
  - `email` único cuando se informa. La comparación no distingue mayúsculas
    de minúsculas.
  - La combinación `documentType + documentNumber` es única cuando ambos se
    informan. El mismo número con otro tipo de documento sí se permite.
  - Los opcionales vacíos o solo con espacios se guardan como `null` y pueden
    repetirse sin generar duplicados.
  - La base de datos lo refuerza con índices únicos parciales
    (`ux_guests_email`, `ux_guests_document`).

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
- **Tarifas:** si se envía tarifa, debe estar activa, pertenecer al tipo de
  habitación de la reserva, cubrir las noches de la estadía y cumplir
  `minimumNights`.
- **Habitaciones operables:** no se puede asignar a una reserva una habitación
  con estado operativo `maintenance` u `out_of_service`.
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
- `PUT /bookings/{id}` no permite cambiar `status`; los cambios importantes de
  estado se hacen mediante operaciones específicas.
- Una reserva `checked_in` no admite modificaciones estructurales por el
  `PUT` general (huésped, tipo, habitación, tarifa, fechas u ocupantes). Esas
  operaciones quedan reservadas para flujos específicos futuros.

### Confirmación y cancelación
- **Confirmar (`POST /bookings/{id}/confirm`):** solo reservas `pending`
  pueden pasar a `confirmed`. Confirmar cualquier otro estado responde `400`.
- **Cancelar (`POST /bookings/{id}/cancel`):** solo reservas `pending` o
  `confirmed` pueden pasar a `cancelled`.
- La cancelación exige `reason` no vacío; el backend lo guarda recortado en
  `cancellationReason`, registra `cancelledAt` y actualiza `updatedAt`.
- Reservas `checked_in`, `checked_out`, `cancelled` o `no_show` no pueden
  cancelarse por este endpoint.
- Ambas operaciones bloquean la reserva para serializar cambios de estado
  simultáneos.

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

### Checkout (`POST /bookings/{id}/check-out`)
- Solo reservas en estado `checked_in`.
- Debe existir un folio abierto para la reserva. Si no existe → `404`; si el
  folio no está abierto → `400`.
- El saldo del folio debe ser exactamente `0`. Cualquier saldo positivo o
  negativo bloquea el checkout con `409`.
- Resultado: la reserva pasa a `checked_out`, el folio queda `closed`, la
  habitación queda operativamente `available` y su `housekeepingStatus` pasa a
  `dirty`.
- El checkout no limpia la habitación; solo inicia el flujo posterior de
  Housekeeping.

## 8. Acompañantes (`/bookings/{bookingId}/companions`)

- Campos obligatorios: `firstName`, `lastName` y `guestType` (`adult`/`child`).
- La administración normal de acompañantes (crear, editar o eliminar) solo se
  permite antes del check-in, mientras la reserva esté en estado `pending` o
  `confirmed`. Si la reserva está `checked_in`, `checked_out`, `cancelled` o
  `no_show`, esas operaciones responden `409`.
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

- **Listado global:** `GET /guest-accounts` devuelve todas las cuentas de
  huésped persistidas, sin depender de un `bookingId`. Usa la misma respuesta
  de folio que el detalle por reserva, con cargos y totales calculados.
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
  - El monto total debe ser mayor que `0`; los cargos financieros normales de
    valor cero se rechazan con `400`.
  - Se crean con `status = posted` y suman al saldo.
  - Además de `POST /charges`, Room Service crea un cargo automáticamente al
    entregar un pedido, con estas mismas reglas (ver sección 17).
- **Anular cargo:** requiere `reason`, no se puede anular dos veces y resta
  el monto del saldo.
- **Listado global de cargos:** `GET /charges` devuelve todos los cargos
  persistidos, incluidos los anulados. Coexiste con
  `GET /bookings/{bookingId}/charges`, que sigue filtrando por reserva.
- **Cierre:** el folio se cierra exclusivamente como parte del checkout de la
  reserva.

## 10. Pagos (`/bookings/{bookingId}/payments`)

- **Listado global:** `GET /payments` devuelve todos los pagos persistidos,
  sin requerir `bookingId`. Coexiste con el listado por reserva.
- Campos obligatorios: `amountCents` (> 0, entero) y `method`.
  `transactionReference` es opcional.
- No hay pasarela de pago: el pago se registra directamente como
  `completed`, con `paidAt` igual al momento del registro.
- Se guarda el usuario autenticado que lo procesó.
- El pago requiere un folio existente y abierto. Si todavía no existe folio,
  la respuesta es `404`; si el folio existe pero no está abierto, la respuesta
  es `400`.
- El pago resta del saldo del folio abierto.
- No se permiten sobrepagos: si `amountCents` supera el saldo actual del folio,
  la respuesta es `409`.
- No se registran pagos normales en reservas `cancelled`, `no_show` o
  `checked_out`.
- Se bloquea la reserva para que un pago y una apertura de folio simultáneos
  no descuadren el saldo.

## 11. Depósitos (`/bookings/{bookingId}/deposits`)

- **Listado global:** `GET /deposits` devuelve todos los depósitos persistidos,
  sin requerir `bookingId`. Coexiste con el listado por reserva.
- Campos obligatorios: `amountCents` (> 0, entero) y `method`. `notes` es
  opcional.
- Se crean como `held` (retenido), con `collectedAt` igual al momento del
  registro.
- **Los depósitos no afectan el saldo del folio al crearse:** son una garantía,
  no un pago.
- No se registran depósitos normales en reservas `cancelled`, `no_show` o
  `checked_out`.
- **Aplicación al folio (`POST /{depositId}/apply`):** solo depósitos `held`
  con folio abierto. Cambia el depósito a `applied`, agrega la nota
  `Applied to folio` y reduce el saldo por el monto del depósito. La operación
  es idempotente: repetirla sobre un depósito ya `applied` responde `200` sin
  mover nuevamente el saldo. Un depósito `refunded` no puede aplicarse.
- **Reembolso:** solo depósitos `held`. Si ya fue reembolsado, se responde
  `"Deposit is already refunded"`. El reembolso pasa el depósito a `refunded`,
  registra `refundedAt` y, si se envía un motivo, lo agrega a las notas como
  `Refund: <motivo>`.
- Se bloquea el depósito para evitar dos reembolsos o aplicaciones simultáneas.

---

## 12. Caja (`/cash-sessions`)

- **Sesiones por usuario:** cada usuario puede tener como maximo una sesion de
  caja abierta. Otros usuarios pueden tener sus propias sesiones abiertas en
  paralelo.
- **Apertura (`POST /open`):**
  - `openingBalanceCents` es obligatorio (>= 0, entero). `notes` es opcional.
  - Si el usuario autenticado ya tiene una sesion abierta -> `400`.
  - El usuario autenticado debe existir en la tabla `users`, porque queda
    como `openedByUser`; si no existe -> `401`.
  - Se crea con `status = open` y `currency = GTQ`.
  - Las aperturas simultaneas del mismo usuario se serializan con un bloqueo
    de PostgreSQL y un indice unico parcial, asi que nunca quedan dos
    sesiones abiertas para el mismo usuario.
- **Sesion actual (`GET /current`):** devuelve la sesion abierta del usuario
  autenticado con sus totales calculados en vivo. Si ese usuario no tiene
  caja abierta -> `404`.
- **Movimientos (`POST /{id}/movements`):**
  - Campos obligatorios: `type` (`income`/`expense`), `concept` (no vacio,
    max. 255) y `amountCents` (> 0, entero).
  - No se registran en una sesion cerrada (`400`).
  - **Un egreso no puede superar el efectivo disponible** (apertura +
    ingresos - egresos). La caja nunca queda en negativo.
  - Se guarda el usuario responsable y `occurredAt` es el momento del
    registro.
  - Los movimientos manuales no se vinculan a pagos, asi que `paymentId`
    queda en `null`.
- **Listado de movimientos (`GET /{id}/movements`):** ordenado
  cronologicamente. Si la sesion no existe -> `404`.
- **Cierre (`POST /{id}/close`):**
  - `countedBalanceCents` es obligatorio (>= 0, entero): lo contado
    fisicamente.
  - Solo se cierra una sesion abierta; cerrar dos veces -> `400`.
  - El backend calcula y guarda los totales; los que envie el cliente se
    ignoran:
    - `expectedBalanceCents = apertura + ingresos - egresos`
    - `differenceCents = contado - esperado` (negativo = faltante,
      positivo = sobrante)
  - Registra `closedByUser` y `closedAt`. Las notas del cierre se agregan a
    las de apertura.
- Cerrar y registrar movimientos bloquea la sesion, asi que un movimiento no
  puede entrar mientras la sesion se esta cerrando.
- **Integracion con pagos:** un pago con `method = cash` crea automaticamente
  un movimiento `income` en la caja abierta del usuario que procesa el pago.
  Si el usuario no tiene caja abierta, el pago se rechaza y no queda
  parcialmente registrado. Pagos con tarjeta, transferencia u online no crean
  movimientos de caja. `cash_movements.payment_id` es unico para impedir
  duplicidades.

---

## 13. Solicitudes operativas (`/service-requests`)

- Gestiona solicitudes generales de operación sobre `ServiceRequest` para
  crear o cambiar estado de `type = maintenance` u `other`. Las solicitudes
  `concierge` y `housekeeping` se rechazan en esas mutaciones y deben usar sus
  endpoints dedicados.
- **Listado (`GET`):** filtros opcionales `type`, `bookingId`, `roomId` y
  `status`. Orden: de la más antigua a la más reciente (`requestedAt`).
- **Detalle (`GET /{id}`):** si no existe → `404`.
- **Creación (`POST`):**
  - `roomId`, `type` y `description` (no vacía) son obligatorios.
  - `bookingId` es opcional. Si se envía, la reserva debe existir, estar
    `confirmed` o `checked_in`, y la habitación enviada debe ser la de la
    reserva.
  - Si no se envía `bookingId`, la solicitud queda asociada solo a la
    habitación; `guestId` queda vacío.
  - El backend fija `status = pending` y controla `requestedAt`, `createdAt`
    y `updatedAt`.
- **Flujo de estados (`POST /{id}/status`):**
  - `pending → accepted | rejected | cancelled`
  - `accepted → in_progress | cancelled`
  - `in_progress → completed | cancelled`
  - `completed`, `rejected` y `cancelled` son terminales.
  - `in_progress` registra `startedAt`; `completed` registra `completedAt`.
  - `responsibleUserId` opcional asigna responsable; si se omite, aceptar,
    iniciar o completar puede asignar al usuario autenticado cuando existe en
    `users`.
  - `notes` opcional se agrega a las notas existentes, recortado.

## 14. Conserjería (`/concierge/requests`)

- **Solo solicitudes de conserjería.** El módulo trabaja sobre `ServiceRequest`
  únicamente con `type = concierge`:
  - las solicitudes de otro tipo (`housekeeping`, `maintenance`, `other`) no
    aparecen en el listado;
  - consultar o cambiar el estado de una solicitud de otro tipo por su ID
    responde `404`, como si no existiera.
- **Listado (`GET`):** filtros opcionales `bookingId` y `status`. Un valor
  inválido en cualquiera de los dos → `400`. Orden: de la más antigua a la más
  reciente (`requestedAt`).
- **Creación (`POST`):**
  - `bookingId` y `description` (no vacía) son obligatorios. `notes` es
    opcional.
  - La reserva debe existir. Si no existe → `400`, porque es una referencia
    en el body.
  - Solo se crean solicitudes para reservas `confirmed` o `checked_in`; cualquier
    otro estado responde `400`.
  - El backend fija `type = concierge` y `status = pending`, y controla
    `requestedAt`, `createdAt` y `updatedAt`.
  - La habitación y el huésped se toman de la reserva. `roomId` queda vacío si
    la reserva no tiene habitación asignada.
  - Si el cliente envía `type`, `status`, `roomId`, `guestId` o `chargeId`, se
    ignoran.
- **Flujo de estados (`POST /{requestId}/status`):**
  - `pending → accepted | rejected | cancelled`
  - `accepted → in_progress | cancelled`
  - `in_progress → completed | cancelled`
  - `completed`, `rejected` y `cancelled` son **terminales**: cualquier cambio
    → `400`.
  - Cualquier otra transición, incluido repetir el mismo estado → `400`.
  - `rejected` solo puede producirse desde `pending`; si una solicitud aceptada
    o en progreso no se realizará, se usa `cancelled`.
  - `notes` es opcional en el cambio de estado (por ejemplo, el motivo del
    rechazo) y se agrega a las notas existentes.
  - `responsibleUserId` es opcional en el cambio de estado. Si se envía, debe
    existir en `users`; el backend lo asocia a la solicitud para mantener la
    trazabilidad del responsable.
  - Si no se envía `responsibleUserId` y la solicitud aún no tiene
    responsable, al pasar a `accepted`, `in_progress` o `completed` se asigna
    el usuario autenticado (si existe en `users`). Un responsable ya asignado
    no se reemplaza, y rechazar o cancelar no asigna a nadie. Las acciones del
    portal del huésped no asignan responsable.
  - La respuesta incluye `responsibleUserName` y `responsibleUserEmail`, para
    mostrar al responsable sin acceso a `/admin/users`, y `roomNumber` y
    `guestName`, porque el rol `concierge` no tiene `rooms.read`.
  - El cambio de estado bloquea la solicitud, así que dos cambios simultáneos
    no pueden saltarse el flujo.
- **Edición (`PUT /{requestId}`):** al menos un campo debe venir en el body.
  - `notes` se puede reemplazar mientras la solicitud está `pending`,
    `accepted` o `in_progress` (vacío las limpia).
  - `description` solo se puede cambiar en `pending`; después → `400`.
  - Una solicitud terminal (`completed`, `rejected`, `cancelled`) no se puede
    editar → `400`.
- **Sin cargos:** el módulo nunca crea cargos ni toca el folio; `chargeId`
  queda en `null`.

## 15. Inventario (`/inventory/items`)

- **Fuente oficial:** `InventoryItem.currentQuantity` es la fuente oficial de
  existencias. `Product.stockQuantity` no se actualiza desde inventario ni se
  usa para calcular stock disponible.
- **Solo existencias.** El modulo consulta articulos y registra entradas,
  salidas y ajustes sobre articulos que ya existen. No crea, edita ni elimina
  `InventoryItem`.
- **Listado (`GET`):** todos los filtros son opcionales y se combinan:
  - `active` (`true`/`false`);
  - `category`, sin distinguir mayusculas ni espacios al inicio o al final;
  - `lowStock=true` devuelve los articulos con `currentQuantity <=
    minimumQuantity`, y `lowStock=false` el resto.

  Un valor invalido en `active` o `lowStock` -> `400`. Orden: por nombre y
  luego por SKU.
- **`lowStock` en la respuesta:** cada articulo incluye `lowStock`,
  calculado en el backend con la misma regla (`currentQuantity <=
  minimumQuantity`).
- **Detalle y movimientos (`GET /{itemId}`, `GET /{itemId}/movements`):** los
  articulos inactivos siguen siendo consultables. Los movimientos se listan
  en orden cronologico. Si el articulo no existe -> `404`.
- **Registrar movimiento (`POST /{itemId}/movements`):**
  - `type` (`in`/`out`), `reason` y `quantity` (entero > 0) son obligatorios.
    `notes` es opcional.
  - Las cantidades con decimales, en texto o fuera del rango de un entero se
    rechazan (`400`) en lugar de truncarse.
  - El articulo debe existir (`404`) y estar **activo** (`400`).
  - **Combinaciones validas de tipo y razon:**
    - `in`: `purchase`, `restock`, `physical_count`
    - `out`: `consumption`, `sale`, `shrinkage`, `physical_count`
    - cualquier otra combinacion -> `400`.
    - `room_service_return` es un motivo interno de Room Service y siempre
      se rechaza (`400`) en este endpoint.
  - `physical_count` representa un ajuste trazable por conteo fisico: si el
    conteo real es mayor se registra como `in`, y si es menor como `out`.
  - `in` suma y `out` resta a `currentQuantity`.
  - **El stock nunca queda negativo:** una salida mayor que la existencia ->
    `400` (`Insufficient stock`). Se permite dejar el stock exactamente en 0.
  - Una entrada que desborde el maximo de un entero -> `400`.
  - El backend controla el articulo (por la URL), `occurredAt`, `createdAt` y
    `responsibleUser`, que se toma del JWT cuando el usuario existe en `users`
    y queda `null` si no.
- **Atomicidad y concurrencia:** el movimiento y la actualizacion de
  `currentQuantity` ocurren en la misma transaccion. El articulo se bloquea
  mientras se registra el movimiento, asi que dos salidas simultaneas no
  pueden vender de mas ni perder una resta.
- **Integracion con Room Service:** los pedidos descuentan y devuelven
  existencias con movimientos automaticos (ver seccion 17):
  - al aceptar: `out` / `sale`;
  - al cancelar un pedido ya aceptado: `in` / `room_service_return`.
  - Estos movimientos aplican las mismas reglas de stock y bloqueo del
    articulo e incluyen `roomServiceOrderId` en la respuesta de movimientos.
    Los movimientos manuales tienen `roomServiceOrderId = null`.
  - La BD garantiza que un pedido descuente y devuelva cada articulo como
    maximo una vez, y que `room_service_return` siempre tenga pedido.
- **Sin otras integraciones:** no hay integracion con Caja ni Folio.
- **Historial:** los movimientos nunca se borran ni se editan. Los errores se
  corrigen con movimientos compensatorios.

### Administración (`/admin/inventory/items`)
- Permite crear y editar metadatos de `InventoryItem`: `sku`, `name`,
  `description`, `category`, `unit`, `minimumQuantity`, producto vinculado y
  bandera `active`.
- `currentQuantity` no se edita por CRUD. Inicia en 0 y cambia solo mediante
  movimientos trazables o integraciones internas como Room Service.
- Desactivar un artículo evita movimientos manuales nuevos, pero no borra
  historial ni modifica stock.

## 16. Housekeeping (`/housekeeping/rooms`)

- **Listado (`GET`):** todas las habitaciones ordenadas por numero, con
  filtro opcional `housekeepingStatus` (`dirty`, `cleaning`, `clean`,
  `inspected`). Un valor invalido -> `400`.
- **Detalle (`GET /{roomId}`):** si la habitacion no existe -> `404`.
- **Flujo de limpieza de turnover:** cada accion exige un estado de origen
  exacto:
  - `POST /{roomId}/start`: `dirty -> cleaning`
  - `POST /{roomId}/complete`: `cleaning -> clean`
  - `POST /{roomId}/inspect`: `clean -> inspected`
  - Si la habitacion no esta en el estado de origen -> `400`.
- El flujo operativo normal es de avance (`dirty -> cleaning -> clean ->
  inspected`). El checkout es la operacion que vuelve a marcar automaticamente
  una habitacion como `dirty`.
- Cada transicion de turnover registra trazabilidad en la habitacion:
  `cleaningUser` y `cleaningStartedAt` para quien inicia, `cleaningCompletedByUser`
  y `cleaningCompletedAt` para quien completa, e `inspectorUser`/`inspectedAt`
  para quien inspecciona. Las personas pueden ser distintas.
- Solo cambia `housekeepingStatus`; el estado operativo (`status`:
  `available`, `occupied`, etc.) no se toca.
- Relacion con el check-in: una habitacion solo admite check-in si esta
  `clean` o `inspected` (seccion 7).
- Cada accion bloquea la habitacion, asi que dos acciones simultaneas no
  pueden saltarse el flujo.
- **Limpieza durante estancia:** se maneja como una solicitud independiente de
  Housekeeping sobre `ServiceRequest` con `type = housekeeping`.
  - `POST /{roomId}/stayover-cleanings` crea una tarea `pending` para una
    reserva `checked_in` que pertenezca a esa habitacion.
  - `GET /stayover-cleanings` lista las tareas stayover con filtros opcionales
    `bookingId` y `status` (`pending`, `accepted`, `in_progress`, `completed`,
    `rejected`, `cancelled`). Sin filtros devuelve todas, para que el rol
    housekeeping consulte su cola sin depender de `GET /bookings` (no tiene
    `bookings.read`). Orden: de la mas antigua a la mas reciente
    (`requestedAt`). Un `bookingId` inexistente -> `404`; un valor invalido en
    cualquiera de los dos filtros -> `400`. La respuesta solo trae datos de
    limpieza (habitacion, estado, descripcion y trazabilidad), sin datos del
    huesped.
  - `POST /stayover-cleanings/{requestId}/start`: `pending -> in_progress`.
  - `POST /stayover-cleanings/{requestId}/complete`: `in_progress -> completed`.
  - `responsibleUser` conserva al responsable inicial de la tarea.
  - `startedByUser`/`startedAt` registran quien inicia y
    `completedByUser`/`completedAt` quien completa, ademas de `createdAt` y
    `updatedAt`.
  - No cambia `Room.status` ni `Room.housekeepingStatus`: una habitacion
    `occupied` continua ocupada y no se libera por completar esta tarea.

## 17. Room Service (`/room-service`)

### Productos (`GET /room-service/products`)
- Solo lista productos **activos**, ordenados por nombre, con filtro
  opcional `category` (`minibar`, `shop`, `food_and_beverage`, `other`).

### Pedidos (`/room-service/orders`)
- **Listado (`GET`):** filtros opcionales `bookingId` y `status`. Orden: del
  más reciente al más antiguo.
- **Detalle (`GET /{orderId}`):** incluye las líneas del pedido. Si no
  existe → `404`.
- **Creación (`POST`):**
  - `bookingId` y al menos un ítem son obligatorios. `notes` es opcional.
  - Cada ítem requiere `productId` y `quantity`.
  - **Cantidades:** solo enteros positivos (`1`, `2`, `3`, ...). `0`,
    negativos, decimales (`1.5`, `2.0`), texto y valores fuera del rango de
    un entero → `400`. Los decimales no se truncan ni se redondean.
  - La reserva debe existir y estar **`checked_in`**. Si no existe →
    **`404`**; si existe en cualquier otro estado → `400`.
  - Los productos deben existir y estar activos. Si no → **`404`**.
  - El `404` de `bookingId`/`productId` es distinto de la convención del
    resto de la API (ver sección 0 y pendientes en la sección 20).
  - **Productos repetidos:** si el mismo `productId` aparece varias veces se
    consolida en una sola línea sumando las cantidades. Si la suma desborda
    un entero → `400`.
  - Se crea con `status = pending` y `currency = GTQ`. La habitación y el
    huésped se toman de la reserva, y el backend controla los timestamps.
  - La reserva se bloquea mientras se crea el pedido, así que un checkout
    simultáneo no deja crear pedidos sobre una reserva que ya salió.
  - **Precio congelado:** cada línea guarda el precio del producto al
    momento del pedido (`unitPriceCents`), así que un cambio de precio
    posterior no afecta pedidos ya creados.
  - **Totales calculados en backend:** `lineTotalCents = quantity ×
    unitPriceCents` y `totalCents` = suma de las líneas.
  - Crear el pedido (`pending`) **no** descuenta inventario ni genera cargos.
- **Flujo de estados (`POST /{orderId}/status`):**
  - Flujo principal: `pending → accepted → preparing → ready → on_the_way →
    delivered`.
  - `pending` también puede pasar a `rejected`.
  - Cancelación permitida solo desde `pending`, `accepted`, `preparing` y
    `ready`. Desde `on_the_way` el pedido ya no se puede cancelar: solo puede
    pasar a `delivered`.
  - `delivered`, `rejected` y `cancelled` son **terminales** (`400`).
  - Repetir el estado actual o hacer cualquier otra transición → `400`.
  - El body acepta `notes` opcional (máx. 1000 caracteres) junto con
    `status`, p. ej. el motivo de un rechazo o una cancelación. Si viene, se
    guarda recortado (vacío limpia las notas); si se omite, las notas
    actuales se conservan. Si la transición falla, las notas tampoco cambian.
  - El cambio de estado bloquea el pedido, así que dos cambios simultáneos
    sobre el mismo pedido se procesan uno detrás del otro.
  - El usuario del JWT queda como responsable de los movimientos de
    inventario y como creador del cargo, cuando existe en `users`.
  - Si una integración falla, la transición completa se revierte: el pedido
    conserva su estado anterior y no quedan descuentos, devoluciones ni
    cargos parciales.
- **Inventario (`InventoryItem.currentQuantity` es la fuente oficial):**
  - **Al pasar a `accepted`:**
    - Cada producto del pedido debe tener **exactamente un** artículo de
      inventario **activo** vinculado (`InventoryItem.product`). Si no tiene
      ninguno o tiene varios → `400`.
    - Se validan las existencias de **todas** las líneas antes de descontar.
      Si falta stock en cualquiera → `400` (`Insufficient stock for product
      ...`) y no se descuenta ninguna.
    - Si todo es válido, cada línea descuenta su cantidad con un movimiento
      `out` / `sale` ligado al pedido (`roomServiceOrderId`).
    - Los artículos se bloquean (siempre en el mismo orden), así que pedidos
      simultáneos no pueden vender de más.
  - **Al cancelar un pedido que ya descontó inventario:** se devuelve
    exactamente lo descontado con movimientos `in` / `room_service_return`.
    Esto ocurre aunque el artículo esté inactivo en ese momento.
  - Cancelar o rechazar un pedido `pending` no toca el inventario.
  - El pedido guarda internamente cuándo descontó (`inventory_deducted_at`)
    y cuándo devolvió (`inventory_restored_at`) inventario. La BD impide
    descontar o devolver dos veces el mismo artículo para un pedido.
  - `Product.stockQuantity` no se usa ni se modifica.
- **Cargo al folio (al pasar a `delivered`):**
  - Se crea **un** cargo en el folio de la reserva por el total real del
    pedido (precios congelados): `quantity = 1`, `unitPriceCents =
    totalCents`, `category = consumption`, `status = posted` y descripción
    `Room service order <orderId>`. El saldo del folio aumenta en ese monto.
  - El pedido guarda el cargo y lo expone como `chargeId` en la respuesta.
    `chargeId` es `null` hasta la entrega.
  - Se aplican las reglas del Folio: sin folio → `404`; folio no abierto →
    `400`.
  - Un pedido con `totalCents = 0` no puede entregarse → `400`, porque el
    folio no admite cargos de valor cero.
  - En cualquiera de esos errores el pedido permanece `on_the_way` y no se
    crea ningún cargo.
  - **Sin cargos duplicados:** un pedido entregado es terminal y el bloqueo
    del pedido serializa los reintentos. Además, la BD impide que un mismo
    cargo quede ligado a dos pedidos.
  - Crear o aceptar un pedido no genera cargos.
- **Observaciones (`PATCH /{orderId}/notes`):**
  - Body `{ "notes": "..." }` obligatorio (máx. 1000 caracteres). Reemplaza
    las notas sin cambiar el estado; texto vacío las limpia.
  - Un pedido terminal (`delivered`, `rejected`, `cancelled`) conserva sus
    notas, por ejemplo el motivo del rechazo → `400`. Pedido inexistente →
    `404`.
  - Requiere `room-service.write` (o administración), igual que el cambio de
    estado. Bloquea el pedido mientras actualiza.

### Catálogo administrativo (`/admin/room-service/products`)
- Permite listar, crear y editar productos de Room Service sin alterar pedidos
  históricos.
- El precio se guarda en centavos (`priceCents`), la moneda permanece `GTQ` y
  `active=false` oculta el producto del menú operativo/de huésped.

---

## 18. Portal huésped, amenidades y notificaciones

### Room Service huésped (`/guest/room-service/orders`)
- El huésped consulta solo pedidos de su estadía.
- Al crear un pedido, el backend inyecta el `bookingId` desde el JWT; el body
  no acepta una reserva arbitraria.
- El huésped puede cancelar usando las mismas reglas del flujo actual de Room
  Service. Un pedido ajeno responde `403`.
- **Menú (`GET /guest/room-service/products`):** mismo catálogo que
  `GET /room-service/products` (solo productos **activos**, ordenados por
  nombre, filtro opcional `category`), pero con identidad de huésped: el token
  de huésped no tiene `room-service.read`. Sin token → `401`; con token de
  personal → `403`.

### Solicitudes del huésped
- `/guest/housekeeping/requests` crea y consulta stayover cleanings reales
  (`ServiceRequest.type = housekeeping`) para la habitación asignada.
- `/guest/concierge/requests` reutiliza Conserjería (`type = concierge`) y
  filtra siempre por la reserva del JWT.
- Cancelar solicitudes ajenas responde `403`; cancelar estados no permitidos
  responde `400`.

### Amenidades (`/guest/amenities`, `/admin/amenities`)
- El huésped lista y consulta solo amenidades activas.
- Administración puede crear, editar, activar/desactivar y mantener horarios
  (`opensAt`, `closesAt`). Si ambos horarios se envían, apertura debe ser
  anterior a cierre.

### Notificaciones (`/guest/notifications`)
- Las notificaciones son persistentes y pertenecen a una reserva y huésped.
- Se generan para cambios relevantes de Room Service y Conserjería, y para
  cancelaciones de limpieza hechas desde el portal huésped.
- El huésped lista solo las propias, consulta contador de no leídas y marca
  como leída. El backend evita duplicados por `booking + resource + type`.
- `POST /guest/notifications/read-all` marca como leídas, en una sola
  transacción, todas las notificaciones no leídas de la reserva del JWT y
  devuelve el listado actualizado. Es idempotente y nunca toca notificaciones
  de otra reserva.

## 19. Administración, reportes y auditoría

### Usuarios, roles y permisos (`/admin/users`, `/admin/roles`, `/admin/permissions`)
- Administración lista, crea y edita usuarios de personal. Las respuestas no
  exponen `passwordHash`.
- Crear usuario requiere contraseña; se guarda cifrada con BCrypt.
- Editar permite datos básicos, estado y rol. No cambia credenciales desde el
  endpoint general.
- `/admin/roles` devuelve roles con sus permisos asociados.
- `/admin/permissions` devuelve el catálogo de permisos disponible ordenado por
  clave.
- Crear rol normaliza `code` a mayúsculas, convierte espacios/guiones en `_` y
  rechaza caracteres fuera de letras, números y `_`.
- Crear o reemplazar permisos de un rol exige que todas las claves existan en
  `permissions`; claves desconocidas responden `400`.
- Reemplazar permisos es atómico: se eliminan las asociaciones anteriores del
  rol y se guardan las nuevas en `role_permissions`.
- El rol `ADMIN` es crítico: no puede desactivarse ni reemplazar su conjunto de
  permisos desde la API administrativa.

### Promociones (`/admin/promotions`)
- Permite listar, crear y editar promociones con código, nombre, porcentaje,
  vigencia y bandera `active`.
- `discountPercent` debe estar entre 1 y 100, y `validTo` no puede ser anterior
  a `validFrom`.

### Comprobante, reportes y auditoría
- `/admin/bookings/{bookingId}/receipt` devuelve comprobante estructurado con
  estadía, cargos, pagos, depósitos, saldo final y moneda.
- `/admin/reports/operations` calcula métricas desde datos persistidos para un
  rango de fechas.
- `/admin/audit-logs` expone auditoría de solo lectura filtrada por rango de
  fecha/hora. No existe endpoint normal para editar o borrar auditoría.

## 20. Decisiones acordadas pendientes de implementación

Esta sección documenta decisiones ya tomadas por el equipo que **todavía no
deben leerse como comportamiento implementado**. Cuando una decisión contradice
el estado actual, se deja explícita la diferencia entre:

- **Actual:** comportamiento hoy implementado en el backend.
- **Acordado:** regla que debe implementarse posteriormente.

### Reservas y acompañantes
- **Correcciones posteriores al check-in.**
  - Actual: el check-in valida huésped titular + acompañantes contra adultos,
    niños y capacidad; luego la reserva queda protegida contra cambios
    estructurales por el endpoint general.
  - Acordado: si se requieren correcciones posteriores al check-in, deberán
    manejarse en el futuro mediante una operación administrativa controlada.

### Folio, pagos y depósitos
- **Reembolsos de pagos.**
  - Actual: solo se pueden reembolsar depósitos; no existe flujo de reembolso
    de pagos.
  - Acordado: los reembolsos de pagos deben implementarse como movimientos
    independientes. No se debe eliminar ni modificar el pago original y debe
    mantenerse trazabilidad completa.

### Conserjería
- **Cobro de servicios.**
  - Actual: el módulo no crea cargos ni toca el folio; `chargeId` queda en
    `null`.
  - Acordado: servicios de conserjería con costo podrán generar cargos al
    folio; servicios gratuitos no generan cargos. El cargo debe generarse
    cuando corresponda confirmar o completar realmente el servicio, no
    simplemente al crear la solicitud.
### Room Service
- **Referencias del body.**
  - Actual: si no existen el `bookingId` o un `productId` del body, o el
    producto está inactivo, responde `404`, distinto de la convención del
    resto de la API.
  - Acordado: corregirlo en un ticket posterior para alinearlo con la regla
    general de la sección 0 (`400` para referencias inválidas del body). Se
    mantuvo deliberadamente fuera de la Tanda 5.
