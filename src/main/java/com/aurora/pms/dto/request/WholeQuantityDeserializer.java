package com.aurora.pms.dto.request;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Cantidades en unidades enteras: solo acepta enteros JSON dentro del rango
 * de Integer. Por defecto Jackson trunca 1.5 a 1, lo que movería en silencio
 * una cantidad distinta a la enviada.
 */
public class WholeQuantityDeserializer extends ValueDeserializer<Integer> {

	@Override
	public Integer deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
				|| parser.getNumberType() != JsonParser.NumberType.INT) {
			return (Integer) context.handleUnexpectedToken(Integer.class, parser);
		}
		return parser.getIntValue();
	}
}
