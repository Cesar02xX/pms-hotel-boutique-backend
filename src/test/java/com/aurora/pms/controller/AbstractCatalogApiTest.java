package com.aurora.pms.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomFeature;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.RoomStatus;
import com.aurora.pms.repository.RateRepository;
import com.aurora.pms.repository.RoomFeatureRepository;
import com.aurora.pms.repository.RoomRepository;
import com.aurora.pms.repository.RoomTypeFeatureRepository;
import com.aurora.pms.repository.RoomTypeRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Base de los tests de API del catálogo: levanta MockMvc con Spring Security,
 * crea datos de prueba y borra al final todo lo que cada test haya creado.
 */
@SpringBootTest(properties = "security.jwt.secret=01234567890123456789012345678901")
abstract class AbstractCatalogApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	protected RoomRepository roomRepository;

	@Autowired
	protected RoomTypeRepository roomTypeRepository;

	@Autowired
	protected RoomFeatureRepository roomFeatureRepository;

	@Autowired
	protected RoomTypeFeatureRepository roomTypeFeatureRepository;

	@Autowired
	protected RateRepository rateRepository;

	protected MockMvc mockMvc;

	private final List<UUID> rateIds = new ArrayList<>();
	private final List<UUID> roomIds = new ArrayList<>();
	private final List<UUID> roomTypeIds = new ArrayList<>();
	private final List<UUID> roomFeatureIds = new ArrayList<>();

	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	@AfterEach
	void cleanUpCatalogData() {
		rateRepository.deleteAllById(rateIds);
		roomRepository.deleteAllById(roomIds);
		roomTypeIds.forEach(roomTypeId ->
				roomTypeFeatureRepository.deleteAll(roomTypeFeatureRepository.findByIdRoomTypeId(roomTypeId)));
		roomTypeRepository.deleteAllById(roomTypeIds);
		roomFeatureRepository.deleteAllById(roomFeatureIds);
	}

	protected RequestPostProcessor staffUser() {
		return user("catalog.tester@aurora.test");
	}

	protected static String uniqueSuffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	protected static OffsetDateTime now() {
		return OffsetDateTime.now(ZoneOffset.UTC);
	}

	protected RoomFeature createRoomFeature(String name) {
		RoomFeature roomFeature = new RoomFeature();
		roomFeature.setName(name + " " + uniqueSuffix());
		roomFeature.setDescription("Test feature");
		roomFeature.setCreatedAt(now());
		roomFeature.setUpdatedAt(now());
		roomFeature = roomFeatureRepository.save(roomFeature);
		roomFeatureIds.add(roomFeature.getId());
		return roomFeature;
	}

	protected RoomType createRoomType() {
		RoomType roomType = new RoomType();
		roomType.setCode("T-" + uniqueSuffix());
		roomType.setName("Test Room Type");
		roomType.setCapacity(2);
		roomType.setBedConfiguration("1 queen bed");
		roomType.setCreatedAt(now());
		roomType.setUpdatedAt(now());
		roomType = roomTypeRepository.save(roomType);
		roomTypeIds.add(roomType.getId());
		return roomType;
	}

	protected Room createRoom(RoomType roomType) {
		Room room = new Room();
		room.setRoomNumber("T" + uniqueSuffix());
		room.setRoomType(roomType);
		room.setFloor(1);
		room.setStatus(RoomStatus.available);
		room.setHousekeepingStatus(RoomHousekeepingStatus.clean);
		room.setCreatedAt(now());
		room.setUpdatedAt(now());
		room = roomRepository.save(room);
		roomIds.add(room.getId());
		return room;
	}

	protected Rate createRate(RoomType roomType) {
		Rate rate = new Rate();
		rate.setRoomType(roomType);
		rate.setName("Test Rate");
		rate.setValidFrom(LocalDate.of(2026, 1, 1));
		rate.setValidTo(LocalDate.of(2026, 12, 31));
		rate.setPriceCents(45000L);
		rate.setMinimumNights(1);
		rate.setCreatedAt(now());
		rate.setUpdatedAt(now());
		rate = rateRepository.save(rate);
		rateIds.add(rate.getId());
		return rate;
	}

	/** Registra para limpieza un recurso creado vía API y devuelve su id. */
	protected UUID trackCreatedRoom(MvcResult result) throws Exception {
		UUID id = extractId(result);
		roomIds.add(id);
		return id;
	}

	protected UUID trackCreatedRoomType(MvcResult result) throws Exception {
		UUID id = extractId(result);
		roomTypeIds.add(id);
		return id;
	}

	protected UUID trackCreatedRate(MvcResult result) throws Exception {
		UUID id = extractId(result);
		rateIds.add(id);
		return id;
	}

	private static UUID extractId(MvcResult result) throws Exception {
		String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		return UUID.fromString(id);
	}
}
