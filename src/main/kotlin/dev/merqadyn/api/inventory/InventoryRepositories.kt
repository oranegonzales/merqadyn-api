package dev.merqadyn.api.inventory

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.util.UUID

interface InventoryItemRepository : JpaRepository<InventoryItemEntity, UUID> {
    fun findAllByMerchantIdOrderByUpdatedAtDesc(merchantId: UUID): List<InventoryItemEntity>
    fun findTop200ByMerchantIdAndLocationIdOrderByUpdatedAtDesc(merchantId: UUID, locationId: UUID): List<InventoryItemEntity>
    fun findByMerchantIdAndLocationIdAndProductId(
        merchantId: UUID,
        locationId: UUID,
        productId: UUID,
    ): InventoryItemEntity?

    fun findByMerchantIdOrderByUpdatedAtDescIdAsc(merchantId: UUID, pageable: Pageable): Slice<InventoryItemEntity>

    @Query("select coalesce(sum(i.onHand), 0) from InventoryItemEntity i where i.merchantId = :merchantId")
    fun totalOnHand(merchantId: UUID): BigDecimal

    @Query("select count(i) from InventoryItemEntity i where i.merchantId = :merchantId and (i.onHand - i.reserved) <= 5")
    fun lowStockCount(merchantId: UUID): Long

    @Query("select coalesce(sum(i.onHand * p.price), 0) from InventoryItemEntity i join ProductEntity p on p.id = i.productId where i.merchantId = :merchantId")
    fun inventoryValue(merchantId: UUID): BigDecimal

    @Query("select i.locationId as locationId, count(i) as itemCount, coalesce(sum(i.onHand), 0) as unitsOnHand from InventoryItemEntity i where i.merchantId = :merchantId group by i.locationId")
    fun locationSummaries(merchantId: UUID): List<LocationInventorySummary>
}

interface LocationInventorySummary {
    val locationId: UUID
    val itemCount: Long
    val unitsOnHand: BigDecimal
}

interface StockMovementRepository : JpaRepository<StockMovementEntity, UUID> {
    fun findTop12ByMerchantIdOrderByCreatedAtDesc(merchantId: UUID): List<StockMovementEntity>
}
