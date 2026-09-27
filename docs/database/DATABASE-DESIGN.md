# Diseno de Base de Datos - PMS Hotel Boutique

## 1. Objetivo

Este documento define el modelo relacional propuesto para el backend del PMS Hotel Boutique a partir del contrato funcional vigente del frontend. Su alcance es exclusivamente documental: no crea migraciones, entidades JPA, servicios, endpoints, datos seed ni integraciones.

El modelo contempla 27 tablas relacionales para seguridad, hotel, huespedes, reservas, folio, pagos, room service, solicitudes, inventario, caja, administracion y auditoria.

## 2. Convenciones

- Nombres de tablas y columnas en `snake_case`.
- Claves primarias y foraneas internas documentadas como `UUID`.
- Codigos funcionales y de negocio documentados como `VARCHAR` con `UNIQUE` cuando corresponda.
- Campos obligatorios marcados como `NOT NULL`.
- Campos monetarios en unidades menores enteras con sufijo `_cents`.
- Moneda actual: `GTQ`.
- No se documentan arrays TypeScript como relaciones. Las relaciones N:M se modelan con tablas puente.
- Los estados se documentan como valores permitidos. La implementacion podra usar `CHECK`, tablas catalogo o validacion de servicio segun se decida en migraciones.

## 3. Estrategia de identificadores

La estrategia definitiva del proyecto es usar `UUID` como identificador tecnico interno para las claves primarias y foraneas del modelo relacional. PostgreSQL utilizara su tipo nativo `UUID` y las entidades JPA usaran `UUID` cuando se implementen en un ticket posterior.

Los identificadores simulados actuales del frontend, por ejemplo `GST-001`, `BKG-001`, `RM-101` y `RATE-001`, pertenecen a la base mock y no se convierten automaticamente en PK reales de PostgreSQL.

Los codigos con significado funcional para el hotel permanecen separados de las PK y se documentan como `VARCHAR` con `UNIQUE` cuando corresponda. Ejemplos:

- `bookings.confirmation_code`
- `bookings.guest_link_code`
- `rooms.room_number`
- `room_types.code`
- `products.sku`
- `inventory_items.sku`
- `promotions.code`
- `permissions.key`
- `roles.code`
- `users.email`

La forma exacta de generacion de UUID queda fuera del alcance de este documento y se definira al crear migraciones Liquibase y entidades JPA.

## 4. Dinero

Todos los valores monetarios se documentan como enteros en centavos usando `BIGINT`. Esta decision mantiene el mismo contrato desde frontend hasta backend y evita representar dinero con tipos de punto flotante.

Ejemplos:

| Valor visible | Valor persistido |
|---------------|------------------|
| Q650.00 | `65000` |
| Q125.50 | `12550` |
| Q10.00 | `1000` |
| Q0.50 | `50` |

No se usa `FLOAT` ni `DOUBLE` para dinero. `NUMERIC`/`DECIMAL` no se consideran incorrectos en general; este proyecto documenta unidades menores enteras como convencion propia.

## 5. Fechas y timestamps

Las fechas civiles se documentan como `DATE`, por ejemplo `check_in`, `check_out`, `valid_from` y `valid_to`.

Los instantes de evento se documentan como `TIMESTAMPTZ`, por ejemplo `created_at`, `updated_at`, `opened_at`, `closed_at`, `charged_at`, `paid_at`, `requested_at`, `occurred_at`, `collected_at` y `refunded_at`. La estrategia propuesta es guardar instantes con zona horaria para conservar el momento real del evento y formatear la hora local en capas de aplicacion o presentacion.

Los campos `opens_at` y `closes_at` de `amenities` representan hora del dia y se documentan como `TIME`.

## 6. Modelo relacional

### Seguridad

1. `users`
2. `roles`
3. `permissions`
4. `role_permissions`

### Hotel

5. `room_types`
6. `room_features`
7. `room_type_features`
8. `rooms`
9. `rates`

### Huespedes y reservas

10. `guests`
11. `bookings`
12. `booking_companions`

### Folio y pagos

13. `guest_accounts`
14. `charges`
15. `payments`
16. `deposits`

### Room service y solicitudes

17. `products`
18. `orders`
19. `order_items`
20. `service_requests`

### Inventario

21. `inventory_items`
22. `inventory_movements`

### Caja

23. `cash_sessions`
24. `cash_movements`

### Administracion

25. `promotions`
26. `amenities`
27. `audit_logs`

## 7. Diccionario de datos

### `guests`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador del huesped. |
| `first_name` | `VARCHAR` | No |  | `NOT NULL` | Nombres. |
| `last_name` | `VARCHAR` | No |  | `NOT NULL` | Apellidos. |
| `email` | `VARCHAR` | Si |  |  | Correo de contacto. |
| `phone` | `VARCHAR` | Si |  |  | Telefono. |
| `nationality` | `VARCHAR` | Si |  |  | Nacionalidad. |
| `document_type` | `VARCHAR` | Si |  | `passport`, `national_id`, `driver_license` | Tipo de documento. |
| `document_number` | `VARCHAR` | Si |  |  | Numero de documento. |
| `notes` | `TEXT` | Si |  |  | Notas internas. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de ultima actualizacion. |

### `room_types`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador del tipo de habitacion. |
| `code` | `VARCHAR` | No | UK | `NOT NULL`, `UNIQUE` | Codigo funcional. |
| `name` | `VARCHAR` | No |  | `NOT NULL` | Nombre comercial. |
| `description` | `TEXT` | Si |  |  | Descripcion. |
| `capacity` | `INTEGER` | No |  | `NOT NULL`, `capacity > 0` | Capacidad maxima. |
| `bed_configuration` | `VARCHAR` | Si |  |  | Configuracion de camas. |
| `active` | `BOOLEAN` | No |  | `NOT NULL` | Indica si se ofrece. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

No se guarda `room_feature_ids`; la relacion se modela con `room_type_features`.

### `room_features`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de caracteristica. |
| `name` | `VARCHAR` | No |  | `NOT NULL` | Nombre. |
| `description` | `TEXT` | Si |  |  | Descripcion. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

### `room_type_features`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `room_type_id` | `UUID` | No | PK, FK | `NOT NULL`, FK a `room_types.id` | Tipo de habitacion. |
| `room_feature_id` | `UUID` | No | PK, FK | `NOT NULL`, FK a `room_features.id` | Caracteristica asociada. |

PK compuesta: (`room_type_id`, `room_feature_id`).

### `rooms`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de habitacion. |
| `room_number` | `VARCHAR` | No | UK | `NOT NULL`, `UNIQUE` | Numero visible de habitacion. |
| `room_type_id` | `UUID` | No | FK | `NOT NULL`, FK a `room_types.id` | Tipo de habitacion. |
| `floor` | `INTEGER` | Si |  |  | Piso. |
| `status` | `VARCHAR` | No |  | `available`, `occupied`, `maintenance`, `out_of_service` | Estado operativo. |
| `housekeeping_status` | `VARCHAR` | No |  | `dirty`, `cleaning`, `clean`, `inspected` | Estado de limpieza. |
| `notes` | `TEXT` | Si |  |  | Notas. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

No existe columna `is_assignable`; es un valor derivado: `status = available` y `housekeeping_status IN (clean, inspected)`.

### `rates`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de tarifa. |
| `room_type_id` | `UUID` | No | FK | `NOT NULL`, FK a `room_types.id` | Tipo de habitacion al que aplica. |
| `name` | `VARCHAR` | No |  | `NOT NULL` | Nombre de tarifa. |
| `valid_from` | `DATE` | No |  | `NOT NULL` | Inicio de vigencia. |
| `valid_to` | `DATE` | Si |  | `valid_to IS NULL OR valid_to >= valid_from` | Fin de vigencia; `NULL` significa sin fecha de finalizacion definida. |
| `price_cents` | `BIGINT` | No |  | `NOT NULL`, `price_cents >= 0` | Precio por noche en centavos. |
| `currency` | `CHAR(3)` | No |  | `NOT NULL`, actual `GTQ` | Moneda. |
| `minimum_nights` | `INTEGER` | No |  | `NOT NULL`, `minimum_nights > 0` | Minimo de noches. |
| `refundable` | `BOOLEAN` | No |  | `NOT NULL` | Indica si es reembolsable. |
| `active` | `BOOLEAN` | No |  | `NOT NULL` | Indica si esta activa. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

### `bookings`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de reserva. |
| `confirmation_code` | `VARCHAR` | No | UK | `NOT NULL`, `UNIQUE` | Codigo de confirmacion. |
| `guest_link_code` | `VARCHAR` | No | UK | `NOT NULL`, `UNIQUE` | Codigo para portal de huesped. |
| `guest_id` | `UUID` | No | FK | `NOT NULL`, FK a `guests.id` | Huesped titular. |
| `room_id` | `UUID` | Si | FK | FK a `rooms.id` | Habitacion asignada; puede ser nula al reservar. |
| `room_type_id` | `UUID` | No | FK | `NOT NULL`, FK a `room_types.id` | Tipo reservado. |
| `rate_id` | `UUID` | Si | FK | FK a `rates.id` | Tarifa aplicada si existe. |
| `check_in` | `DATE` | No |  | `NOT NULL` | Fecha civil de entrada. |
| `check_out` | `DATE` | No |  | `NOT NULL`, `check_out > check_in` | Fecha civil de salida. |
| `status` | `VARCHAR` | No |  | `pending`, `confirmed`, `checked_in`, `checked_out`, `cancelled`, `no_show` | Estado de reserva. |
| `adults` | `INTEGER` | No |  | `NOT NULL`, `adults >= 0` | Adultos. |
| `children` | `INTEGER` | No |  | `NOT NULL`, `children >= 0` | Menores. |
| `total_amount_cents` | `BIGINT` | No |  | `NOT NULL`, `total_amount_cents >= 0` | Total estimado en centavos. |
| `currency` | `CHAR(3)` | No |  | `NOT NULL`, actual `GTQ` | Moneda. |
| `notes` | `TEXT` | Si |  |  | Notas. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

Regla de negocio: `adults + children <= room_types.capacity`.

### `booking_companions`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador del acompanante. |
| `booking_id` | `UUID` | No | FK | `NOT NULL`, FK a `bookings.id` | Reserva asociada. |
| `first_name` | `VARCHAR` | No |  | `NOT NULL` | Nombres. |
| `last_name` | `VARCHAR` | No |  | `NOT NULL` | Apellidos. |
| `document_type` | `VARCHAR` | Si |  | `passport`, `national_id`, `driver_license` | Tipo de documento. |
| `document_number` | `VARCHAR` | Si |  |  | Numero de documento. |
| `guest_type` | `VARCHAR` | No |  | `adult`, `child` | Tipo de acompanante. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

### `guest_accounts`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de folio. |
| `booking_id` | `UUID` | No | FK, UK | `NOT NULL`, `UNIQUE`, FK a `bookings.id` | Reserva asociada; materializa relacion 1:0..1 desde reserva hacia folio. |
| `guest_id` | `UUID` | No | FK | `NOT NULL`, FK a `guests.id` | Huesped titular. |
| `status` | `VARCHAR` | No |  | `open`, `closed` | Estado del folio. |
| `balance_cents` | `BIGINT` | No |  | `NOT NULL` | Saldo en centavos. |
| `currency` | `CHAR(3)` | No |  | `NOT NULL`, actual `GTQ` | Moneda. |
| `opened_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Apertura. |
| `closed_at` | `TIMESTAMPTZ` | Si |  |  | Cierre. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

### `charges`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de cargo. |
| `booking_id` | `UUID` | No | FK | `NOT NULL`, FK a `bookings.id` | Reserva asociada. |
| `product_id` | `UUID` | Si | FK | FK a `products.id` | Producto si el cargo viene de consumo. |
| `description` | `TEXT` | No |  | `NOT NULL` | Descripcion del cargo. |
| `quantity` | `INTEGER` | No |  | `NOT NULL`, `quantity > 0` | Cantidad. |
| `unit_price_cents` | `BIGINT` | No |  | `NOT NULL`, `unit_price_cents >= 0` | Precio unitario en centavos. |
| `amount_cents` | `BIGINT` | No |  | `NOT NULL`, `amount_cents >= 0` | Total del cargo en centavos. |
| `currency` | `CHAR(3)` | No |  | `NOT NULL`, actual `GTQ` | Moneda. |
| `category` | `VARCHAR` | No |  | `stay`, `consumption` | Categoria. |
| `status` | `VARCHAR` | No |  | `pending`, `posted`, `voided` | Estado. |
| `charged_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Momento del cargo. |
| `created_by_user_id` | `UUID` | Si | FK | FK a `users.id` | Usuario que crea el cargo. |
| `void_reason` | `TEXT` | Si |  |  | Motivo de anulacion. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |

### `payments`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de pago. |
| `booking_id` | `UUID` | No | FK | `NOT NULL`, FK a `bookings.id` | Reserva asociada. |
| `amount_cents` | `BIGINT` | No |  | `NOT NULL`, `amount_cents > 0` | Monto pagado en centavos. |
| `currency` | `CHAR(3)` | No |  | `NOT NULL`, actual `GTQ` | Moneda. |
| `method` | `VARCHAR` | No |  | `cash`, `credit_card`, `debit_card`, `bank_transfer`, `online` | Metodo. |
| `status` | `VARCHAR` | No |  | `pending`, `completed`, `failed`, `refunded` | Estado. |
| `transaction_reference` | `VARCHAR` | Si |  |  | Referencia externa. |
| `paid_at` | `TIMESTAMPTZ` | Si |  |  | Momento de pago. |
| `processed_by_user_id` | `UUID` | Si | FK | FK a `users.id` | Usuario que procesa. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |

### `deposits`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de deposito. |
| `booking_id` | `UUID` | No | FK | `NOT NULL`, FK a `bookings.id` | Reserva asociada. |
| `guest_id` | `UUID` | No | FK | `NOT NULL`, FK a `guests.id` | Huesped que entrega el deposito. |
| `amount_cents` | `BIGINT` | No |  | `NOT NULL`, `amount_cents >= 0` | Monto en centavos. |
| `currency` | `CHAR(3)` | No |  | `NOT NULL`, actual `GTQ` | Moneda. |
| `method` | `VARCHAR` | No |  | `cash`, `credit_card`, `debit_card`, `bank_transfer` | Metodo. |
| `status` | `VARCHAR` | No |  | `held`, `refunded`, `applied` | Estado. |
| `collected_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Momento de cobro. |
| `refunded_at` | `TIMESTAMPTZ` | Si |  |  | Momento de devolucion. |
| `notes` | `TEXT` | Si |  |  | Notas. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

### `products`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de producto. |
| `sku` | `VARCHAR` | No | UK | `NOT NULL`, `UNIQUE` | SKU. |
| `name` | `VARCHAR` | No |  | `NOT NULL` | Nombre. |
| `description` | `TEXT` | Si |  |  | Descripcion. |
| `category` | `VARCHAR` | No |  | `minibar`, `shop`, `food_and_beverage`, `other` | Categoria. |
| `price_cents` | `BIGINT` | No |  | `NOT NULL`, `price_cents >= 0` | Precio en centavos. |
| `currency` | `CHAR(3)` | No |  | `NOT NULL`, actual `GTQ` | Moneda. |
| `stock_quantity` | `INTEGER` | No |  | `NOT NULL`, `stock_quantity >= 0` | Existencia comercial. |
| `reorder_level` | `INTEGER` | No |  | `NOT NULL`, `reorder_level >= 0` | Punto de reorden. |
| `active` | `BOOLEAN` | No |  | `NOT NULL` | Indica si se ofrece. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

### `orders`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de pedido. |
| `booking_id` | `UUID` | No | FK | `NOT NULL`, FK a `bookings.id` | Reserva asociada. |
| `room_id` | `UUID` | Si | FK | FK a `rooms.id` | Habitacion desde la que se solicita. |
| `guest_id` | `UUID` | Si | FK | FK a `guests.id` | Huesped solicitante si esta identificado en el pedido. |
| `status` | `VARCHAR` | No |  | `pending`, `accepted`, `preparing`, `ready`, `on_the_way`, `delivered`, `rejected`, `cancelled` | Estado. |
| `notes` | `TEXT` | Si |  |  | Notas. |
| `currency` | `CHAR(3)` | No |  | `NOT NULL`, actual `GTQ` | Moneda. |
| `charge_id` | `UUID` | Si | FK | FK a `charges.id` | Cargo generado al entregar si aplica. |
| `requested_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Momento de solicitud. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

Los items del pedido no se guardan como JSON; se modelan en `order_items`.

### `order_items`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | PK propia de la linea de pedido. |
| `order_id` | `UUID` | No | FK | `NOT NULL`, FK a `orders.id` | Pedido. |
| `product_id` | `UUID` | No | FK | `NOT NULL`, FK a `products.id` | Producto solicitado. |
| `quantity` | `INTEGER` | No |  | `NOT NULL`, `quantity > 0` | Cantidad. |
| `unit_price_cents` | `BIGINT` | No |  | `NOT NULL`, `unit_price_cents >= 0` | Precio del producto al momento del pedido. |

Decision documentada: `order_items` usa PK propia `id` para mantener consistencia con el resto del modelo y permitir futuras referencias a una linea especifica sin depender de una PK compuesta.

### `service_requests`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de solicitud. |
| `booking_id` | `UUID` | No | FK | `NOT NULL`, FK a `bookings.id` | Reserva asociada. |
| `room_id` | `UUID` | Si | FK | FK a `rooms.id` | Habitacion relacionada. |
| `guest_id` | `UUID` | Si | FK | FK a `guests.id` | Huesped solicitante si esta identificado en la solicitud. |
| `type` | `VARCHAR` | No |  | `housekeeping`, `concierge`, `maintenance`, `other` | Tipo. |
| `description` | `TEXT` | No |  | `NOT NULL` | Descripcion. |
| `status` | `VARCHAR` | No |  | `pending`, `accepted`, `in_progress`, `completed`, `rejected` | Estado. |
| `notes` | `TEXT` | Si |  |  | Notas internas. |
| `charge_id` | `UUID` | Si | FK | FK a `charges.id` | Cargo asociado si aplica. |
| `requested_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Momento de solicitud. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

### `inventory_items`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de item de inventario. |
| `sku` | `VARCHAR` | No | UK | `NOT NULL`, `UNIQUE` | SKU de inventario. |
| `name` | `VARCHAR` | No |  | `NOT NULL` | Nombre. |
| `description` | `TEXT` | Si |  |  | Descripcion. |
| `category` | `VARCHAR` | No |  | `NOT NULL` | Categoria operativa. |
| `unit` | `VARCHAR` | No |  | `NOT NULL` | Unidad de medida. |
| `current_quantity` | `INTEGER` | No |  | `NOT NULL`, `current_quantity >= 0` | Cantidad actual. |
| `minimum_quantity` | `INTEGER` | No |  | `NOT NULL`, `minimum_quantity >= 0` | Cantidad minima. |
| `product_id` | `UUID` | Si | FK | FK a `products.id` | Producto comercial relacionado si existe. |
| `active` | `BOOLEAN` | No |  | `NOT NULL` | Indica si se controla. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

`Product` e `InventoryItem` no son la misma entidad.

### `inventory_movements`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de movimiento. |
| `inventory_item_id` | `UUID` | No | FK | `NOT NULL`, FK a `inventory_items.id` | Item afectado. |
| `type` | `VARCHAR` | No |  | `in`, `out` | Tipo de movimiento. |
| `reason` | `VARCHAR` | No |  | `purchase`, `restock`, `consumption`, `sale`, `shrinkage` | Motivo. |
| `quantity` | `INTEGER` | No |  | `NOT NULL`, `quantity > 0` | Cantidad movida. |
| `responsible_user_id` | `UUID` | Si | FK | FK a `users.id` | Usuario responsable. |
| `occurred_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Momento del movimiento. |
| `notes` | `TEXT` | Si |  |  | Notas. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |

### `cash_sessions`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de sesion de caja. |
| `opened_by_user_id` | `UUID` | No | FK | `NOT NULL`, FK a `users.id` | Usuario que abre. |
| `opened_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Momento de apertura. |
| `opening_balance_cents` | `BIGINT` | No |  | `NOT NULL` | Saldo inicial en centavos. |
| `currency` | `CHAR(3)` | No |  | `NOT NULL`, actual `GTQ` | Moneda. |
| `status` | `VARCHAR` | No |  | `open`, `closed` | Estado. |
| `closed_by_user_id` | `UUID` | Si | FK | FK a `users.id` | Usuario que cierra. |
| `closed_at` | `TIMESTAMPTZ` | Si |  |  | Momento de cierre. |
| `expected_balance_cents` | `BIGINT` | Si |  |  | Saldo esperado al cierre. |
| `counted_balance_cents` | `BIGINT` | Si |  |  | Saldo contado. |
| `difference_cents` | `BIGINT` | Si |  |  | Diferencia. |
| `notes` | `TEXT` | Si |  |  | Notas. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

Los campos de cierre pueden ser `NULL` mientras la caja este abierta.

### `cash_movements`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de movimiento de caja. |
| `cash_session_id` | `UUID` | No | FK | `NOT NULL`, FK a `cash_sessions.id` | Sesion de caja. |
| `type` | `VARCHAR` | No |  | `income`, `expense` | Tipo. |
| `concept` | `VARCHAR` | No |  | `NOT NULL` | Concepto. |
| `amount_cents` | `BIGINT` | No |  | `NOT NULL`, `amount_cents >= 0` | Monto en centavos. |
| `currency` | `CHAR(3)` | No |  | `NOT NULL`, actual `GTQ` | Moneda. |
| `responsible_user_id` | `UUID` | Si | FK | FK a `users.id` | Usuario responsable. |
| `occurred_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Momento del movimiento. |
| `payment_id` | `UUID` | Si | FK | FK a `payments.id` | Pago asociado si aplica. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |

### `promotions`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de promocion. |
| `code` | `VARCHAR` | No | UK | `NOT NULL`, `UNIQUE` | Codigo promocional. |
| `name` | `VARCHAR` | No |  | `NOT NULL` | Nombre. |
| `description` | `TEXT` | Si |  |  | Descripcion. |
| `discount_percent` | `INTEGER` | No |  | `NOT NULL`, `0 <= discount_percent <= 100` | Porcentaje de descuento. |
| `valid_from` | `DATE` | No |  | `NOT NULL` | Inicio de vigencia. |
| `valid_to` | `DATE` | Si |  | `valid_to IS NULL OR valid_to >= valid_from` | Fin de vigencia; `NULL` significa sin fecha de finalizacion definida. |
| `active` | `BOOLEAN` | No |  | `NOT NULL` | Indica si esta activa. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

### `amenities`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de amenidad. |
| `name` | `VARCHAR` | No |  | `NOT NULL` | Nombre. |
| `description` | `TEXT` | Si |  |  | Descripcion. |
| `category` | `VARCHAR` | No |  | `room`, `hotel`, `service` | Categoria. |
| `location` | `VARCHAR` | Si |  |  | Ubicacion. |
| `opens_at` | `TIME` | Si |  |  | Hora de apertura, nula para servicios continuos. |
| `closes_at` | `TIME` | Si |  |  | Hora de cierre, nula para servicios continuos. |
| `active` | `BOOLEAN` | No |  | `NOT NULL` | Indica si esta activa. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

### `audit_logs`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de auditoria. |
| `user_id` | `UUID` | Si | FK | FK a `users.id` | Usuario que ejecuta la accion. |
| `module` | `VARCHAR` | No |  | `NOT NULL` | Modulo afectado. |
| `action` | `VARCHAR` | No |  | `NOT NULL` | Accion. |
| `entity_type` | `VARCHAR` | No |  | `NOT NULL` | Tipo de entidad afectada. |
| `entity_id` | `UUID` | No |  | `NOT NULL` | ID de entidad afectada. |
| `occurred_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Momento del evento. |
| `details` | `JSONB` | Si |  |  | Detalles adicionales de auditoria. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de registro. |

No se crea FK dinamica para `entity_id` porque puede referirse a distintos tipos de entidad.

### `permissions`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de permiso. |
| `key` | `VARCHAR` | No | UK | `NOT NULL`, `UNIQUE` | Clave funcional. |
| `name` | `VARCHAR` | No |  | `NOT NULL` | Nombre. |
| `description` | `TEXT` | Si |  |  | Descripcion. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

### `roles`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de rol. |
| `code` | `VARCHAR` | No | UK | `NOT NULL`, `UNIQUE` | Codigo funcional. |
| `name` | `VARCHAR` | No |  | `NOT NULL` | Nombre. |
| `active` | `BOOLEAN` | No |  | `NOT NULL` | Indica si el rol esta activo. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

Roles funcionales actuales: `admin`, `guest`, `reception`, `housekeeping`, `concierge`, `room_service`.

No se guarda `permission_ids`; la relacion se modela con `role_permissions`.

### `role_permissions`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `role_id` | `UUID` | No | PK, FK | `NOT NULL`, FK a `roles.id` | Rol. |
| `permission_id` | `UUID` | No | PK, FK | `NOT NULL`, FK a `permissions.id` | Permiso. |

PK compuesta: (`role_id`, `permission_id`).

### `users`

| Columna | Tipo PostgreSQL | Nulo | Clave | Restricciones | Descripcion |
|---------|-----------------|------|-------|---------------|-------------|
| `id` | `UUID` | No | PK | `NOT NULL` | Identificador de usuario. |
| `first_name` | `VARCHAR` | No |  | `NOT NULL` | Nombres. |
| `last_name` | `VARCHAR` | No |  | `NOT NULL` | Apellidos. |
| `email` | `VARCHAR` | No | UK | `NOT NULL`, `UNIQUE` | Correo de inicio de sesion. |
| `password_hash` | `VARCHAR` | No |  | `NOT NULL` | Hash para autenticacion real futura. |
| `role_id` | `UUID` | No | FK | `NOT NULL`, FK a `roles.id` | Rol asignado. |
| `status` | `VARCHAR` | No |  | `active`, `inactive` | Estado del usuario. |
| `created_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de creacion. |
| `updated_at` | `TIMESTAMPTZ` | No |  | `NOT NULL` | Fecha de actualizacion. |

No se copian contrasenas mock del frontend.

## 8. Relaciones

### 1:0..1

- `bookings` 1:0..1 `guest_accounts`, implementada con `guest_accounts.booking_id UNIQUE`. Una reserva puede existir antes de abrir su folio.

### 1:N

- `guests` 1:N `bookings`.
- `room_types` 1:N `rooms`.
- `room_types` 1:N `rates`.
- `bookings` 1:N `booking_companions`.
- `bookings` 1:N `charges`.
- `bookings` 1:N `payments`.
- `bookings` 1:N `deposits`.
- `bookings` 1:N `orders`.
- `bookings` 1:N `service_requests`.
- `orders` 1:N `order_items`.
- `products` 1:N `order_items`.
- `inventory_items` 1:N `inventory_movements`.
- `cash_sessions` 1:N `cash_movements`.
- `roles` 1:N `users`.

### N:M mediante tablas puente

- `room_types` N:M `room_features` mediante `room_type_features`.
- `roles` N:M `permissions` mediante `role_permissions`.

### Relaciones opcionales relevantes

- `bookings.room_id` -> `rooms.id`.
- `bookings.rate_id` -> `rates.id`.
- `charges.product_id` -> `products.id`.
- `charges.created_by_user_id` -> `users.id`.
- `payments.processed_by_user_id` -> `users.id`.
- `inventory_items.product_id` -> `products.id`.
- `inventory_movements.responsible_user_id` -> `users.id`.
- `cash_sessions.closed_by_user_id` -> `users.id`.
- `cash_movements.responsible_user_id` -> `users.id`.
- `cash_movements.payment_id` -> `payments.id`.
- `orders.room_id` -> `rooms.id`.
- `orders.guest_id` -> `guests.id`.
- `orders.charge_id` -> `charges.id`.
- `service_requests.room_id` -> `rooms.id`.
- `service_requests.guest_id` -> `guests.id`.
- `service_requests.charge_id` -> `charges.id`.
- `audit_logs.user_id` -> `users.id`.

## 9. Estados

- `rooms.status`: `available`, `occupied`, `maintenance`, `out_of_service`.
- `rooms.housekeeping_status`: `dirty`, `cleaning`, `clean`, `inspected`.
- `bookings.status`: `pending`, `confirmed`, `checked_in`, `checked_out`, `cancelled`, `no_show`.
- `booking_companions.guest_type`: `adult`, `child`.
- `guest_accounts.status`: `open`, `closed`.
- `charges.category`: `stay`, `consumption`.
- `charges.status`: `pending`, `posted`, `voided`.
- `payments.method`: `cash`, `credit_card`, `debit_card`, `bank_transfer`, `online`.
- `payments.status`: `pending`, `completed`, `failed`, `refunded`.
- `deposits.method`: `cash`, `credit_card`, `debit_card`, `bank_transfer`.
- `deposits.status`: `held`, `refunded`, `applied`.
- `products.category`: `minibar`, `shop`, `food_and_beverage`, `other`.
- `orders.status`: `pending`, `accepted`, `preparing`, `ready`, `on_the_way`, `delivered`, `rejected`, `cancelled`.
- `service_requests.type`: `housekeeping`, `concierge`, `maintenance`, `other`.
- `service_requests.status`: `pending`, `accepted`, `in_progress`, `completed`, `rejected`.
- `inventory_movements.type`: `in`, `out`.
- `inventory_movements.reason`: `purchase`, `restock`, `consumption`, `sale`, `shrinkage`.
- `cash_sessions.status`: `open`, `closed`.
- `cash_movements.type`: `income`, `expense`.
- `amenities.category`: `room`, `hotel`, `service`.
- `users.status`: `active`, `inactive`.

## 10. Reglas de integridad

### Representables directamente en PostgreSQL

- PK en todas las tablas.
- FK hacia tablas existentes.
- `UNIQUE` en `room_types.code`, `rooms.room_number`, `bookings.confirmation_code`, `bookings.guest_link_code`, `guest_accounts.booking_id`, `products.sku`, `inventory_items.sku`, `promotions.code`, `permissions.key`, `roles.code` y `users.email`.
- PK compuestas en `room_type_features` y `role_permissions`.
- Valores no negativos para montos `_cents`, excepto pagos que requieren `payments.amount_cents > 0`.
- Valores positivos para cantidades y noches minimas.
- `check_out > check_in`.
- Rangos de vigencia abiertos o validos: `valid_to IS NULL OR valid_to >= valid_from`.
- `discount_percent` entre 0 y 100.
- Nulabilidad de FK opcionales segun el contrato.

### Reglas para validar posteriormente en Spring Boot/Service

- Capacidad de habitacion: `adults + children <= room_types.capacity`.
- Asignabilidad de habitacion: `status = available` y `housekeeping_status IN (clean, inspected)`.
- Transiciones validas de estados de reservas, habitaciones, pagos, solicitudes, pedidos y caja.
- Calculo del saldo del folio a partir de cargos, pagos y depositos aplicados.
- Checkout solo cuando `guest_accounts.balance_cents = 0`.
- Consistencia entre cargos de estancia y consumo.
- Generacion de cargo al entregar un pedido de Room Service.
- Cierre de caja con saldos esperados, contados y diferencia.
- Consistencia de inventario al registrar entradas, salidas, ventas, consumos y mermas.

No se intenta resolver toda la logica con `CHECK` constraints complejos.

## 11. Reglas de negocio para Service

- Crear reservas con habitacion nula es valido cuando aun no existe asignacion concreta.
- La tarifa de reserva puede ser nula si el contrato actual no la define.
- El folio se abre para una reserva y se cierra solo cuando el balance queda en cero.
- `charges.category = stay` representa estancia; `charges.category = consumption` representa consumos y servicios.
- Los pedidos de Room Service se componen de `order_items`; al pasar a `delivered` podran generar un `charge`.
- `orders.charge_id` y `service_requests.charge_id` vinculan servicios facturables con el folio.
- El stock comercial de `products` y el inventario operativo de `inventory_items` pueden relacionarse, pero no son la misma entidad.
- Los movimientos de caja asociados a pagos usan `cash_movements.payment_id`.
- Auditoria registra el actor con `user_id` y la entidad afectada con `entity_type` + `entity_id`, sin FK dinamica. `entity_id` guarda el UUID interno de la entidad auditada.

## 12. Decisiones de diseno

- Usar `UUID` para PK/FK internas y mantener codigos de negocio como `VARCHAR`.
- Modelar relaciones N:M con tablas puente: `room_type_features` y `role_permissions`.
- Modelar items de pedidos en `order_items` y no como JSON.
- Usar `BIGINT` para dinero en centavos y sufijo `_cents`.
- Usar `DATE` para fechas civiles y `TIMESTAMPTZ` para instantes.
- No persistir valores derivados como `rooms.is_assignable`.
- No persistir arrays del frontend como `room_feature_ids` o `permission_ids`.
- Usar `guest_accounts.booking_id UNIQUE` para representar la relacion 1:0..1 con `bookings`.
- Usar `order_items.id` como PK propia para facilitar referencias futuras a lineas de pedido.

## 13. Decisiones pendientes

- Forma final de implementar estados: `CHECK`, enums de PostgreSQL, tablas catalogo o validacion de servicio.
- Politicas de borrado/retencion: `RESTRICT`, `CASCADE`, borrado logico o archivado.
- Indices adicionales por consultas reales: busquedas por fechas, estados, habitacion, huesped y codigos.
- Precision de auditoria para cambios detallados en `audit_logs.details`.
- Reglas definitivas de disponibilidad y solapamiento de reservas por habitacion.
