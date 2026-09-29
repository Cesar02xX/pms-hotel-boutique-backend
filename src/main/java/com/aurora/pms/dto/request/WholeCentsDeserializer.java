package com.aurora.pms.dto.request;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Montos en centavos: solo acepta enteros JSON. Por defecto Jackson trunca
 * 1.5 a 1, lo que registraría en silencio un monto distinto al enviado.
 */
public class WholeCentsDeserializer extends ValueDeserializer<Long> {

	@Override
	public Long deserialize(JsonParser parser, DeserializationContext context) {
		if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
			return (Long) context.handleUnexpectedToken(Long.class, parser);
		}
		return parser.getLongValue();
	}
}
