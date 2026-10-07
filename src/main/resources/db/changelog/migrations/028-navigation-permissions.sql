--liquibase formatted sql

--changeset aurora:028-navigation-permissions
INSERT INTO permissions (key, name, description)
SELECT permission_key, permission_key, 'Persisted navigation visibility for role settings'
FROM (VALUES
    ('navigation.configured'),
    ('navigation.administracion.dashboard'),
    ('navigation.administracion.usuarios-y-roles'),
    ('navigation.administracion.habitaciones'),
    ('navigation.administracion.tarifas'),
    ('navigation.administracion.promociones'),
    ('navigation.administracion.servicios'),
    ('navigation.administracion.reportes'),
    ('navigation.administracion.inventario'),
    ('navigation.administracion.caja'),
    ('navigation.administracion.auditoria'),
    ('navigation.recepcion.resumen'),
    ('navigation.recepcion.calendario'),
    ('navigation.recepcion.reservas'),
    ('navigation.recepcion.huespedes'),
    ('navigation.recepcion.disponibilidad'),
    ('navigation.recepcion.habitaciones'),
    ('navigation.recepcion.caja'),
    ('navigation.limpieza.inicio'),
    ('navigation.limpieza.habitaciones'),
    ('navigation.limpieza.solicitudes'),
    ('navigation.limpieza.historial'),
    ('navigation.room-service.pedidos-activos'),
    ('navigation.room-service.menu'),
    ('navigation.room-service.historial'),
    ('navigation.room-service.inventario'),
    ('navigation.conserjeria.solicitudes'),
    ('navigation.conserjeria.por-habitacion'),
    ('navigation.conserjeria.historial'),
    ('navigation.huesped.inicio'),
    ('navigation.huesped.mis-reservas'),
    ('navigation.huesped.mi-estancia'),
    ('navigation.huesped.amenidades'),
    ('navigation.huesped.conserjeria'),
    ('navigation.huesped.servicios-de-habitacion'),
    ('navigation.huesped.room-service'),
    ('navigation.huesped.mis-solicitudes-y-pedidos'),
    ('navigation.huesped.notificaciones')
) AS navigation_permissions(permission_key)
ON CONFLICT (key) DO NOTHING;
