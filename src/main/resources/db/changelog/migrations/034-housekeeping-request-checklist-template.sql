--liquibase formatted sql

--changeset aurora:034-housekeeping-request-checklist-template
CREATE TABLE housekeeping_checklist_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE housekeeping_checklist_template_items (
    template_id UUID NOT NULL REFERENCES housekeeping_checklist_templates(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    label TEXT NOT NULL,
    PRIMARY KEY (template_id, position)
);

INSERT INTO housekeeping_checklist_templates (code, name)
VALUES ('guest-cleaning', 'Limpieza de habitación');

INSERT INTO housekeeping_checklist_template_items (template_id, position, label)
SELECT template.id, item.position, item.label
FROM housekeeping_checklist_templates template
CROSS JOIN (VALUES
    (0, 'Retirar basura y ropa usada que corresponda'),
    (1, 'Hacer la cama y revisar la ropa de cama'),
    (2, 'Limpiar y desinfectar superficies y puntos de contacto'),
    (3, 'Limpiar baño: lavabo, inodoro, ducha y espejos'),
    (4, 'Reponer toallas, papel y amenidades según corresponda'),
    (5, 'Barrer o aspirar y trapear el piso'),
    (6, 'Hacer una revisión final y reportar cualquier desperfecto')
) AS item(position, label)
WHERE template.code = 'guest-cleaning';

--rollback DROP TABLE IF EXISTS housekeeping_checklist_template_items;
--rollback DROP TABLE IF EXISTS housekeeping_checklist_templates;
