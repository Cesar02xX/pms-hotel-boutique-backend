# Imágenes de catálogo (#82)

Contrato y configuración para las imágenes de **tipos de habitación**, **productos de Room Service** y **amenidades**. Lo consume la web en el issue #146.

Resumen del diseño:

- Los archivos viven en un almacenamiento de objetos compatible con S3 (AWS S3, Cloudflare R2, DigitalOcean Spaces, RustFS, MinIO). Nunca en PostgreSQL, en el contenedor ni en el repositorio.
- PostgreSQL guarda solo metadatos y la asociación (`media_images`, migración `027-media-images.sql`).
- El bucket es **privado**. La API lee los objetos y los sirve; nunca entrega credenciales ni URLs del proveedor.
- Flujo en dos pasos: primero se **sube** la imagen y queda pendiente; después su `id` se envía en el create/update del registro.
- El proveedor se elige por configuración (`PMS_MEDIA_STORAGE_PROVIDER`). Los módulos dependen solo de `ObjectStoragePort`.

## Límites

| Regla | Valor | Variable |
| --- | --- | --- |
| Formatos admitidos | JPEG, PNG, WebP (validados por su contenido, no por la extensión ni el `Content-Type`) | — |
| Tamaño máximo por archivo | 5 MB | `PMS_MEDIA_MAX_FILE_SIZE` |
| Imágenes por registro | 10 | `PMS_MEDIA_MAX_IMAGES_PER_RECORD` |
| Píxeles máximos | 24 000 000 (se revisa leyendo solo la cabecera, antes de decodificar) | `pms.media.max-pixels` |
| Vida de una imagen pendiente | 24 h | `PMS_MEDIA_PENDING_TTL` (ISO-8601, p. ej. `PT24H`) |
| Tope duro de cualquier multipart | 10 MB | `spring.servlet.multipart.max-file-size` |

Variantes generadas al subir (el lado mayor; una imagen más chica no se agranda):

| Variante | Lado mayor | Uso sugerido |
| --- | --- | --- |
| `thumb` | 320 px | listas, tarjetas, menú |
| `medium` | 960 px | detalle, galería |
| `large` | 1600 px | vista ampliada |

Las variantes se vuelven a codificar en **JPEG** (calidad 0,85), o en **PNG** si la imagen tiene transparencia. Así se descarta el EXIF (ubicación GPS, datos del teléfono) y se aplica la orientación de la cámara. Se aceptan subidas en WebP, pero no se generan variantes WebP porque Java no trae codificador y uno nativo complicaría Docker y Jenkins. El original se guarda, pero nunca se sirve.

## Permisos

| Acción | Permiso |
| --- | --- |
| Subir o borrar imagen `room_type` | `room-types.write` o `ROLE_ADMIN` |
| Subir o borrar imagen `product` | `room-service.write` o `ROLE_ADMIN` |
| Subir o borrar imagen `amenity` | `rooms.write` o `ROLE_ADMIN` (el mismo que protege `PUT /admin/amenities`) |
| Ver cualquier imagen como personal | el permiso `.read` o `.write` del destino |
| Ver una imagen publicada | ninguno |

No se agregó un permiso nuevo. `POST /api/v1/media` revisa el permiso según el `target` (`MediaAccessPolicy`).

Una imagen es **pública** solo si está asociada a un registro **activo**. Una imagen pendiente, o la de un registro desactivado, responde `404` en la ruta pública.

## Contrato HTTP

### Subir imagen

```http
POST /api/v1/media
Authorization: Bearer <token>
Content-Type: multipart/form-data

file=<archivo>
target=room_type | product | amenity
```

Respuesta `201 Created`:

```json
{
  "id": "6f1c...-uuid",
  "target": "room_type",
  "contentType": "image/jpeg",
  "sizeBytes": 734512,
  "width": 4032,
  "height": 3024,
  "urls": {
    "thumb": "https://api.example.com/api/v1/public/media/6f1c.../thumb",
    "medium": "https://api.example.com/api/v1/public/media/6f1c.../medium",
    "large": "https://api.example.com/api/v1/public/media/6f1c.../large"
  },
  "expiresAt": "2026-10-07T18:00:00Z"
}
```

`contentType` es el de las variantes que se sirven. `expiresAt` indica cuándo se borra si nadie la asocia. Mientras está pendiente, sus `urls` responden `404`: para la vista previa la web usa el archivo local (`URL.createObjectURL`).

Errores (formato `ApiErrorResponse`):

| Estado | Cuándo |
| --- | --- |
| `400` | Falta `file` o `target`, `target` inválido, archivo vacío, corrupto o con demasiados píxeles |
| `401` | Sin token o token inválido |
| `403` | Sin permiso para ese `target` |
| `413` | Supera el tamaño máximo |
| `415` | No es JPEG, PNG ni WebP |
| `503` | El almacenamiento no respondió; no se guardó nada y se puede reintentar |

### Asociar, ordenar y quitar

Los create/update existentes aceptan un campo opcional `images`:

- `POST /api/v1/room-types`, `PUT /api/v1/room-types/{id}`
- `POST /api/v1/admin/room-service/products`, `PUT /api/v1/admin/room-service/products/{id}`
- `POST /api/v1/admin/amenities`, `PUT /api/v1/admin/amenities/{id}`

```json
{
  "images": [
    { "mediaId": "uuid-1", "altText": "Cama king con vista al jardín" },
    { "mediaId": "uuid-2", "altText": "Baño", "primary": true }
  ]
}
```

Reglas:

- El orden de la lista es el orden de la galería (`position` 0, 1, 2…).
- A lo sumo una con `primary: true`. Si ninguna la trae, la primera es la principal.
- `images` ausente o `null` **no cambia nada**; así los clientes que no envían el campo siguen funcionando. `[]` quita todas.
- Una imagen que sale de la lista queda pendiente: deja de ser pública al instante y la limpieza la borra al vencer el plazo.
- Errores: `400` si un `mediaId` no existe, está repetido, hay más de una principal, se supera el máximo o la imagen se subió para otro `target`. `409` si ya pertenece a otro registro.

### Lectura

Las respuestas de tipo de habitación, producto y amenidad (admin, huésped y públicas) incluyen `images`, ordenadas por `position`:

```json
"images": [
  {
    "id": "uuid-2",
    "altText": "Baño",
    "position": 0,
    "primary": true,
    "width": 4032,
    "height": 3024,
    "urls": { "thumb": "…/thumb", "medium": "…/medium", "large": "…/large" }
  }
]
```

Un registro sin imágenes devuelve `"images": []`. La web muestra un placeholder neutral, no fotos de otro registro.

Endpoints que las exponen:

- `GET /api/v1/room-types`, `GET /api/v1/room-types/{id}`
- `GET /api/v1/public/room-types` (sin sesión)
- `GET /api/v1/admin/room-service/products`, `GET /api/v1/room-service/products`, `GET /api/v1/guest/room-service/products`
- `GET /api/v1/admin/amenities`, `GET /api/v1/guest/amenities`
- **Nuevo:** `GET /api/v1/public/amenities` (sin sesión; solo amenidades activas, sin timestamps internos)

### Servir imágenes

```http
GET /api/v1/public/media/{id}/{thumb|medium|large}
```

Sin sesión. Responde `200` con los bytes, `Cache-Control: max-age=3600, public`, `ETag` y `X-Content-Type-Options: nosniff`, o `404` si la imagen no existe o no es pública. El caché es corto porque una imagen deja de ser pública al quitarla o al desactivar su registro. Se puede poner un CDN delante apuntando `PMS_MEDIA_PUBLIC_BASE_URL` a su dominio.

```http
GET /api/v1/media/{id}/content/{thumb|medium|large}
Authorization: Bearer <token>
```

Para el personal: sirve también imágenes pendientes o de registros inactivos (`Cache-Control: private, no-store`). Como lleva token, la web la descarga con `fetch` y la muestra con un `blob:`.

### Borrar una imagen pendiente

```http
DELETE /api/v1/media/{id}
```

`204` si estaba pendiente. `409` si está asociada: se quita desde su registro con `images`.

## Limpieza de huérfanos

`MediaCleanupJob` corre cada `PMS_MEDIA_CLEANUP_INTERVAL` (por defecto `PT1H`, primera ejecución 5 min después de arrancar). Borra las imágenes pendientes más viejas que `PMS_MEDIA_PENDING_TTL`, en lotes de 100.

Cada imagen se borra en su propia transacción: primero la fila (solo si sigue pendiente; la fila queda bloqueada, así que una asociación concurrente no la puede tomar) y después los objetos. Si el almacenamiento falla, la fila se restaura y la siguiente ejecución lo reintenta. Así nunca queda una fila apuntando a un archivo borrado ni un archivo sin fila que lo limpie.

Si en producción corre más de una instancia, conviene dejar la limpieza en una sola con `PMS_MEDIA_CLEANUP_ENABLED=false` en las demás. Correr varias no rompe nada, pero repite trabajo.

Las FK de `media_images` no tienen `ON DELETE`: un tipo de habitación, producto o amenidad con imágenes no se puede borrar sin quitarlas antes. La API no borra estos registros, solo los desactiva.

## Desarrollo local

`docker-compose.yml` levanta [RustFS](https://github.com/rustfs/rustfs) `1.0.1`, un almacenamiento S3 compatible con licencia Apache-2.0 (API en `localhost:9100`, consola en `http://localhost:9101`) y el servicio `object-storage-init` crea el bucket privado `pms-media` con la CLI oficial de AWS. Se usa RustFS porque MinIO dejó de publicar imágenes de Docker. Los valores por defecto de `application.yaml` ya apuntan ahí; no hace falta tocar `.env`.

```bash
docker compose up -d
./mvnw spring-boot:run
```

Los puertos evitan el `9000` de SonarQube. Los datos persisten en el volumen `pms_object_storage_data`. Usuario y contraseña de la consola: `PMS_MEDIA_S3_ACCESS_KEY` / `PMS_MEDIA_S3_SECRET_KEY` (por defecto `pms_storage` / `pms_storage_password`, solo para desarrollo). Los puertos se cambian con `OBJECT_STORAGE_API_PORT` y `OBJECT_STORAGE_CONSOLE_PORT`.

Las pruebas (`./mvnw test`) no necesitan el almacenamiento local: `MediaApiTest` reemplaza el almacenamiento por uno en memoria.

## Producción

### Variables de entorno

| Variable | Obligatoria | Ejemplo / valor por defecto | Descripción |
| --- | --- | --- | --- |
| `PMS_MEDIA_PUBLIC_BASE_URL` | Sí | `https://api.hotel-aurora.com` o `https://cdn.hotel-aurora.com` | Origen con el que se arman las URLs públicas (la API o un CDN delante de ella). Sin `/` final. |
| `PMS_MEDIA_STORAGE_PROVIDER` | No | `s3` | Implementación de `ObjectStoragePort`. |
| `PMS_MEDIA_S3_BUCKET` | Sí | `aurora-pms-media` | Bucket privado. |
| `PMS_MEDIA_S3_REGION` | Sí | `us-east-1` | Región del bucket. En R2 se usa `auto`. |
| `PMS_MEDIA_S3_ENDPOINT` | Según proveedor | vacío en AWS; `https://<account>.r2.cloudflarestorage.com` en R2; `https://<región>.digitaloceanspaces.com` en Spaces | **Dejar vacío en AWS S3**: el valor por defecto apunta al almacenamiento local. |
| `PMS_MEDIA_S3_PATH_STYLE` | Según proveedor | `false` en AWS, R2 y Spaces; `true` en RustFS y MinIO | Rutas `/bucket/clave` en vez de subdominios. |
| `PMS_MEDIA_S3_ACCESS_KEY` / `PMS_MEDIA_S3_SECRET_KEY` | Según proveedor | — | **Dejar vacías en AWS con rol IAM** (ECS, EC2, EKS): se usa la cadena de credenciales por defecto. Si se definen, van como secretos del entorno, nunca en el repositorio. |
| `PMS_MEDIA_S3_KEY_PREFIX` | No | `media` | Prefijo de las claves en el bucket. |
| `PMS_MEDIA_MAX_FILE_SIZE` | No | `5MB` | No debe superar los 10 MB del tope multipart. |
| `PMS_MEDIA_MAX_IMAGES_PER_RECORD` | No | `10` | |
| `PMS_MEDIA_PENDING_TTL` | No | `PT24H` | |
| `PMS_MEDIA_CLEANUP_ENABLED` | No | `true` | |
| `PMS_MEDIA_CLEANUP_INTERVAL` | No | `PT1H` | |

> Importante: los valores por defecto de endpoint, path-style y credenciales son los del almacenamiento local de desarrollo. En producción hay que definir explícitamente `PMS_MEDIA_S3_ENDPOINT` (vacío en AWS), `PMS_MEDIA_S3_PATH_STYLE` y las credenciales (o vaciarlas para usar el rol IAM).

### Bucket

- Acceso público **bloqueado** (AWS: *Block all public access* activado). La API es la única que lee.
- Sin ACL públicas ni política de lectura anónima.
- Cifrado en reposo del proveedor (SSE-S3 en AWS) activado.
- Opcional: una regla de ciclo de vida que borre objetos bajo `media/` sin fila en la base no es necesaria, porque la limpieza ya lo hace.

### Permisos mínimos (AWS IAM)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::aurora-pms-media/media/*"
    }
  ]
}
```

No hace falta `s3:ListBucket`, `s3:PutObjectAcl` ni permisos sobre otros buckets. En R2 o Spaces se usa un token limitado a lectura y escritura de objetos de ese bucket.

### CDN (opcional)

Para no pasar cada imagen por la API en cada visita, se puede poner un CDN (CloudFront, Cloudflare) **delante de la API**, con origen en `/api/v1/public/media/*`, respetando `Cache-Control` del origen. Después se apunta `PMS_MEDIA_PUBLIC_BASE_URL` al dominio del CDN. No se recomienda servir el bucket directo por CDN: perdería la regla de "solo imágenes de registros activos".
