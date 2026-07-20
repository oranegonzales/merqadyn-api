package dev.merqadyn.api.inventory

import dev.merqadyn.api.api.BusinessRuleException
import dev.merqadyn.api.api.InventoryView
import dev.merqadyn.api.api.NotFoundException
import dev.merqadyn.api.catalog.ProductRepository
import dev.merqadyn.api.merchant.LocationRepository
import org.springframework.stereotype.Service
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
        val products = productRepository.findAllByMerchantIdOrderByName(merchantId).associateBy { it.id }
        val locations = locationRepository.findAllByMerchantIdOrderByName(merchantId).associateBy { it.id }
        return inventoryRepository.findAllByMerchantIdOrderByUpdatedAtDesc(merchantId)
            .asSequence()
            .filter { locationId == null || it.locationId == locationId }
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
                reason = reason.trim().ifBlank { "Offline stock adjustment" },
                occurredAt = occurredAt,
            ),
        )
        return InventoryAdjustmentOutcome(savedInventory, movement)
    }
}

data class InventoryAdjustmentOutcome(
    val inventory: InventoryItemEntity,
    val movement: StockMovementEntity,
)
