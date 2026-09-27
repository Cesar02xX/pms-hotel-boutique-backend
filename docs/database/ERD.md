# DER - PMS Hotel Boutique

Este DER documenta las 27 tablas del modelo relacional definido en `docs/database/DATABASE-DESIGN.md`. Las PK y FK internas usan `UUID`; los codigos funcionales permanecen como `VARCHAR`. Las relaciones N:M se representan con tablas puente y no con arrays del contrato TypeScript.

```mermaid
erDiagram
    GUESTS ||--o{ BOOKINGS : realiza
    GUESTS ||--o{ GUEST_ACCOUNTS : titular
    GUESTS ||--o{ DEPOSITS : entrega
    GUESTS |o--o{ ORDERS : solicita
    GUESTS |o--o{ SERVICE_REQUESTS : solicita

    ROOM_TYPES ||--o{ ROOMS : clasifica
    ROOM_TYPES ||--o{ RATES : tarifa
    ROOM_TYPES ||--o{ BOOKINGS : reservado_como
    ROOM_TYPES ||--o{ ROOM_TYPE_FEATURES : tiene
    ROOM_FEATURES ||--o{ ROOM_TYPE_FEATURES : describe

    ROOMS |o--o{ BOOKINGS : asignada_a
    ROOMS |o--o{ ORDERS : recibe
    ROOMS |o--o{ SERVICE_REQUESTS : atiende

    RATES |o--o{ BOOKINGS : aplica

    BOOKINGS ||--o{ BOOKING_COMPANIONS : incluye
    BOOKINGS ||--o| GUEST_ACCOUNTS : abre
    BOOKINGS ||--o{ CHARGES : genera
    BOOKINGS ||--o{ PAYMENTS : recibe
    BOOKINGS ||--o{ DEPOSITS : respalda
    BOOKINGS ||--o{ ORDERS : contiene
    BOOKINGS ||--o{ SERVICE_REQUESTS : registra

    PRODUCTS |o--o{ CHARGES : origina
    PRODUCTS ||--o{ ORDER_ITEMS : vendido_como
    PRODUCTS |o--o{ INVENTORY_ITEMS : puede_controlar

    ORDERS ||--o{ ORDER_ITEMS : contiene
    CHARGES |o--o{ ORDERS : factura
    CHARGES |o--o{ SERVICE_REQUESTS : factura

    INVENTORY_ITEMS ||--o{ INVENTORY_MOVEMENTS : mueve

    CASH_SESSIONS ||--o{ CASH_MOVEMENTS : contiene
    PAYMENTS |o--o{ CASH_MOVEMENTS : registra

    ROLES ||--o{ USERS : asignado_a
    ROLES ||--o{ ROLE_PERMISSIONS : concede
    PERMISSIONS ||--o{ ROLE_PERMISSIONS : incluido_en

    USERS |o--o{ CHARGES : crea
    USERS |o--o{ PAYMENTS : procesa
    USERS |o--o{ INVENTORY_MOVEMENTS : responsable
    USERS ||--o{ CASH_SESSIONS : abre
    USERS |o--o{ CASH_SESSIONS : cierra
    USERS |o--o{ CASH_MOVEMENTS : responsable
    USERS |o--o{ AUDIT_LOGS : ejecuta

    GUESTS {
        UUID id PK
        VARCHAR first_name
        VARCHAR last_name
        VARCHAR email
        VARCHAR phone
        VARCHAR nationality
        VARCHAR document_type
        VARCHAR document_number
        TEXT notes
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    ROOM_TYPES {
        UUID id PK
        VARCHAR code UK
        VARCHAR name
        TEXT description
        INTEGER capacity
        VARCHAR bed_configuration
        BOOLEAN active
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    ROOM_FEATURES {
        UUID id PK
        VARCHAR name
        TEXT description
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    ROOM_TYPE_FEATURES {
        UUID room_type_id PK,FK
        UUID room_feature_id PK,FK
    }

    ROOMS {
        UUID id PK
        VARCHAR room_number UK
        UUID room_type_id FK
        INTEGER floor
        VARCHAR status
        VARCHAR housekeeping_status
        TEXT notes
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    RATES {
        UUID id PK
        UUID room_type_id FK
        VARCHAR name
        DATE valid_from
        DATE valid_to
        BIGINT price_cents
        CHAR currency
        INTEGER minimum_nights
        BOOLEAN refundable
        BOOLEAN active
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    BOOKINGS {
        UUID id PK
        VARCHAR confirmation_code UK
        VARCHAR guest_link_code UK
        UUID guest_id FK
        UUID room_id FK
        UUID room_type_id FK
        UUID rate_id FK
        DATE check_in
        DATE check_out
        VARCHAR status
        INTEGER adults
        INTEGER children
        BIGINT total_amount_cents
        CHAR currency
        TEXT notes
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    BOOKING_COMPANIONS {
        UUID id PK
        UUID booking_id FK
        VARCHAR first_name
        VARCHAR last_name
        VARCHAR document_type
        VARCHAR document_number
        VARCHAR guest_type
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    GUEST_ACCOUNTS {
        UUID id PK
        UUID booking_id FK,UK
        UUID guest_id FK
        VARCHAR status
        BIGINT balance_cents
        CHAR currency
        TIMESTAMPTZ opened_at
        TIMESTAMPTZ closed_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    CHARGES {
        UUID id PK
        UUID booking_id FK
        UUID product_id FK
        TEXT description
        INTEGER quantity
        BIGINT unit_price_cents
        BIGINT amount_cents
        CHAR currency
        VARCHAR category
        VARCHAR status
        TIMESTAMPTZ charged_at
        UUID created_by_user_id FK
        TEXT void_reason
        TIMESTAMPTZ created_at
    }

    PAYMENTS {
        UUID id PK
        UUID booking_id FK
        BIGINT amount_cents
        CHAR currency
        VARCHAR method
        VARCHAR status
        VARCHAR transaction_reference
        TIMESTAMPTZ paid_at
        UUID processed_by_user_id FK
        TIMESTAMPTZ created_at
    }

    DEPOSITS {
        UUID id PK
        UUID booking_id FK
        UUID guest_id FK
        BIGINT amount_cents
        CHAR currency
        VARCHAR method
        VARCHAR status
        TIMESTAMPTZ collected_at
        TIMESTAMPTZ refunded_at
        TEXT notes
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    PRODUCTS {
        UUID id PK
        VARCHAR sku UK
        VARCHAR name
        TEXT description
        VARCHAR category
        BIGINT price_cents
        CHAR currency
        INTEGER stock_quantity
        INTEGER reorder_level
        BOOLEAN active
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    ORDERS {
        UUID id PK
        UUID booking_id FK
        UUID room_id FK
        UUID guest_id FK
        VARCHAR status
        TEXT notes
        CHAR currency
        UUID charge_id FK
        TIMESTAMPTZ requested_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    ORDER_ITEMS {
        UUID id PK
        UUID order_id FK
        UUID product_id FK
        INTEGER quantity
        BIGINT unit_price_cents
    }

    SERVICE_REQUESTS {
        UUID id PK
        UUID booking_id FK
        UUID room_id FK
        UUID guest_id FK
        VARCHAR type
        TEXT description
        VARCHAR status
        TEXT notes
        UUID charge_id FK
        TIMESTAMPTZ requested_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    INVENTORY_ITEMS {
        UUID id PK
        VARCHAR sku UK
        VARCHAR name
        TEXT description
        VARCHAR category
        VARCHAR unit
        INTEGER current_quantity
        INTEGER minimum_quantity
        UUID product_id FK
        BOOLEAN active
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    INVENTORY_MOVEMENTS {
        UUID id PK
        UUID inventory_item_id FK
        VARCHAR type
        VARCHAR reason
        INTEGER quantity
        UUID responsible_user_id FK
        TIMESTAMPTZ occurred_at
        TEXT notes
        TIMESTAMPTZ created_at
    }

    CASH_SESSIONS {
        UUID id PK
        UUID opened_by_user_id FK
        TIMESTAMPTZ opened_at
        BIGINT opening_balance_cents
        CHAR currency
        VARCHAR status
        UUID closed_by_user_id FK
        TIMESTAMPTZ closed_at
        BIGINT expected_balance_cents
        BIGINT counted_balance_cents
        BIGINT difference_cents
        TEXT notes
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    CASH_MOVEMENTS {
        UUID id PK
        UUID cash_session_id FK
        VARCHAR type
        VARCHAR concept
        BIGINT amount_cents
        CHAR currency
        UUID responsible_user_id FK
        TIMESTAMPTZ occurred_at
        UUID payment_id FK
        TIMESTAMPTZ created_at
    }

    PROMOTIONS {
        UUID id PK
        VARCHAR code UK
        VARCHAR name
        TEXT description
        INTEGER discount_percent
        DATE valid_from
        DATE valid_to
        BOOLEAN active
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    AMENITIES {
        UUID id PK
        VARCHAR name
        TEXT description
        VARCHAR category
        VARCHAR location
        TIME opens_at
        TIME closes_at
        BOOLEAN active
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    AUDIT_LOGS {
        UUID id PK
        UUID user_id FK
        VARCHAR module
        VARCHAR action
        VARCHAR entity_type
        UUID entity_id
        TIMESTAMPTZ occurred_at
        JSONB details
        TIMESTAMPTZ created_at
    }

    PERMISSIONS {
        UUID id PK
        VARCHAR key UK
        VARCHAR name
        TEXT description
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    ROLES {
        UUID id PK
        VARCHAR code UK
        VARCHAR name
        BOOLEAN active
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    ROLE_PERMISSIONS {
        UUID role_id PK,FK
        UUID permission_id PK,FK
    }

    USERS {
        UUID id PK
        VARCHAR first_name
        VARCHAR last_name
        VARCHAR email UK
        VARCHAR password_hash
        UUID role_id FK
        VARCHAR status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
```

## Lectura de cardinalidades

- `||--||`: relacion 1:1.
- `||--o{`: relacion 1:N.
- `||--o|`: relacion 1:0..1.
- `|o--o{`: relacion 0..1:N cuando la FK del lado hijo es opcional.
- Las relaciones N:M estan normalizadas con tablas puente:
  - `ROOM_TYPES` N:M `ROOM_FEATURES` mediante `ROOM_TYPE_FEATURES`.
  - `ROLES` N:M `PERMISSIONS` mediante `ROLE_PERMISSIONS`.

## Notas de integridad del DER

- `BOOKINGS.room_id` y `BOOKINGS.rate_id` son FK opcionales.
- `RATES.valid_to` y `PROMOTIONS.valid_to` pueden ser `NULL`; `NULL` significa sin fecha de finalizacion definida. Cuando existe valor, debe cumplirse `valid_to >= valid_from`.
- `GUEST_ACCOUNTS.booking_id` es `UNIQUE` para representar 1:0..1 con `BOOKINGS`; una reserva puede existir antes de abrir su folio.
- `ORDERS.guest_id`, `ORDERS.room_id`, `ORDERS.charge_id`, `SERVICE_REQUESTS.guest_id`, `SERVICE_REQUESTS.room_id` y `SERVICE_REQUESTS.charge_id` son FK opcionales.
- `CHARGES.created_by_user_id`, `PAYMENTS.processed_by_user_id`, `INVENTORY_MOVEMENTS.responsible_user_id`, `CASH_SESSIONS.closed_by_user_id`, `CASH_MOVEMENTS.responsible_user_id` y `AUDIT_LOGS.user_id` son FK opcionales a `USERS`.
- `INVENTORY_ITEMS.product_id` es opcional porque producto comercial e item de inventario no son la misma entidad.
- `AUDIT_LOGS.entity_id` guarda el UUID interno de la entidad auditada, pero no tiene FK dinamica; se interpreta junto con `entity_type`.
- No existen columnas `is_assignable`, `room_feature_ids` ni `permission_ids`.
- `ORDER_ITEMS` evita guardar `Order.items` como JSON.
- Codigos de negocio como `confirmation_code`, `guest_link_code`, `room_number`, `code`, `sku`, `key` y `email` permanecen como `VARCHAR` con unicidad cuando corresponde.
