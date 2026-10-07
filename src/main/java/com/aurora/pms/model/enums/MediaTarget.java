package com.aurora.pms.model.enums;

/**
 * Tipo de registro al que puede asociarse una imagen de catálogo. Se fija al
 * subir la imagen y decide qué permiso hace falta para subirla o leerla.
 */
public enum MediaTarget {
	room_type,
	product,
	amenity,
	inventory_item
}
