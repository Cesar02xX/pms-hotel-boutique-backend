# Reglas de negocio — PMS Hotel Boutique Aurora (backend)

Resumen de las reglas de negocio **implementadas actualmente** en el backend,
separadas por módulo. Cada regla sale del código (services, DTOs y queries),
no de la planificación. Si cambias una regla en el código, actualiza este
documento.

Todas las rutas cuelgan de `/api/v1`.

Al final, la sección **17. Decisiones acordadas pendientes de implementación**
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
  - `409`: conflicto con datos existentes (por ahora, huéspedes duplicados;
    ver sección 6).
  - `500`: error inesperado (mensaje genérico, sin detalles internos).
- **Referencias en el body:** cuando un ID enviado en el body no existe (por
  ejemplo `roomTypeId` al crear una habitación), la respuesta es `400`, no
  `404`. El `404` se reserva para el recurso de la ruta.
  **Excepción actual:** Room Service responde `404` cuando no existen el
  `bookingId` o el `productId` del body (ver sección 16).
- **Dinero:** siempre en **centavos enteros** (`Long`). La moneda es siempre
  `GTQ`. En pagos, depósitos y caja, los montos con decimales (`1.5`) se
  **rechazan** con `400` en lugar de truncarse. Lo mismo aplica a las
  cantidades de los movimientos de inventario.
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
  caja, conserjería, inventario, housekeeping y estados de Room Service)
  usan bloqueo pesimista de fila.

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

## 13. Conserjería (`/concierge/requests`)

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
  - No se crean solicitudes para reservas `checked_out`, `cancelled` o
    `no_show` (`400`).
  - El backend fija `type = concierge` y `status = pending`, y controla
    `requestedAt`, `createdAt` y `updatedAt`.
  - La habitación y el huésped se toman de la reserva. `roomId` queda vacío si
    la reserva no tiene habitación asignada.
  - Si el cliente envía `type`, `status`, `roomId`, `guestId` o `chargeId`, se
    ignoran.
- **Flujo de estados (`POST /{requestId}/status`):**
  - `pending → accepted | rejected`
  - `accepted → in_progress | rejected`
  - `in_progress → completed`
  - `completed` y `rejected` son **terminales**: cualquier cambio → `400`.
  - Cualquier otra transición, incluido repetir el mismo estado → `400`.
  - `accepted → rejected` se permite porque una solicitud aceptada puede
    resultar imposible de cumplir (por ejemplo, un restaurante sin
    disponibilidad).
  - `notes` es opcional en el cambio de estado (por ejemplo, el motivo del
    rechazo) y se agrega a las notas existentes.
  - El cambio de estado bloquea la solicitud, así que dos cambios simultáneos
    no pueden saltarse el flujo.
- **Sin cargos:** el módulo nunca crea cargos ni toca el folio; `chargeId`
  queda en `null`.

## 14. Inventario (`/inventory/items`)

- **Solo existencias.** El módulo consulta artículos y registra entradas y
  salidas sobre artículos que ya existen. No crea, edita ni elimina
  `InventoryItem`.
- **Listado (`GET`):** todos los filtros son opcionales y se combinan:
  - `active` (`true`/`false`);
  - `category`, sin distinguir mayúsculas ni espacios al inicio o al final;
  - `lowStock=true` devuelve los artículos con `currentQuantity <=
    minimumQuantity`, y `lowStock=false` el resto.

  Un valor inválido en `active` o `lowStock` → `400`. Orden: por nombre y
  luego por SKU.
- **`lowStock` en la respuesta:** cada artículo incluye `lowStock`,
  calculado en el backend con la misma regla (`currentQuantity <=
  minimumQuantity`).
- **Detalle y movimientos (`GET /{itemId}`, `GET /{itemId}/movements`):** los
  artículos inactivos siguen siendo consultables. Los movimientos se listan
  en orden cronológico. Si el artículo no existe → `404`.
- **Registrar movimiento (`POST /{itemId}/movements`):**
  - `type` (`in`/`out`), `reason` y `quantity` (entero > 0) son obligatorios.
    `notes` es opcional.
  - Las cantidades con decimales, en texto o fuera del rango de un entero se
    rechazan (`400`) en lugar de truncarse.
  - El artículo debe existir (`404`) y estar **activo** (`400`).
  - **Combinaciones válidas de tipo y razón:**
    - `in`: `purchase`, `restock`
    - `out`: `consumption`, `sale`, `shrinkage`
    - cualquier otra combinación → `400`.
  - `in` suma y `out` resta a `currentQuantity`.
  - **El stock nunca queda negativo:** una salida mayor que la existencia →
    `400` (`Insufficient stock`). Se permite dejar el stock exactamente en 0.
  - Una entrada que desborde el máximo de un entero → `400`.
  - El backend controla el artículo (por la URL), `occurredAt`, `createdAt` y
    `responsibleUser`, que se toma del JWT cuando el usuario existe en `users`
    y queda `null` si no.
- **Atomicidad y concurrencia:** el movimiento y la actualización de
  `currentQuantity` ocurren en la misma transacción. El artículo se bloquea
  mientras se registra el movimiento, así que dos salidas simultáneas no
  pueden vender de más ni perder una resta.
- **Sin integraciones:** no se modifica `Product.stockQuantity` aunque el
  artículo esté vinculado a un producto. No hay integración con Room
  Service, Caja ni Folio.
- **Historial:** los movimientos nunca se borran ni se editan.

## 15. Housekeeping (`/housekeeping/rooms`)

- **Listado (`GET`):** todas las habitaciones ordenadas por número, con
  filtro opcional `housekeepingStatus` (`dirty`, `cleaning`, `clean`,
  `inspected`). Un valor inválido → `400`.
- **Detalle (`GET /{roomId}`):** si la habitación no existe → `404`.
- **Flujo de limpieza:** cada acción exige un estado de origen exacto:
  - `POST /{roomId}/start`: `dirty → cleaning`
  - `POST /{roomId}/complete`: `cleaning → clean`
  - `POST /{roomId}/inspect`: `clean → inspected`
  - Si la habitación no está en el estado de origen → `400`.
- El flujo es solo de avance: no hay acción para volver a marcar una
  habitación como `dirty` (ver sección 17, Decisiones acordadas pendientes de
  implementación).
- Solo cambia `housekeepingStatus`; el estado operativo (`status`:
  `available`, `occupied`…) no se toca.
- Relación con el check-in: una habitación solo admite check-in si está
  `clean` o `inspected` (sección 7).
- Cada acción bloquea la habitación, así que dos acciones simultáneas no
  pueden saltarse el flujo.

## 16. Room Service (`/room-service`)

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
  - Cada ítem requiere `productId` y `quantity` (> 0).
  - La reserva y los productos deben existir, y los productos deben estar
    activos. Si no → **`404`** (distinto de la convención del resto de la
    API, ver sección 0).
  - Se crea con `status = pending` y `currency = GTQ`. La habitación y el
    huésped se toman de la reserva, y el backend controla los timestamps.
  - **Precio congelado:** cada línea guarda el precio del producto al
    momento del pedido (`unitPriceCents`), así que un cambio de precio
    posterior no afecta pedidos ya creados.
  - **Totales calculados en backend:** `lineTotalCents = quantity ×
    unitPriceCents` y `totalCents` = suma de las líneas.
- **Flujo de estados (`POST /{orderId}/status`):**
  - `pending → accepted | rejected | cancelled`
  - `accepted → preparing | cancelled`
  - `preparing → ready | cancelled`
  - `ready → on_the_way | cancelled`
  - `on_the_way → delivered | cancelled`
  - `delivered`, `rejected` y `cancelled` son **terminales** (`400`).
  - Repetir el estado actual o hacer cualquier otra transición → `400`.
  - El cambio de estado bloquea el pedido.
- **Sin integraciones:** crear o entregar un pedido **no** genera cargos en
  el folio y **no** descuenta `Product.stockQuantity` ni el inventario.

---

## 17. Decisiones acordadas pendientes de implementación

Esta sección documenta decisiones ya tomadas por el equipo que **todavía no
deben leerse como comportamiento implementado**. Cuando una decisión contradice
el estado actual, se deja explícita la diferencia entre:

- **Actual:** comportamiento hoy implementado en el backend.
- **Acordado:** regla que debe implementarse posteriormente.

### General / seguridad
- **Roles y permisos.**
  - Actual: el JWT incluye rol y permisos, pero ningún endpoint los valida;
    cualquier usuario autenticado puede operar todos los módulos.
  - Acordado: implementar autorización real por roles/permisos en endpoints.
    Ocultar funcionalidades en frontend no es suficiente. Un usuario
    autenticado solo podrá ejecutar operaciones permitidas para su rol o sus
    permisos.
- **Códigos HTTP de negocio.**
  - Actual: muchos conflictos de estado o duplicados responden `400`.
  - Acordado: usar `400 Bad Request` para datos o validaciones inválidas,
    `401 Unauthorized` para usuarios no autenticados, `403 Forbidden` para
    usuarios autenticados sin permiso, `404 Not Found` para recursos
    inexistentes y `409 Conflict` para operaciones válidas pero incompatibles
    con el estado actual del recurso.

### Catálogo (habitaciones, tipos, tarifas)
- **Cambios de limpieza desde habitaciones.**
  - Actual: `PUT /rooms/{id}` permite cambiar `housekeepingStatus` libremente,
    incluso saltándose el flujo de Housekeeping.
  - Acordado: `housekeepingStatus` no debe poder modificarse libremente desde
    el CRUD normal de habitaciones. Los cambios de limpieza deben pasar por
    Housekeeping.
- **Tarifas superpuestas.**
  - Actual: se pueden crear varias tarifas del mismo tipo de habitación con
    vigencias que se cruzan.
  - Acordado: no permitir tarifas superpuestas cuando correspondan al mismo
    contexto aplicable.
- **Reducir capacidad de un tipo.**
  - Actual: se puede bajar `capacity` aunque haya reservas activas o futuras
    que ya superan la nueva capacidad.
  - Acordado: no permitir reducir la capacidad si deja reservas activas o
    futuras existentes por encima de la nueva capacidad.

### Reservas, check-in y checkout
- **Check-in.**
  - Actual: el check-in ya es una operación específica, usa la fecha del hotel
    en `America/Guatemala` y solo permite check-in dentro de la estadía.
  - Acordado: mantener el check-in como operación específica con sus
    validaciones y mantener la fecha del hotel usando `America/Guatemala`. Por
    ahora no implementar tolerancia especial de early/late check-in.
- **Checkout.**
  - Actual: no está implementado.
  - Acordado: la reserva debe estar `checked_in`, debe existir folio abierto y
    el saldo debe ser exactamente `Q0.00`. Si existe saldo positivo o negativo,
    bloquear checkout. Al completar checkout: cerrar folio, cambiar reserva a
    `checked_out`, liberar operativamente la habitación y cambiar
    `housekeepingStatus` a `dirty`. Una habitación `dirty` no puede recibir un
    nuevo check-in hasta completar el proceso requerido de Housekeeping.

### Acompañantes
- **Cambios por estado de reserva.**
  - Actual: se pueden agregar, editar o borrar acompañantes de una reserva ya
    `checked_in`, y también de una reserva cancelada.
  - Acordado: permitir administrar acompañantes antes del check-in mientras la
    reserva siga en un estado válido para preparación. Después del check-in,
    bloquear altas, modificaciones y eliminaciones normales. También bloquear
    cambios cuando la reserva esté `cancelled`, `no_show` o `checked_out`.
- **Validación en check-in.**
  - Actual: el check-in valida huésped titular + acompañantes contra adultos,
    niños y capacidad.
  - Acordado: mantener esa validación. Correcciones posteriores al check-in
    deberán manejarse en el futuro mediante una operación administrativa
    controlada.

### Folio, pagos y depósitos
- **Sobrepagos.**
  - Actual: un pago puede superar el saldo pendiente y dejar saldo negativo.
  - Acordado: no permitir sobrepagos normales. Un pago no puede superar el
    saldo pendiente del folio. El objetivo para checkout es saldo exactamente
    `Q0.00`.
- **Pagos y depósitos en reservas cerradas.**
  - Actual: se pueden registrar pagos o depósitos en reservas `cancelled`,
    `no_show` o `checked_out` si no tienen folio.
  - Acordado: no permitir pagos o depósitos normales sobre reservas cerradas:
    `cancelled`, `no_show` o `checked_out`.
- **Reembolsos.**
  - Actual: solo se pueden reembolsar depósitos; no existe flujo de reembolso
    de pagos.
  - Acordado: los reembolsos deben implementarse como movimientos
    independientes. No se debe eliminar ni modificar el pago original y debe
    mantenerse trazabilidad completa.
- **Depósitos aplicados.**
  - Actual: existe el estado `applied`, pero ningún flujo lo usa.
  - Acordado: dar uso al estado `applied` e implementar posteriormente una
    operación específica para aplicar un depósito al folio. Al aplicarlo debe
    reducir el saldo correspondiente. Un depósito aplicado no puede aplicarse
    nuevamente.
- **Cargos de valor cero.**
  - Actual: se permiten cargos con `unitPriceCents = 0`.
  - Acordado: los cargos financieros normales deben ser mayores que `Q0.00`;
    no crear cargos de valor cero.
- **Cierre del folio.**
  - Actual: existe el estado `closed` pero no hay endpoint para cerrar el
    folio.
  - Acordado: el cierre del folio debe realizarse como parte del checkout.

### Caja
- **Sesiones por usuario.**
  - Actual: hay una sola caja abierta en todo el hotel y puede cerrarla un
    usuario distinto del que la abrió.
  - Acordado: cambiar a una sesión de caja por usuario/recepcionista. Cada
    usuario autorizado podrá tener su propia sesión abierta y debe mantenerse
    trazabilidad del usuario responsable.
- **Caja actual inexistente.**
  - Actual: `GET /cash-sessions/current` responde `404` si no hay caja abierta.
  - Acordado: si se consulta la caja actual y el usuario no tiene una abierta,
    mantener `404 Not Found`.
- **Egresos mayores que efectivo disponible.**
  - Actual: se rechazan para que la caja nunca quede en negativo.
  - Acordado: mantener la regla; no permitir egresos superiores al efectivo
    disponible.
- **Integración con pagos.**
  - Actual: los pagos no generan movimientos automáticos de caja.
  - Acordado: los pagos en efectivo deben generar automáticamente un movimiento
    de entrada en la caja del usuario que recibió el pago. Pagos con tarjeta u
    otros medios electrónicos no aumentan el efectivo físico de la caja.
- **Reapertura y correcciones.**
  - Actual: una caja cerrada no puede reabrirse y los movimientos históricos no
    se editan ni anulan.
  - Acordado: una caja cerrada no puede reabrirse. Correcciones posteriores
    deben realizarse mediante ajustes o movimientos trazables.

### Conserjería
- **Reservas permitidas.**
  - Actual: se permiten solicitudes para reservas `pending`, `confirmed` o
    `checked_in`; no se permiten para `cancelled`, `no_show` o `checked_out`.
  - Acordado: permitir nuevas solicitudes solo para reservas `confirmed` o
    `checked_in`. No permitir nuevas solicitudes para `cancelled`, `no_show` o
    `checked_out`.
- **Flujo de estados.**
  - Actual: `pending → accepted | rejected`, `accepted → in_progress |
    rejected`, `in_progress → completed`; `completed` y `rejected` son
    terminales. No existe `cancelled`.
  - Acordado: flujo principal `pending → accepted → in_progress → completed`,
    además `pending → rejected`. Agregar `cancelled` para solicitudes que
    posteriormente no se realizarán. `rejected` solo debe producirse desde
    `pending`; una solicitud ya aceptada no debe pasar a `rejected`. Si no se
    realizará, utilizar `cancelled`.
- **Asignación de empleados.**
  - Actual: no se registra empleado responsable.
  - Acordado: permitir asignar un empleado responsable.
- **Cobro de servicios.**
  - Actual: el módulo no crea cargos ni toca el folio; `chargeId` queda en
    `null`.
  - Acordado: servicios de conserjería con costo podrán generar cargos al
    folio; servicios gratuitos no generan cargos. El cargo debe generarse
    cuando corresponda confirmar o completar realmente el servicio, no
    simplemente al crear la solicitud.
- **Edición de solicitud.**
  - Actual: no se puede modificar la descripción después de creada.
  - Acordado: permitir edición normal de la solicitud solamente mientras esté
    `pending`.

### Inventario
- **Fuente oficial de existencias.**
  - Actual: `InventoryItem.currentQuantity` y `Product.stockQuantity` son
    independientes; Room Service no descuenta ninguno de los dos.
  - Acordado: `InventoryItem.currentQuantity` será la fuente oficial de
    existencias. Evitar mantener dos cantidades independientes entre `Product`
    e `InventoryItem`.
- **Stock bajo.**
  - Actual: `lowStock` ya se calcula como `currentQuantity <=
    minimumQuantity`; `lowStock=false` devuelve los artículos por encima del
    mínimo.
  - Acordado: mantener stock bajo como `currentQuantity <= minimumQuantity`.
    Si la existencia es exactamente igual al mínimo, ya se considera stock
    bajo.
- **Conteo físico y ajustes.**
  - Actual: no existe razón específica de ajuste positivo por conteo físico; un
    faltante se registra como `shrinkage`.
  - Acordado: implementar movimientos de ajuste de inventario. No modificar
    directamente `currentQuantity` sin trazabilidad. Los ajustes deben
    registrar usuario, fecha y motivo.
- **Movimientos históricos.**
  - Actual: los movimientos nunca se borran ni se editan.
  - Acordado: no editar ni eliminar movimientos históricos. Los errores deben
    corregirse mediante movimientos compensatorios.
- **CRUD de artículos.**
  - Actual: no existe; los artículos solo se pueden crear por SQL.
  - Acordado: queda fuera de estas decisiones y requerirá ticket específico si
    se necesita administrar artículos desde la API.

### Housekeeping
- **Limpieza posterior al checkout.**
  - Actual: el flujo existente es `dirty → cleaning → clean → inspected`, pero
    el checkout no está implementado y solo `PUT /rooms/{id}` puede volver una
    habitación a `dirty`.
  - Acordado: diferenciar limpieza posterior al checkout con el flujo
    `checkout → habitación available + dirty → cleaning → clean → inspected`.
    El checkout debe marcar automáticamente la habitación como `dirty`. Una
    habitación `dirty` no puede recibir otro check-in. No permitir saltarse
    estados mediante el CRUD normal.
- **Limpieza durante la estancia.**
  - Actual: Housekeeping opera sobre el `housekeepingStatus` de la habitación;
    no existe una tarea independiente de limpieza durante estancia.
  - Acordado: una habitación `occupied` también puede recibir limpieza si el
    huésped la solicita. Esta limpieza debe manejarse como tarea/solicitud de
    Housekeeping independiente, por ejemplo `pending → in_progress →
    completed`. La habitación continúa `occupied`, no se libera y no debe
    confundirse con la limpieza obligatoria posterior al checkout.
- **Trazabilidad.**
  - Actual: no se registra quién limpió o inspeccionó.
  - Acordado: registrar empleado responsable, inicio y finalización, y quién
    realiza la inspección cuando corresponda. La persona que limpia y la
    persona que inspecciona pueden ser diferentes.

### Room Service
- **Estado de reserva para crear pedidos.**
  - Actual: se pueden crear pedidos para reservas `pending`, `cancelled`,
    `no_show` o `checked_out`.
  - Acordado: los pedidos asociados a una habitación/reserva solo pueden
    crearse cuando la reserva esté `checked_in`.
- **Inventario.**
  - Actual: no se valida disponibilidad ni se descuenta stock.
  - Acordado: `pending` todavía no descuenta inventario. Al pasar a
    `accepted`, se debe validar existencia y descontar inventario. Si el pedido
    se cancela después de haber afectado inventario, devolver las existencias.
    La operación debe ser transaccional y segura ante concurrencia.
- **Cargo al folio.**
  - Actual: crear, aceptar o entregar un pedido no genera cargos en el folio;
    `charge_id` no se usa.
  - Acordado: el cargo se genera cuando el pedido llega a `delivered`. No
    generar el cargo simplemente al crear o aceptar el pedido. Guardar y usar
    `charge_id` para impedir cargos duplicados.
- **Cantidades.**
  - Actual: `quantity` puede truncar decimales como `1.5` a `1`.
  - Acordado: solo permitir números enteros positivos (`1`, `2`, `3`, ...).
    `0`, negativos y decimales son inválidos. Un decimal no debe redondearse
    ni truncarse; por ejemplo, `1.5` debe rechazarse con `400 Bad Request`.
- **Cancelación.**
  - Actual: se puede cancelar hasta `on_the_way`.
  - Acordado: permitir cancelación hasta `ready`. Flujo principal:
    `pending → accepted → preparing → ready → on_the_way → delivered`.
    Cancelación permitida desde `pending`, `accepted`, `preparing` y `ready`.
    Una vez `on_the_way`, el pedido ya no puede cancelarse mediante el flujo
    normal.
- **Productos duplicados.**
  - Actual: el mismo producto puede aparecer en varias líneas independientes.
  - Acordado: no mantener varias líneas independientes para el mismo producto
    dentro de un pedido; consolidar productos repetidos en una sola línea
    sumando sus cantidades.
- **Referencias del body.**
  - Actual: si no existen el `bookingId` o un `productId` del body, responde
    `404`, distinto de la convención del resto de la API.
  - Acordado: la convención general de códigos queda sujeta a la regla de
    códigos HTTP definida en General / seguridad.
