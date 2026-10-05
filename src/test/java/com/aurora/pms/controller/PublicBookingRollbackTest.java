package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.aurora.pms.dto.request.CreateBookingRequest;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.service.BookingService;

/**
 * Huésped y reserva públicos se crean en una sola transacción: si la reserva
 * falla después de dar de alta al huésped, no queda un huésped huérfano.
 */
class PublicBookingRollbackTest extends AbstractCatalogApiTest {

	@MockitoSpyBean
	private BookingService bookingService;

	private String email;

	@AfterEach
	void removeGuestIfRollbackFailed() {
		if (email != null) {
			guestRepository.findByEmailIgnoreCase(email).ifPresent(guestRepository::delete);
		}
	}

	@Test
	void failedBookingRollsBackTheNewGuest() throws Exception {
		RoomType roomType = createRoomType();
		Rate rate = createRate(roomType);
		LocalDate checkIn = LocalDate.now(ZoneId.of("America/Guatemala")).plusDays(40);
		rate.setValidFrom(checkIn.minusDays(1));
		rate.setValidTo(checkIn.plusDays(10));
		rateRepository.save(rate);
		createRoom(roomType);
		email = "rollback.%s@aurora.test".formatted(uniqueSuffix());
		doThrow(new BadRequestException("Simulated booking failure"))
				.when(bookingService).create(any(CreateBookingRequest.class));

		mockMvc.perform(post("/api/v1/public/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomTypeId": "%s", "checkIn": "%s", "checkOut": "%s", "adults": 1, "children": 0,
								 "guest": {"firstName": "Ana", "lastName": "Lopez", "email": "%s"}}
								""".formatted(roomType.getId(), checkIn, checkIn.plusDays(2), email)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Simulated booking failure"));

		assertThat(guestRepository.findByEmailIgnoreCase(email)).isEmpty();
	}
}
