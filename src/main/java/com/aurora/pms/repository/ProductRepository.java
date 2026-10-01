package com.aurora.pms.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.Product;
import com.aurora.pms.model.enums.ProductCategory;

public interface ProductRepository extends JpaRepository<Product, UUID> {

	List<Product> findByActiveTrueOrderByNameAsc();

	List<Product> findByActiveTrueAndCategoryOrderByNameAsc(ProductCategory category);

	Optional<Product> findByIdAndActiveTrue(UUID id);

	boolean existsBySkuIgnoreCase(String sku);
}
