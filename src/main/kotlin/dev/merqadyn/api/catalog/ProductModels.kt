package dev.merqadyn.api.catalog

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "products")
class ProductEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 64)
    var sku: String = "",
    @Column(nullable = false, length = 180)
    var name: String = "",
    @Column(nullable = false, length = 100)
    var category: String = "",
    @Column(nullable = false, length = 32)
    var unit: String = "unit",
    @Column(nullable = false, precision = 14, scale = 2)
    var price: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false)
    var active: Boolean = true,
    @Version
    @Column(nullable = false)
    var version: Long = 0,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
