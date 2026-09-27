package com.aurora.pms.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.CashSession;

public interface CashSessionRepository extends JpaRepository<CashSession, UUID> {
}
