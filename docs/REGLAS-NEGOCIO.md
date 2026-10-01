# Reglas de negocio â€” PMS Hotel Boutique Aurora (backend)

Resumen de las reglas de negocio **implementadas actualmente** en el backend,
separadas por mÃ³dulo. Cada regla sale del cÃ³digo (services, DTOs y queries),
no de la planificaciÃ³n. Si cambias una regla en el cÃ³digo, actualiza este
documento.

Todas las rutas cuelgan de `/api/v1`.

Al final, la secciÃ³n **17. Decisiones acordadas pendientes de implementaciÃ³n**
lista las reglas ya decididas por el equipo que todavÃ­a no deben asumirse como
implementadas en Java.

---

## 0. Reglas generales (aplican a toda la API)

- **Seguridad:** todos los endpoints requieren JWT (`Authorization: Bearer <token>`),
  excepto `/health`, `/auth/login`, `/auth/refresh`, `/auth/logout` y Swagger.
  Sin token o con token invÃ¡lido â†’ `401`.
- **Formato de error Ãºnico:** todos los errores usan `ApiErrorResponse` a travÃ©s de
  `GlobalExceptionHandler`.
- **CÃ³digos HTTP:**
  - `400`: validaciÃ³n de DTO fallida (incluye `errors` por campo), regla de
    negocio incumplida, JSON mal formado, enum/fecha/UUID invÃ¡lido en el body
    o UUID invÃ¡lido en la ruta.
  - `404`: el recurso principal de la ruta no existe.
  - `401`: sin autenticaciÃ³n.
  - `409`: conflicto con datos existentes o con el estado financiero de la
    reserva, por ejemplo huÃ©spedes duplicados, sobrepagos o checkout con saldo
    distinto de cero.
  - `500`: error inesperado (mensaje genÃ©rico, sin detalles internos).
- **Referencias en el body:** cuando un ID enviado en el body no existe (por
  ejemplo `roomTypeId` al crear una habitaciÃ³n), la respuesta es `400`, no
  `404`. El `404` se reserva para el recurso de la ruta.
  **ExcepciÃ³n actual:** Room Service responde `404` cuando no existen el
  `bookingId` o el `productId` del body (ver secciÃ³n 16).
- **Dinero:** siempre en **centavos enteros** (`Long`). La moneda es siempre
  `GTQ`. En pagos, depÃ³sitos y caja, los montos con decimales (`1.5`) se
  **rechazan** con `400` en lugar de truncarse. Lo mismo aplica a las
  cantidades de los movimientos de inventario.
- **Campos controlados por el servidor:** IDs, estados iniciales, moneda,
  timestamps (`createdAt`, `updatedAt`, `paidAt`, `openedAt`, etc.) y usuario
  responsable los asigna el backend. Si el cliente los envÃ­a en el body, se
  **ignoran**.
- **Textos:** los nombres, cÃ³digos y conceptos se guardan sin espacios al inicio
  ni al final (`trim`).
- **Entidades JPA:** nunca se exponen directamente; siempre se usan DTOs de
  request/response.
- **Transacciones:** las operaciones que modifican datos son transaccionales.
  Las que pueden sufrir concurrencia (check-in, checkout, folio, pagos,
  depÃ³sitos, reembolsos, caja, conserjerÃ­a, inventario, housekeeping y estados
  de Room Service) usan bloqueo pesimista de fila.

---

## 1. AutenticaciÃ³n (`/auth`)

- **Login:** email (formato vÃ¡lido) y contraseÃ±a son obligatorios. Ante
  cualquier fallo (usuario inexistente, contraseÃ±a incorrecta, usuario
  deshabilitado) se responde siempre `"Invalid email or password"`, para no
  revelar quÃ© dato fallÃ³.
- **Usuario inactivo:** un usuario con `status = inactive` queda deshabilitado
  y no puede autenticarse.
- **Respuesta del login:** `accessToken` (JWT), `refreshToken`, tipo `Bearer` y
  la expiraciÃ³n en segundos.
- **Permisos:** el token incluye el rol como `ROLE_<CODIGO_ROL>` y las
  *keys* de los permisos asociados al rol.
- **Refresh tokens:**
  - Son valores aleatorios de 64 bytes. En la BD solo se guarda su **hash
    SHA-256**, nunca el valor original.
  - **RotaciÃ³n:** cada `/auth/refresh` revoca el token usado y emite uno nuevo.
    Un token no se puede reutilizar.
  - Un token es invÃ¡lido si estÃ¡ revocado, expirado, sin usuario o si el
    usuario no estÃ¡ `active`.
  - `/auth/logout` revoca el refresh token enviado.
  - Expiraciones configurables: access token de 30 min y refresh token de
    7 dÃ­as por defecto.

---

## 2. Tipos de habitaciÃ³n (`/room-types`)

- Campos obligatorios: `code`, `name` y `capacity` (mayor que 0).
- **`code` Ãºnico:** no puede repetirse al crear ni al actualizar.
- **CaracterÃ­sticas (`roomFeatureIds`):**
  - Todas deben existir; si falta alguna â†’ `400` con la lista de IDs faltantes.
  - Los IDs repetidos se ignoran (no se duplican asociaciones).
  - Al actualizar, si se envÃ­a la lista, **reemplaza** las asociaciones: quita
    las que ya no estÃ¡n y agrega las nuevas. Si no se envÃ­a, no se tocan.
- La actualizaciÃ³n es parcial: solo cambian los campos enviados, y los
  campos de texto enviados no pueden estar en blanco.
- **ReducciÃ³n de capacidad:** no se permite bajar `capacity` si existen
  reservas activas o futuras (`pending`, `confirmed`, `checked_in`) cuyo
  `adults + children` supere la nueva capacidad. El conflicto responde `409`.

## 3. CaracterÃ­sticas de habitaciÃ³n (`/room-features`)

- CatÃ¡logo de solo lectura: se listan ordenadas por nombre.

## 4. Habitaciones (`/rooms`)

- Campos obligatorios: `roomNumber` y `roomTypeId`. El tipo debe existir.
- **`roomNumber` Ãºnico** al crear y al actualizar.
- Estados por defecto al crear: `status = available` y
  `housekeepingStatus = dirty`.
- La actualizaciÃ³n es parcial; si se envÃ­a `roomNumber`, no puede estar en
  blanco.
- `housekeepingStatus` no puede modificarse desde `PUT /rooms/{id}`. Los
  cambios de limpieza deben pasar por Housekeeping; intentarlo desde el CRUD
  normal responde `409`.

## 5. Tarifas (`/rates`)

- Campos obligatorios: `roomTypeId` (debe existir), `name`, `validFrom`,
  `priceCents` (â‰¥ 0) y `minimumNights` (> 0).
- `currency` solo acepta `GTQ`.
- **Vigencia:** `validTo` (opcional) debe ser igual o posterior a `validFrom`.
  En una actualizaciÃ³n parcial se valida contra los valores resultantes.
- **Sin superposiciÃ³n:** no se permiten tarifas del mismo tipo de habitaciÃ³n
  con vigencias que se crucen. Una `validTo` vacÃ­a se trata como vigencia
  abierta. El conflicto responde `409`.

---

## 6. HuÃ©spedes (`/guests`)

- Campos obligatorios: `firstName` y `lastName`. Si se envÃ­a `email`, debe
  tener formato vÃ¡lido.
- `createdAt` nunca cambia al actualizar.
- Listado ordenado por apellido y nombre.
- **Unicidad** (al crear y al actualizar; en la actualizaciÃ³n no se compara
  al huÃ©sped consigo mismo). Un duplicado responde `409`:
  - `email` Ãºnico cuando se informa. La comparaciÃ³n no distingue mayÃºsculas
    de minÃºsculas.
  - La combinaciÃ³n `documentType + documentNumber` es Ãºnica cuando ambos se
    informan. El mismo nÃºmero con otro tipo de documento sÃ­ se permite.
  - Los opcionales vacÃ­os o solo con espacios se guardan como `null` y pueden
    repetirse sin generar duplicados.
  - La base de datos lo refuerza con Ã­ndices Ãºnicos parciales
    (`ux_guests_email`, `ux_guests_document`).

## 7. Reservas (`/bookings`)

### CreaciÃ³n y ediciÃ³n
- Campos obligatorios: `guestId`, `roomTypeId`, `checkIn`, `checkOut`,
  `adults` (â‰¥ 1) y `children` (â‰¥ 0). `roomId` y `rateId` son opcionales.
- Todas las referencias (huÃ©sped, tipo, habitaciÃ³n, tarifa) deben existir.
- **Fechas:** `checkIn` debe ser anterior a `checkOut`.
- **Capacidad:** `adults + children` no puede superar la capacidad del tipo de
  habitaciÃ³n.
- **Coherencia:** la habitaciÃ³n y la tarifa, si se envÃ­an, deben pertenecer al
  mismo tipo de habitaciÃ³n de la reserva.
- **Tarifas:** si se envÃ­a tarifa, debe estar activa, pertenecer al tipo de
  habitaciÃ³n de la reserva, cubrir las noches de la estadÃ­a y cumplir
  `minimumNights`.
- **Habitaciones operables:** no se puede asignar a una reserva una habitaciÃ³n
  con estado operativo `maintenance` u `out_of_service`.
- **Disponibilidad:** una habitaciÃ³n no puede tener dos reservas activas
  (`pending`, `confirmed`, `checked_in`) con fechas que se crucen. El dÃ­a de
  salida de una reserva puede ser el de entrada de la siguiente.
- **Total:** `totalAmountCents = precio de la tarifa Ã— noches`. Sin tarifa, el
  total es 0. Se recalcula en cada actualizaciÃ³n.
- Valores asignados al crear: `status = pending`, `currency = GTQ` y
  dos cÃ³digos Ãºnicos aleatorios, `confirmationCode` (`BKG-XXXXXXXX`) y
  `guestLinkCode` (`GL-XXXXXXXX`). Los cÃ³digos usan caracteres sin ambigÃ¼edad
  (sin 0/O ni 1/I).
- La actualizaciÃ³n es parcial y todas las validaciones se aplican de nuevo
  sobre el resultado final.
- `PUT /bookings/{id}` no permite cambiar `status`; los cambios importantes de
  estado se hacen mediante operaciones especÃ­ficas.
- Una reserva `checked_in` no admite modificaciones estructurales por el
  `PUT` general (huÃ©sped, tipo, habitaciÃ³n, tarifa, fechas u ocupantes). Esas
  operaciones quedan reservadas para flujos especÃ­ficos futuros.

### Check-in (`POST /bookings/{id}/check-in`)
- Solo reservas en estado `confirmed`. Si ya estÃ¡ `checked_in`, se responde
  `"Booking is already checked in"`.
- **Fecha:** solo dentro de la estadÃ­a, es decir `checkIn â‰¤ hoy < checkOut`.
  "Hoy" se calcula en la zona horaria del hotel (`America/Guatemala`).
- La reserva debe tener una habitaciÃ³n asignada que:
  - pertenezca al tipo de la reserva;
  - estÃ© `available`;
  - estÃ© limpia (`clean` o `inspected`);
  - no tenga otra reserva activa que se cruce.
- **ComposiciÃ³n:** huÃ©sped principal + acompaÃ±antes debe coincidir
  **exactamente** con los `adults` y `children` declarados, y no superar la
  capacidad.
- Resultado: la reserva pasa a `checked_in` y la habitaciÃ³n a `occupied`.
- Se bloquea la reserva para evitar dos check-ins simultÃ¡neos.

### Checkout (`POST /bookings/{id}/check-out`)
- Solo reservas en estado `checked_in`.
- Debe existir un folio abierto para la reserva. Si no existe â†’ `404`; si el
  folio no estÃ¡ abierto â†’ `400`.
- El saldo del folio debe ser exactamente `0`. Cualquier saldo positivo o
  negativo bloquea el checkout con `409`.
- Resultado: la reserva pasa a `checked_out`, el folio queda `closed`, la
  habitaciÃ³n queda operativamente `available` y su `housekeepingStatus` pasa a
  `dirty`.
- El checkout no limpia la habitaciÃ³n; solo inicia el flujo posterior de
  Housekeeping.

## 8. AcompaÃ±antes (`/bookings/{bookingId}/companions`)

- Campos obligatorios: `firstName`, `lastName` y `guestType` (`adult`/`child`).
- La administraciÃ³n normal de acompaÃ±antes (crear, editar o eliminar) solo se
  permite antes del check-in, mientras la reserva estÃ© en estado `pending` o
  `confirmed`. Si la reserva estÃ¡ `checked_in`, `checked_out`, `cancelled` o
  `no_show`, esas operaciones responden `409`.
- **El huÃ©sped principal no puede registrarse como acompaÃ±ante.** Se detecta
  por el mismo nÃºmero de documento o por el mismo nombre y apellido
  (ignorando mayÃºsculas y espacios).
- **ComposiciÃ³n:** el huÃ©sped principal cuenta como 1 adulto. Con los
  acompaÃ±antes:
  - los adultos no pueden superar los `adults` declarados en la reserva;
  - los niÃ±os no pueden superar los `children` declarados;
  - el total no puede superar la capacidad del tipo de habitaciÃ³n.
- El acompaÃ±ante debe pertenecer a la reserva de la ruta; si no â†’ `404`.

---

## 9. Folio / cuenta del huÃ©sped (`/bookings/{bookingId}/folio`, `/charges`)

- **Apertura idempotente:** `POST /folio/open` crea la cuenta (`201`) o
  devuelve la existente sin cambios (`200`). Hay una sola cuenta por reserva,
  asociada al huÃ©sped principal.
- No se puede abrir folio si la reserva estÃ¡ `checked_out`, `cancelled` o
  `no_show`.
- **Saldo inicial:** al abrir, el saldo se calcula con los cargos no anulados
  menos los pagos completados que ya tuviera la reserva.
- **Saldo:** `balance = cargos no anulados âˆ’ pagos completados`. Se mantiene
  actualizado en cada movimiento.
- **Cargos:**
  - Requieren un folio **abierto** (`404` si no existe, `400` si no estÃ¡
    abierto).
  - Campos obligatorios: `description`, `quantity` (> 0), `unitPriceCents`
    (â‰¥ 0) y `category`. `productId` es opcional y, si se envÃ­a, debe existir.
  - `amountCents = quantity Ã— unitPriceCents`, calculado en el backend. Si el
    resultado desborda â†’ `400`.
  - El monto total debe ser mayor que `0`; los cargos financieros normales de
    valor cero se rechazan con `400`.
  - Se crean con `status = posted` y suman al saldo.
- **Anular cargo:** requiere `reason`, no se puede anular dos veces y resta
  el monto del saldo.
- **Cierre:** el folio se cierra exclusivamente como parte del checkout de la
  reserva.

## 10. Pagos (`/bookings/{bookingId}/payments`)

- Campos obligatorios: `amountCents` (> 0, entero) y `method`.
  `transactionReference` es opcional.
- No hay pasarela de pago: el pago se registra directamente como
  `completed`, con `paidAt` igual al momento del registro.
- Se guarda el usuario autenticado que lo procesÃ³.
- El pago requiere un folio existente y abierto. Si todavÃ­a no existe folio,
  la respuesta es `404`; si el folio existe pero no estÃ¡ abierto, la respuesta
  es `400`.
- El pago resta del saldo del folio abierto.
- No se permiten sobrepagos: si `amountCents` supera el saldo actual del folio,
  la respuesta es `409`.
- No se registran pagos normales en reservas `cancelled`, `no_show` o
  `checked_out`.
- Se bloquea la reserva para que un pago y una apertura de folio simultÃ¡neos
  no descuadren el saldo.

## 11. DepÃ³sitos (`/bookings/{bookingId}/deposits`)

- Campos obligatorios: `amountCents` (> 0, entero) y `method`. `notes` es
  opcional.
- Se crean como `held` (retenido), con `collectedAt` igual al momento del
  registro.
- **Los depÃ³sitos no afectan el saldo del folio al crearse:** son una garantÃ­a,
  no un pago.
- No se registran depÃ³sitos normales en reservas `cancelled`, `no_show` o
  `checked_out`.
- **AplicaciÃ³n al folio (`POST /{depositId}/apply`):** solo depÃ³sitos `held`
  con folio abierto. Cambia el depÃ³sito a `applied`, agrega la nota
  `Applied to folio` y reduce el saldo por el monto del depÃ³sito. La operaciÃ³n
  es idempotente: repetirla sobre un depÃ³sito ya `applied` responde `200` sin
  mover nuevamente el saldo. Un depÃ³sito `refunded` no puede aplicarse.
- **Reembolso:** solo depÃ³sitos `held`. Si ya fue reembolsado, se responde
  `"Deposit is already refunded"`. El reembolso pasa el depÃ³sito a `refunded`,
  registra `refundedAt` y, si se envÃ­a un motivo, lo agrega a las notas como
  `Refund: <motivo>`.
- Se bloquea el depÃ³sito para evitar dos reembolsos o aplicaciones simultÃ¡neas.

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

## 13. ConserjerÃ­a (`/concierge/requests`)

- **Solo solicitudes de conserjerÃ­a.** El mÃ³dulo trabaja sobre `ServiceRequest`
  Ãºnicamente con `type = concierge`:
  - las solicitudes de otro tipo (`housekeeping`, `maintenance`, `other`) no
    aparecen en el listado;
  - consultar o cambiar el estado de una solicitud de otro tipo por su ID
    responde `404`, como si no existiera.
- **Listado (`GET`):** filtros opcionales `bookingId` y `status`. Un valor
  invÃ¡lido en cualquiera de los dos â†’ `400`. Orden: de la mÃ¡s antigua a la mÃ¡s
  reciente (`requestedAt`).
- **CreaciÃ³n (`POST`):**
  - `bookingId` y `description` (no vacÃ­a) son obligatorios. `notes` es
    opcional.
  - La reserva debe existir. Si no existe â†’ `400`, porque es una referencia
    en el body.
  - Solo se crean solicitudes para reservas `confirmed` o `checked_in`; cualquier
    otro estado responde `400`.
  - El backend fija `type = concierge` y `status = pending`, y controla
    `requestedAt`, `createdAt` y `updatedAt`.
  - La habitaciÃ³n y el huÃ©sped se toman de la reserva. `roomId` queda vacÃ­o si
    la reserva no tiene habitaciÃ³n asignada.
  - Si el cliente envÃ­a `type`, `status`, `roomId`, `guestId` o `chargeId`, se
    ignoran.
- **Flujo de estados (`POST /{requestId}/status`):**
  - `pending â†’ accepted | rejected | cancelled`
  - `accepted â†’ in_progress | cancelled`
  - `in_progress â†’ completed | cancelled`
  - `completed`, `rejected` y `cancelled` son **terminales**: cualquier cambio
    â†’ `400`.
  - Cualquier otra transiciÃ³n, incluido repetir el mismo estado â†’ `400`.
  - `rejected` solo puede producirse desde `pending`; si una solicitud aceptada
    o en progreso no se realizarÃ¡, se usa `cancelled`.
  - `notes` es opcional en el cambio de estado (por ejemplo, el motivo del
    rechazo) y se agrega a las notas existentes.
  - `responsibleUserId` es opcional en el cambio de estado. Si se envÃ­a, debe
    existir en `users`; el backend lo asocia a la solicitud para mantener la
    trazabilidad del responsable.
  - El cambio de estado bloquea la solicitud, asÃ­ que dos cambios simultÃ¡neos
    no pueden saltarse el flujo.
- **EdiciÃ³n (`PUT /{requestId}`):** solo solicitudes `pending`. Permite cambiar
  `description` y/o `notes`; al menos un campo debe venir en el body.
- **Sin cargos:** el mÃ³dulo nunca crea cargos ni toca el folio; `chargeId`
  queda en `null`.

## 14. Inventario (`/inventory/items`)

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
- **Sin integraciones:** no hay integracion con Room Service, Caja ni Folio.
- **Historial:** los movimientos nunca se borran ni se editan. Los errores se
  corrigen con movimientos compensatorios.

## 15. Housekeeping (`/housekeeping/rooms`)

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
  `cleaningUser`, `cleaningStartedAt`, `cleaningCompletedAt`, `inspectorUser`
  e `inspectedAt` segun corresponda. La persona que limpia y la que
  inspecciona pueden ser distintas.
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
  - `GET /stayover-cleanings?bookingId=...` lista las tareas de una reserva.
  - `POST /stayover-cleanings/{requestId}/start`: `pending -> in_progress`.
  - `POST /stayover-cleanings/{requestId}/complete`: `in_progress -> completed`.
  - Registra responsable, `requestedAt`, `startedAt`, `completedAt`,
    `createdAt` y `updatedAt`.
  - No cambia `Room.status` ni `Room.housekeepingStatus`: una habitacion
    `occupied` continua ocupada y no se libera por completar esta tarea.

## 16. Room Service (`/room-service`)

### Productos (`GET /room-service/products`)
- Solo lista productos **activos**, ordenados por nombre, con filtro
  opcional `category` (`minibar`, `shop`, `food_and_beverage`, `other`).

### Pedidos (`/room-service/orders`)
- **Listado (`GET`):** filtros opcionales `bookingId` y `status`. Orden: del
  mÃ¡s reciente al mÃ¡s antiguo.
- **Detalle (`GET /{orderId}`):** incluye las lÃ­neas del pedido. Si no
  existe â†’ `404`.
- **CreaciÃ³n (`POST`):**
  - `bookingId` y al menos un Ã­tem son obligatorios. `notes` es opcional.
  - Cada Ã­tem requiere `productId` y `quantity` (> 0).
  - La reserva y los productos deben existir, y los productos deben estar
    activos. Si no â†’ **`404`** (distinto de la convenciÃ³n del resto de la
    API, ver secciÃ³n 0).
  - Se crea con `status = pending` y `currency = GTQ`. La habitaciÃ³n y el
    huÃ©sped se toman de la reserva, y el backend controla los timestamps.
  - **Precio congelado:** cada lÃ­nea guarda el precio del producto al
    momento del pedido (`unitPriceCents`), asÃ­ que un cambio de precio
    posterior no afecta pedidos ya creados.
  - **Totales calculados en backend:** `lineTotalCents = quantity Ã—
    unitPriceCents` y `totalCents` = suma de las lÃ­neas.
- **Flujo de estados (`POST /{orderId}/status`):**
  - `pending â†’ accepted | rejected | cancelled`
  - `accepted â†’ preparing | cancelled`
  - `preparing â†’ ready | cancelled`
  - `ready â†’ on_the_way | cancelled`
  - `on_the_way â†’ delivered | cancelled`
  - `delivered`, `rejected` y `cancelled` son **terminales** (`400`).
  - Repetir el estado actual o hacer cualquier otra transiciÃ³n â†’ `400`.
  - El cambio de estado bloquea el pedido.
- **Sin integraciones:** crear o entregar un pedido **no** genera cargos en
  el folio y **no** descuenta `Product.stockQuantity` ni el inventario.

---

## 17. Decisiones acordadas pendientes de implementaciÃ³n

Esta secciÃ³n documenta decisiones ya tomadas por el equipo que **todavÃ­a no
deben leerse como comportamiento implementado**. Cuando una decisiÃ³n contradice
el estado actual, se deja explÃ­cita la diferencia entre:

- **Actual:** comportamiento hoy implementado en el backend.
- **Acordado:** regla que debe implementarse posteriormente.

### Reservas y acompaÃ±antes
- **Correcciones posteriores al check-in.**
  - Actual: el check-in valida huÃ©sped titular + acompaÃ±antes contra adultos,
    niÃ±os y capacidad; luego la reserva queda protegida contra cambios
    estructurales por el endpoint general.
  - Acordado: si se requieren correcciones posteriores al check-in, deberÃ¡n
    manejarse en el futuro mediante una operaciÃ³n administrativa controlada.

### Folio, pagos y depÃ³sitos
- **Reembolsos de pagos.**
  - Actual: solo se pueden reembolsar depÃ³sitos; no existe flujo de reembolso
    de pagos.
  - Acordado: los reembolsos de pagos deben implementarse como movimientos
    independientes. No se debe eliminar ni modificar el pago original y debe
    mantenerse trazabilidad completa.

### ConserjerÃ­a
- **Cobro de servicios.**
  - Actual: el mÃ³dulo no crea cargos ni toca el folio; `chargeId` queda en
    `null`.
  - Acordado: servicios de conserjerÃ­a con costo podrÃ¡n generar cargos al
    folio; servicios gratuitos no generan cargos. El cargo debe generarse
    cuando corresponda confirmar o completar realmente el servicio, no
    simplemente al crear la solicitud.
### Inventario
- **CRUD de articulos.**
  - Actual: no existe; los articulos solo se pueden crear por SQL.
  - Acordado: queda fuera de estas decisiones y requerira ticket especifico si
    se necesita administrar articulos desde la API.

### Room Service
- **Estado de reserva para crear pedidos.**
  - Actual: se pueden crear pedidos para reservas `pending`, `cancelled`,
    `no_show` o `checked_out`.
  - Acordado: los pedidos asociados a una habitaciÃ³n/reserva solo pueden
    crearse cuando la reserva estÃ© `checked_in`.
- **Inventario.**
  - Actual: no se valida disponibilidad ni se descuenta stock.
  - Acordado: `pending` todavÃ­a no descuenta inventario. Al pasar a
    `accepted`, se debe validar existencia y descontar inventario. Si el pedido
    se cancela despuÃ©s de haber afectado inventario, devolver las existencias.
    La operaciÃ³n debe ser transaccional y segura ante concurrencia.
- **Cargo al folio.**
  - Actual: crear, aceptar o entregar un pedido no genera cargos en el folio;
    `charge_id` no se usa.
  - Acordado: el cargo se genera cuando el pedido llega a `delivered`. No
    generar el cargo simplemente al crear o aceptar el pedido. Guardar y usar
    `charge_id` para impedir cargos duplicados.
- **Cantidades.**
  - Actual: `quantity` puede truncar decimales como `1.5` a `1`.
  - Acordado: solo permitir nÃºmeros enteros positivos (`1`, `2`, `3`, ...).
    `0`, negativos y decimales son invÃ¡lidos. Un decimal no debe redondearse
    ni truncarse; por ejemplo, `1.5` debe rechazarse con `400 Bad Request`.
- **CancelaciÃ³n.**
  - Actual: se puede cancelar hasta `on_the_way`.
  - Acordado: permitir cancelaciÃ³n hasta `ready`. Flujo principal:
    `pending â†’ accepted â†’ preparing â†’ ready â†’ on_the_way â†’ delivered`.
    CancelaciÃ³n permitida desde `pending`, `accepted`, `preparing` y `ready`.
    Una vez `on_the_way`, el pedido ya no puede cancelarse mediante el flujo
    normal.
- **Productos duplicados.**
  - Actual: el mismo producto puede aparecer en varias lÃ­neas independientes.
  - Acordado: no mantener varias lÃ­neas independientes para el mismo producto
    dentro de un pedido; consolidar productos repetidos en una sola lÃ­nea
    sumando sus cantidades.
- **Referencias del body.**
  - Actual: si no existen el `bookingId` o un `productId` del body, responde
    `404`, distinto de la convenciÃ³n del resto de la API.
  - Acordado: la convenciÃ³n general de cÃ³digos queda sujeta a la regla de
    cÃ³digos HTTP definida en General / seguridad.
