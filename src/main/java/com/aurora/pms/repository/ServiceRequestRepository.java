package com.aurora.pms.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

	Optional<ServiceRequest> findByIdAndType(UUID id, ServiceRequestType type);

	List<ServiceRequest> findByTypeAndBookingIdOrderByRequestedAtAscCreatedAtAsc(
			ServiceRequestType type,
			UUID bookingId
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from ServiceRequest r where r.id = :id and r.type = :type")
	Optional<ServiceRequest> findByIdAndTypeForUpdate(@Param("id") UUID id, @Param("type") ServiceRequestType type);

	/** Filtros opcionales: un parámetro null no filtra. */
	@Query("""
			select r from ServiceRequest r
			where r.type = :type
			  and (:bookingId is null or r.booking.id = :bookingId)
			  and (:status is null or r.status = :status)
			order by r.requestedAt asc, r.createdAt asc
			""")
	List<ServiceRequest> search(
			@Param("type") ServiceRequestType type,
			@Param("bookingId") UUID bookingId,
			@Param("status") ServiceRequestStatus status
	);
}
