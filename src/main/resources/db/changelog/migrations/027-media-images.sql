--liquibase formatted sql

--changeset aurora:027-media-images
-- Imágenes de catálogo (#82). Los archivos viven en el almacenamiento de
-- objetos (S3 o compatible); esta tabla guarda solo metadatos y la asociación.
-- Una imagen pertenece a lo sumo a un registro del destino con el que se subió.
-- Sin dueño queda "pendiente" (pending_since) y el job de limpieza la borra.
-- Las FK no tienen ON DELETE: un registro con imágenes no se borra sin
-- quitarlas antes, así ningún archivo queda sin fila que lo limpie.
CREATE TABLE media_images (
    id UUID PRIMARY KEY,
    target VARCHAR(20) NOT NULL,
    room_type_id UUID REFERENCES room_types(id),
    product_id UUID REFERENCES products(id),
    amenity_id UUID REFERENCES amenities(id),
    position INTEGER,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    alt_text VARCHAR(255),
    original_content_type VARCHAR(50) NOT NULL,
    variant_content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    uploaded_by VARCHAR(255),
    pending_since TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_media_images_target CHECK (target IN ('room_type', 'product', 'amenity')),
    CONSTRAINT chk_media_images_single_owner CHECK (num_nonnulls(room_type_id, product_id, amenity_id) <= 1),
    CONSTRAINT chk_media_images_owner_matches_target CHECK (
        (room_type_id IS NULL OR target = 'room_type')
        AND (product_id IS NULL OR target = 'product')
        AND (amenity_id IS NULL OR target = 'amenity')
    ),
    CONSTRAINT chk_media_images_pending CHECK (
        (num_nonnulls(room_type_id, product_id, amenity_id) = 0) = (pending_since IS NOT NULL)
    ),
    CONSTRAINT chk_media_images_position CHECK (position IS NULL OR position >= 0),
    CONSTRAINT chk_media_images_size CHECK (size_bytes > 0 AND width > 0 AND height > 0)
);

CREATE INDEX idx_media_images_room_type_id ON media_images(room_type_id);
CREATE INDEX idx_media_images_product_id ON media_images(product_id);
CREATE INDEX idx_media_images_amenity_id ON media_images(amenity_id);
CREATE INDEX idx_media_images_pending_since ON media_images(pending_since) WHERE pending_since IS NOT NULL;

--rollback DROP TABLE IF EXISTS media_images;
