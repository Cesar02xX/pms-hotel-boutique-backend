--liquibase formatted sql

--changeset aurora:029-inventory-item-images
ALTER TABLE media_images ADD COLUMN inventory_item_id UUID REFERENCES inventory_items(id);

ALTER TABLE media_images
    DROP CONSTRAINT chk_media_images_target,
    DROP CONSTRAINT chk_media_images_single_owner,
    DROP CONSTRAINT chk_media_images_owner_matches_target,
    DROP CONSTRAINT chk_media_images_pending;

ALTER TABLE media_images
    ADD CONSTRAINT chk_media_images_target
        CHECK (target IN ('room_type', 'product', 'amenity', 'inventory_item')),
    ADD CONSTRAINT chk_media_images_single_owner
        CHECK (num_nonnulls(room_type_id, product_id, amenity_id, inventory_item_id) <= 1),
    ADD CONSTRAINT chk_media_images_owner_matches_target CHECK (
        (room_type_id IS NULL OR target = 'room_type')
        AND (product_id IS NULL OR target = 'product')
        AND (amenity_id IS NULL OR target = 'amenity')
        AND (inventory_item_id IS NULL OR target = 'inventory_item')
    ),
    ADD CONSTRAINT chk_media_images_pending CHECK (
        (num_nonnulls(room_type_id, product_id, amenity_id, inventory_item_id) = 0) = (pending_since IS NOT NULL)
    );

CREATE INDEX idx_media_images_inventory_item_id ON media_images(inventory_item_id);

--rollback DROP INDEX IF EXISTS idx_media_images_inventory_item_id;
--rollback ALTER TABLE media_images DROP CONSTRAINT IF EXISTS chk_media_images_target, DROP CONSTRAINT IF EXISTS chk_media_images_single_owner, DROP CONSTRAINT IF EXISTS chk_media_images_owner_matches_target, DROP CONSTRAINT IF EXISTS chk_media_images_pending;
--rollback ALTER TABLE media_images ADD CONSTRAINT chk_media_images_target CHECK (target IN ('room_type', 'product', 'amenity'));
--rollback ALTER TABLE media_images ADD CONSTRAINT chk_media_images_single_owner CHECK (num_nonnulls(room_type_id, product_id, amenity_id) <= 1);
--rollback ALTER TABLE media_images ADD CONSTRAINT chk_media_images_owner_matches_target CHECK ((room_type_id IS NULL OR target = 'room_type') AND (product_id IS NULL OR target = 'product') AND (amenity_id IS NULL OR target = 'amenity'));
--rollback ALTER TABLE media_images ADD CONSTRAINT chk_media_images_pending CHECK ((num_nonnulls(room_type_id, product_id, amenity_id) = 0) = (pending_since IS NOT NULL));
--rollback ALTER TABLE media_images DROP COLUMN inventory_item_id;
