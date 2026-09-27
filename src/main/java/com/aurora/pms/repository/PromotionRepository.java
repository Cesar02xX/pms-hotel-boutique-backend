package com.aurora.pms.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.Promotion;

public interface PromotionRepository extends JpaRepository<Promotion, UUID> {
}
