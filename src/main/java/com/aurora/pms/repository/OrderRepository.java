package com.aurora.pms.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.Order;
import com.aurora.pms.model.enums.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, UUID> {

	@Query("""
			select o
			from Order o
			where (:bookingId is null or o.booking.id = :bookingId)
			  and (:status is null or o.status = :status)
			order by o.requestedAt desc, o.createdAt desc
			""")
	List<Order> findWithFilters(@Param("bookingId") UUID bookingId, @Param("status") OrderStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Order o where o.id = :id")
	Optional<Order> findByIdForUpdate(@Param("id") UUID id);
}
