package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.UpsertAmenityRequest;
import com.aurora.pms.dto.request.UpsertInventoryItemRequest;
import com.aurora.pms.dto.request.UpsertProductRequest;
import com.aurora.pms.dto.request.UpsertPromotionRequest;
import com.aurora.pms.dto.response.AmenityResponse;
import com.aurora.pms.dto.response.InventoryItemResponse;
import com.aurora.pms.dto.response.MediaImageResponse;
import com.aurora.pms.dto.response.PromotionResponse;
import com.aurora.pms.dto.response.PublicAmenityResponse;
import com.aurora.pms.dto.response.RoomServiceProductResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.InventoryMapper;
import com.aurora.pms.mapper.RoomServiceMapper;
import com.aurora.pms.model.Amenity;
import com.aurora.pms.model.InventoryItem;
import com.aurora.pms.model.Product;
import com.aurora.pms.model.Promotion;
import com.aurora.pms.model.enums.MediaTarget;
import com.aurora.pms.repository.AmenityRepository;
import com.aurora.pms.repository.InventoryItemRepository;
import com.aurora.pms.repository.ProductRepository;
import com.aurora.pms.repository.PromotionRepository;
import com.aurora.pms.service.AdminCatalogService;
import com.aurora.pms.service.MediaImageService;

@Service
public class AdminCatalogServiceImpl implements AdminCatalogService {

	private final AmenityRepository amenityRepository;
	private final ProductRepository productRepository;
	private final InventoryItemRepository inventoryItemRepository;
	private final PromotionRepository promotionRepository;
	private final RoomServiceMapper roomServiceMapper;
	private final InventoryMapper inventoryMapper;
	private final MediaImageService mediaImageService;

	public AdminCatalogServiceImpl(
			AmenityRepository amenityRepository,
			ProductRepository productRepository,
			InventoryItemRepository inventoryItemRepository,
			PromotionRepository promotionRepository,
			RoomServiceMapper roomServiceMapper,
			InventoryMapper inventoryMapper,
			MediaImageService mediaImageService
	) {
		this.amenityRepository = amenityRepository;
		this.productRepository = productRepository;
		this.inventoryItemRepository = inventoryItemRepository;
		this.promotionRepository = promotionRepository;
		this.roomServiceMapper = roomServiceMapper;
		this.inventoryMapper = inventoryMapper;
		this.mediaImageService = mediaImageService;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AmenityResponse> findAmenities(Boolean active) {
		List<Amenity> amenities = findSortedAmenities(active);
		Map<UUID, List<MediaImageResponse>> images = mediaImageService.findImages(
				MediaTarget.amenity,
				amenities.stream().map(Amenity::getId).toList()
		);
		return amenities.stream()
				.map(amenity -> toAmenityResponse(amenity, images.getOrDefault(amenity.getId(), List.of())))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<PublicAmenityResponse> findPublicAmenities() {
		List<Amenity> amenities = findSortedAmenities(true);
		Map<UUID, List<MediaImageResponse>> images = mediaImageService.findImages(
				MediaTarget.amenity,
				amenities.stream().map(Amenity::getId).toList()
		);
		return amenities.stream()
				.map(amenity -> new PublicAmenityResponse(amenity.getId(), amenity.getName(),
						amenity.getDescription(), amenity.getCategory(), amenity.getLocation(), amenity.getOpensAt(),
						amenity.getClosesAt(), images.getOrDefault(amenity.getId(), List.of())))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public AmenityResponse findAmenity(UUID id) {
		return toAmenityResponse(getAmenity(id));
	}

	@Override
	@Transactional
	public AmenityResponse createAmenity(UpsertAmenityRequest request) {
		Amenity amenity = new Amenity();
		OffsetDateTime now = OffsetDateTime.now();
		amenity.setCreatedAt(now);
		applyAmenity(amenity, request, now);
		Amenity saved = amenityRepository.save(amenity);
		mediaImageService.replaceImages(MediaTarget.amenity, saved.getId(), request.images());
		return toAmenityResponse(saved);
	}

	@Override
	@Transactional
	public AmenityResponse updateAmenity(UUID id, UpsertAmenityRequest request) {
		Amenity amenity = getAmenity(id);
		applyAmenity(amenity, request, OffsetDateTime.now());
		Amenity saved = amenityRepository.save(amenity);
		mediaImageService.replaceImages(MediaTarget.amenity, id, request.images());
		return toAmenityResponse(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoomServiceProductResponse> findProducts(Boolean active) {
		List<Product> products = productRepository.findAll().stream()
				.filter(product -> active == null || active.equals(product.getActive()))
				.sorted(Comparator.comparing(Product::getName))
				.toList();
		Map<UUID, List<MediaImageResponse>> images = mediaImageService.findImages(
				MediaTarget.product,
				products.stream().map(Product::getId).toList()
		);
		List<UUID> productIds = products.stream().map(Product::getId).toList();
		Map<UUID, List<InventoryItem>> inventoryByProduct = productIds.isEmpty()
				? Map.of()
				: inventoryItemRepository.findActiveByProductIdIn(productIds).stream()
						.collect(java.util.stream.Collectors.groupingBy(item -> item.getProduct().getId()));
		return products.stream()
				.map(product -> roomServiceMapper.toProductResponse(
						product,
						stockQuantity(inventoryByProduct.get(product.getId())),
						images.getOrDefault(product.getId(), List.of())
				))
				.toList();
	}

	@Override
	@Transactional
	public RoomServiceProductResponse createProduct(UpsertProductRequest request) {
		if (productRepository.existsBySkuIgnoreCase(request.sku())) {
			throw new ConflictException("Product SKU already exists: " + request.sku());
		}
		Product product = new Product();
		product.setStockQuantity(0);
		OffsetDateTime now = OffsetDateTime.now();
		product.setCreatedAt(now);
		applyProduct(product, request, now);
		Product saved = productRepository.save(product);
		mediaImageService.replaceImages(MediaTarget.product, saved.getId(), request.images());
		return toProductResponse(saved);
	}

	@Override
	@Transactional
	public RoomServiceProductResponse updateProduct(UUID id, UpsertProductRequest request) {
		Product product = productRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
		applyProduct(product, request, OffsetDateTime.now());
		Product saved = productRepository.save(product);
		mediaImageService.replaceImages(MediaTarget.product, id, request.images());
		return toProductResponse(saved);
	}

	@Override
	@Transactional
	public InventoryItemResponse createInventoryItem(UpsertInventoryItemRequest request) {
		InventoryItem item = new InventoryItem();
		item.setCurrentQuantity(0);
		OffsetDateTime now = OffsetDateTime.now();
		item.setCreatedAt(now);
		applyInventoryItem(item, request, now);
		InventoryItem saved = inventoryItemRepository.save(item);
		mediaImageService.replaceImages(MediaTarget.inventory_item, saved.getId(), request.images());
		return toInventoryItemResponse(saved);
	}

	@Override
	@Transactional
	public InventoryItemResponse updateInventoryItem(UUID id, UpsertInventoryItemRequest request) {
		InventoryItem item = inventoryItemRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + id));
		applyInventoryItem(item, request, OffsetDateTime.now());
		InventoryItem saved = inventoryItemRepository.save(item);
		mediaImageService.replaceImages(MediaTarget.inventory_item, id, request.images());
		return toInventoryItemResponse(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public List<PromotionResponse> findPromotions(Boolean active) {
		return promotionRepository.findAll().stream()
				.filter(promotion -> active == null || active.equals(promotion.getActive()))
				.sorted(Comparator.comparing(Promotion::getValidFrom).reversed())
				.map(this::toPromotionResponse)
				.toList();
	}

	@Override
	@Transactional
	public PromotionResponse createPromotion(UpsertPromotionRequest request) {
		Promotion promotion = new Promotion();
		OffsetDateTime now = OffsetDateTime.now();
		promotion.setCreatedAt(now);
		applyPromotion(promotion, request, now);
		return toPromotionResponse(promotionRepository.save(promotion));
	}

	@Override
	@Transactional
	public PromotionResponse updatePromotion(UUID id, UpsertPromotionRequest request) {
		Promotion promotion = promotionRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + id));
		applyPromotion(promotion, request, OffsetDateTime.now());
		return toPromotionResponse(promotionRepository.save(promotion));
	}

	private void applyAmenity(Amenity amenity, UpsertAmenityRequest request, OffsetDateTime now) {
		if (request.opensAt() != null && request.closesAt() != null && !request.opensAt().isBefore(request.closesAt())) {
			throw new BadRequestException("Amenity opensAt must be before closesAt");
		}
		amenity.setName(request.name().trim());
		amenity.setDescription(trimToNull(request.description()));
		amenity.setCategory(request.category());
		amenity.setLocation(trimToNull(request.location()));
		amenity.setOpensAt(request.opensAt());
		amenity.setClosesAt(request.closesAt());
		amenity.setActive(request.active() == null || request.active());
		amenity.setUpdatedAt(now);
	}

	private void applyProduct(Product product, UpsertProductRequest request, OffsetDateTime now) {
		product.setSku(request.sku().trim().toUpperCase());
		product.setName(request.name().trim());
		product.setDescription(trimToNull(request.description()));
		product.setCategory(request.category());
		product.setPriceCents(request.priceCents());
		// Sin reorderLevel en el request se conserva el actual (0 en un producto nuevo):
		// la respuesta no lo expone, así que un cliente que edita no puede reenviarlo (#84).
		if (request.reorderLevel() != null) {
			product.setReorderLevel(request.reorderLevel());
		}
		product.setActive(request.active() == null || request.active());
		product.setUpdatedAt(now);
	}

	private void applyInventoryItem(InventoryItem item, UpsertInventoryItemRequest request, OffsetDateTime now) {
		if (request.sku() != null && !request.sku().isBlank()) {
			item.setSku(request.sku().trim().toUpperCase());
		} else if (item.getSku() == null) {
			item.setSku("INV-" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
		}
		item.setName(request.name().trim());
		item.setDescription(trimToNull(request.description()));
		item.setCategory(request.category().trim());
		item.setUnit(request.unit().trim());
		item.setMinimumQuantity(request.minimumQuantity() == null ? 0 : request.minimumQuantity());
		item.setProduct(request.productId() == null ? null : productRepository.findById(request.productId())
				.orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.productId())));
		item.setActive(request.active() == null || request.active());
		item.setUpdatedAt(now);
	}

	private void applyPromotion(Promotion promotion, UpsertPromotionRequest request, OffsetDateTime now) {
		if (request.validTo() != null && request.validTo().isBefore(request.validFrom())) {
			throw new BadRequestException("Promotion validTo must not be before validFrom");
		}
		promotion.setCode(request.code().trim().toUpperCase());
		promotion.setName(request.name().trim());
		promotion.setDescription(trimToNull(request.description()));
		promotion.setDiscountPercent(request.discountPercent());
		promotion.setValidFrom(request.validFrom());
		promotion.setValidTo(request.validTo());
		promotion.setActive(request.active() == null || request.active());
		promotion.setUpdatedAt(now);
	}

	private Amenity getAmenity(UUID id) {
		return amenityRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Amenity not found: " + id));
	}

	private List<Amenity> findSortedAmenities(Boolean active) {
		return amenityRepository.findAll().stream()
				.filter(amenity -> active == null || active.equals(amenity.getActive()))
				.sorted(Comparator.comparing(Amenity::getName))
				.toList();
	}

	private AmenityResponse toAmenityResponse(Amenity amenity) {
		return toAmenityResponse(amenity, mediaImageService.findImages(MediaTarget.amenity, amenity.getId()));
	}

	private AmenityResponse toAmenityResponse(Amenity amenity, List<MediaImageResponse> images) {
		return new AmenityResponse(amenity.getId(), amenity.getName(), amenity.getDescription(), amenity.getCategory(),
				amenity.getLocation(), amenity.getOpensAt(), amenity.getClosesAt(), amenity.getActive(),
				amenity.getCreatedAt(), amenity.getUpdatedAt(), List.copyOf(images));
	}

	private InventoryItemResponse toInventoryItemResponse(InventoryItem item) {
		return inventoryMapper.toResponse(
				item,
				mediaImageService.findImages(MediaTarget.inventory_item, item.getId())
		);
	}

	private RoomServiceProductResponse toProductResponse(Product product) {
		List<InventoryItem> inventoryItems = inventoryItemRepository.findActiveByProductIdIn(List.of(product.getId()));
		return roomServiceMapper.toProductResponse(
				product,
				stockQuantity(inventoryItems),
				mediaImageService.findImages(MediaTarget.product, product.getId())
		);
	}

	private static int stockQuantity(List<InventoryItem> items) {
		return items != null && items.size() == 1 ? items.get(0).getCurrentQuantity() : 0;
	}

	private PromotionResponse toPromotionResponse(Promotion promotion) {
		return new PromotionResponse(promotion.getId(), promotion.getCode(), promotion.getName(),
				promotion.getDescription(), promotion.getDiscountPercent(), promotion.getValidFrom(),
				promotion.getValidTo(), promotion.getActive(), promotion.getCreatedAt(), promotion.getUpdatedAt());
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
