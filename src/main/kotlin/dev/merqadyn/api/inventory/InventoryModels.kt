package dev.merqadyn.api.inventory

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "inventory_items")
class InventoryItemEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Column(name = "location_id", nullable = false)
    var locationId: UUID = UUID.randomUUID(),
    @Column(name = "product_id", nullable = false)
    var productId: UUID = UUID.randomUUID(),
    @Column(name = "on_hand", nullable = false, precision = 14, scale = 3)
    var onHand: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, precision = 14, scale = 3)
    var reserved: BigDecimal = BigDecimal.ZERO,
    @Version
    @Column(nullable = false)
    var version: Long = 0,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "stock_movements")
class StockMovementEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Column(name = "location_id", nullable = false)
    var locationId: UUID = UUID.randomUUID(),
    @Column(name = "product_id", nullable = false)
    var productId: UUID = UUID.randomUUID(),
    @Column(name = "device_id")
    var deviceId: UUID? = null,
    @Column(name = "mutation_id")
    var mutationId: UUID? = null,
    @Column(name = "movement_type", nullable = false, length = 40)
    var movementType: String = "ADJUSTMENT",
    @Column(name = "quantity_delta", nullable = false, precision = 14, scale = 3)
    var quantityDelta: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, length = 240)
    var reason: String = "",
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now(),
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
