package dev.merqadyn.api.catalog

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import java.util.UUID

interface ProductRepository : JpaRepository<ProductEntity, UUID> {
    fun findAllByMerchantIdOrderByName(merchantId: UUID): List<ProductEntity>
    fun findByMerchantIdOrderByNameAscIdAsc(merchantId: UUID, pageable: Pageable): Slice<ProductEntity>
    fun findByIdAndMerchantId(id: UUID, merchantId: UUID): ProductEntity?
    fun existsByMerchantIdAndSkuIgnoreCase(merchantId: UUID, sku: String): Boolean
    fun countByMerchantIdAndActiveTrue(merchantId: UUID): Long
}
