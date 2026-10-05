package com.aurora.pms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "security.jwt.secret=01234567890123456789012345678901")
class DemoSeedDataTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void demoSeedProvidesOperationalUsersAndGuestAccesses() {
		Integer activeUsers = jdbcTemplate.queryForObject("""
				select count(*)
				from users u
				join roles r on r.id = u.role_id
				where u.email in (
					'admin@aurora.test',
					'recepcion@aurora.test',
					'limpieza@aurora.test',
					'conserjeria@aurora.test',
					'roomservice@aurora.test'
				)
				  and u.status = 'active'
				  and r.code in ('admin', 'reception', 'housekeeping', 'concierge', 'room_service')
				""", Integer.class);
		assertThat(activeUsers).isEqualTo(5);

		Integer legacyUsers = jdbcTemplate.queryForObject("""
				select count(*)
				from users
				where email in (
					'admin.demo@aurora.test',
					'recepcion.demo@aurora.test',
					'limpieza.demo@aurora.test',
					'conserjeria.demo@aurora.test',
					'roomservice.demo@aurora.test',
					'reception@aurora.test',
					'housekeeping@aurora.test',
					'concierge@aurora.test'
				)
				""", Integer.class);
		assertThat(legacyUsers).isZero();

		Integer guestLinks = jdbcTemplate.queryForObject("""
				select count(*)
				from bookings b
				join guests g on g.id = b.guest_id
				join guest_accounts ga on ga.booking_id = b.id
				where b.confirmation_code in ('AUR-DEMO-001', 'AUR-DEMO-002')
				  and b.guest_link_code in ('HUESPED-DEMO-UNO', 'HUESPED-DEMO-DOS')
				  and b.status = 'checked_in'
				  and ga.status = 'open'
				""", Integer.class);
		assertThat(guestLinks).isEqualTo(2);
	}

	@Test
	void demoSeedProvidesCoreOperationalData() {
		Integer roomsAndRates = jdbcTemplate.queryForObject("""
				select count(*)
				from rooms room
				join room_types rt on rt.id = room.room_type_id
				join rates rate on rate.room_type_id = rt.id
				where room.room_number in ('101', '102', '201', '202')
				  and rt.code in ('STD-DEMO', 'DLX-DEMO')
				  and rate.active = true
				""", Integer.class);
		assertThat(roomsAndRates).isGreaterThanOrEqualTo(4);

		Integer productsAndInventory = jdbcTemplate.queryForObject("""
				select count(*)
				from products p
				join inventory_items ii on ii.product_id = p.id
				where p.sku in ('DEMO-WATER', 'DEMO-COFFEE', 'DEMO-SANDWICH')
				  and p.active = true
				""", Integer.class);
		assertThat(productsAndInventory).isEqualTo(3);

		Integer serviceRequests = jdbcTemplate.queryForObject("""
				select count(*)
				from service_requests
				where description in (
					'Limpieza de estancia demo',
					'Reservar cena demo',
					'Revisar aire acondicionado demo'
				)
				""", Integer.class);
		assertThat(serviceRequests).isEqualTo(3);
	}
}
