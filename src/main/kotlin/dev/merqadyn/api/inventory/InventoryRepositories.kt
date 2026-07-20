package dev.merqadyn.api.inventory

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InventoryItemRepository : JpaRepository<InventoryItemEntity, UUID> {
    fun findAllByMerchantIdOrderByUpdatedAtDesc(merchantId: UUID): List<InventoryItemEntity>
    fun findByMerchantIdAndLocationIdAndProductId(
        merchantId: UUID,
        locationId: UUID,
        productId: UUID,
    ): InventoryItemEntity?
}

interface StockMovementRepository : JpaRepository<StockMovementEntity, UUID> {
    fun findTop12ByMerchantIdOrderByCreatedAtDesc(merchantId: UUID): List<StockMovementEntity>
}
