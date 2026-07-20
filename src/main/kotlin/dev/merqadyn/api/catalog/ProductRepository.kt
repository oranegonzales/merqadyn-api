package dev.merqadyn.api.catalog

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProductRepository : JpaRepository<ProductEntity, UUID> {
    fun findAllByMerchantIdOrderByName(merchantId: UUID): List<ProductEntity>
    fun findByIdAndMerchantId(id: UUID, merchantId: UUID): ProductEntity?
    fun existsByMerchantIdAndSkuIgnoreCase(merchantId: UUID, sku: String): Boolean
}
