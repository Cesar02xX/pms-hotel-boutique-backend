# Housekeeping Checklists Contract

Contrato backend para persistir checklists reales de limpieza. Todos los endpoints requieren JWT y permisos de housekeeping:

- Lectura: `housekeeping.read` o `ROLE_ADMIN`
- Escritura: `housekeeping.write` o `ROLE_ADMIN`

## Listar checklists

```http
GET /api/v1/housekeeping/checklists?roomId={uuid}&status={status}&responsibleUserId={uuid}
```

Filtros opcionales:

- `roomId`: habitacion asociada al checklist.
- `status`: `pending`, `in_progress`, `completed`, `cancelled`.
- `responsibleUserId`: usuario responsable del checklist.

Respuesta `200 OK`:

```json
[
  {
    "id": "uuid",
    "serviceRequestId": "uuid",
    "roomId": "uuid",
    "roomNumber": "201",
    "status": "pending",
    "observations": "Primera pasada",
    "responsibleUserEmail": "limpieza.demo@aurora.test",
    "completedByUserEmail": null,
    "startedAt": null,
    "completedAt": null,
    "createdAt": "2026-10-05T12:00:00Z",
    "updatedAt": "2026-10-05T12:00:00Z",
    "items": [
      {
        "id": "uuid",
        "label": "Cambiar sabanas",
        "checked": false,
        "position": 0,
        "notes": null,
        "checkedByUserEmail": null,
        "checkedAt": null,
        "createdAt": "2026-10-05T12:00:00Z",
        "updatedAt": "2026-10-05T12:00:00Z"
      }
    ]
  }
]
```

## Crear checklist

```http
POST /api/v1/housekeeping/checklists
```

El `serviceRequestId` debe pertenecer a una solicitud real `housekeeping` y no puede tener otro checklist asociado.

```json
{
  "serviceRequestId": "uuid",
  "observations": "Primera pasada",
  "items": [
    {"label": "Cambiar sabanas", "checked": true, "notes": "Listo"},
    {"label": "Reponer toallas"}
  ]
}
```

Respuesta `201 Created`: mismo formato de checklist. El responsable se registra desde el usuario autenticado.

## Actualizar checklist

```http
PUT /api/v1/housekeeping/checklists/{id}
```

```json
{
  "status": "completed",
  "observations": "Habitacion lista",
  "items": [
    {"id": "uuid", "label": "Cambiar sabanas", "checked": true, "notes": "OK"},
    {"label": "Desinfectar bano", "checked": true}
  ]
}
```

Reglas:

- Los items enviados con `id` se actualizan.
- Los items enviados sin `id` se agregan.
- Los items omitidos se conservan para no perder historial basico.
- Para pasar a `completed`, todos los items existentes deben estar marcados como `checked`.
- `completed` y `cancelled` son estados terminales.

Errores relevantes:

- `400 Bad Request`: UUID, enum o body invalido.
- `401 Unauthorized`: sin token o token invalido.
- `403 Forbidden`: token valido sin permiso requerido.
- `404 Not Found`: habitacion, responsable, solicitud o checklist inexistente.
- `409 Conflict`: checklist duplicado o transicion/estado inconsistente.
