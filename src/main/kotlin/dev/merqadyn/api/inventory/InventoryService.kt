package dev.merqadyn.api.inventory

import dev.merqadyn.api.api.BusinessRuleException
import dev.merqadyn.api.api.InventoryView
import dev.merqadyn.api.api.PageView
import dev.merqadyn.api.api.NotFoundException
import dev.merqadyn.api.catalog.ProductRepository
import dev.merqadyn.api.merchant.LocationRepository
import org.springframework.stereotype.Service
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class InventoryService(
    private val inventoryRepository: InventoryItemRepository,
    private val stockMovementRepository: StockMovementRepository,
    private val productRepository: ProductRepository,
    private val locationRepository: LocationRepository,
) {
    @Transactional(readOnly = true)
    fun list(merchantId: UUID, locationId: UUID? = null): List<InventoryView> {
        if (locationId == null) return page(merchantId, 0, 200).items
        val products = productRepository.findAllByMerchantIdOrderByName(merchantId).associateBy { it.id }
        val locations = locationRepository.findAllByMerchantIdOrderByName(merchantId).associateBy { it.id }
        return inventoryRepository.findTop200ByMerchantIdAndLocationIdOrderByUpdatedAtDesc(merchantId, locationId)
            .asSequence()
            .mapNotNull { item ->
                val product = products[item.productId] ?: return@mapNotNull null
                val location = locations[item.locationId] ?: return@mapNotNull null
                InventoryView(
                    id = item.id,
                    locationId = location.id,
                    locationCode = location.code,
                    locationName = location.name,
                    productId = product.id,
                    sku = product.sku,
                    productName = product.name,
                    onHand = item.onHand,
                    reserved = item.reserved,
                    available = item.onHand.subtract(item.reserved),
                    unit = product.unit,
                    version = item.version,
                    updatedAt = item.updatedAt,
                )
            }
            .sortedWith(compareBy<InventoryView> { it.locationName }.thenBy { it.productName })
            .toList()
    }

    @Transactional(readOnly = true)
    fun page(merchantId: UUID, page: Int, size: Int): PageView<InventoryView> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)
        val slice = inventoryRepository.findByMerchantIdOrderByUpdatedAtDescIdAsc(
            merchantId,
            PageRequest.of(safePage, safeSize),
        )
        val products = productRepository.findAllById(slice.content.map { it.productId }).associateBy { it.id }
        val locations = locationRepository.findAllById(slice.content.map { it.locationId }).associateBy { it.id }
        val views = slice.content.mapNotNull { item ->
            val product = products[item.productId] ?: return@mapNotNull null
            val location = locations[item.locationId] ?: return@mapNotNull null
            InventoryView(
                id = item.id,
                locationId = location.id,
                locationCode = location.code,
                locationName = location.name,
                productId = product.id,
                sku = product.sku,
                productName = product.name,
                onHand = item.onHand,
                reserved = item.reserved,
                available = item.onHand.subtract(item.reserved),
                unit = product.unit,
                version = item.version,
                updatedAt = item.updatedAt,
            )
        }
        return PageView(views, safePage, safeSize, slice.hasNext())
    }

    fun adjustFromMutation(
        merchantId: UUID,
        deviceId: UUID,
        mutationId: UUID,
        productId: UUID,
        locationId: UUID,
        delta: BigDecimal,
        reason: String,
        occurredAt: Instant,
    ): InventoryAdjustmentOutcome {
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            throw BusinessRuleException("Stock adjustment cannot be zero")
        }
        if (delta.abs() > MAX_QUANTITY) throw BusinessRuleException("Stock adjustment is too large")
        productRepository.findByIdAndMerchantId(productId, merchantId)
            ?: throw NotFoundException("Product $productId was not found")
        locationRepository.findByIdAndMerchantId(locationId, merchantId)
            ?: throw NotFoundException("Location $locationId was not found")
        val inventory = inventoryRepository.findByMerchantIdAndLocationIdAndProductId(
            merchantId,
            locationId,
            productId,
        ) ?: InventoryItemEntity(
            merchantId = merchantId,
            locationId = locationId,
            productId = productId,
        )
        val nextOnHand = inventory.onHand.add(delta)
        if (nextOnHand < BigDecimal.ZERO) {
            throw BusinessRuleException("Stock cannot fall below zero")
        }
        inventory.onHand = nextOnHand.setScale(3)
        inventory.updatedAt = Instant.now()
        val savedInventory = inventoryRepository.saveAndFlush(inventory)
        val movement = stockMovementRepository.save(
            StockMovementEntity(
                merchantId = merchantId,
                locationId = locationId,
                productId = productId,
                deviceId = deviceId,
                mutationId = mutationId,
                movementType = if (delta.signum() > 0) "RECEIPT" else "ADJUSTMENT",
                quantityDelta = delta.setScale(3),
                reason = reason.trim().ifBlank { "Offline stock adjustment" }.also {
                    if (it.length > 240) throw BusinessRuleException("Stock adjustment reason cannot exceed 240 characters")
                },
                occurredAt = occurredAt,
            ),
        )
        return InventoryAdjustmentOutcome(savedInventory, movement)
    }

    private companion object {
        val MAX_QUANTITY: BigDecimal = BigDecimal("99999999999.999")
    }
}

data class InventoryAdjustmentOutcome(
    val inventory: InventoryItemEntity,
    val movement: StockMovementEntity,
)
