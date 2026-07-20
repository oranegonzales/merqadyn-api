package dev.merqadyn.api.merchant

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantRepository : JpaRepository<MerchantEntity, UUID>

interface LocationRepository : JpaRepository<LocationEntity, UUID> {
    fun findAllByMerchantIdOrderByName(merchantId: UUID): List<LocationEntity>
    fun findByIdAndMerchantId(id: UUID, merchantId: UUID): LocationEntity?
}

interface DeviceRepository : JpaRepository<DeviceEntity, UUID> {
    fun findAllByMerchantIdOrderByName(merchantId: UUID): List<DeviceEntity>
    fun findByIdAndMerchantId(id: UUID, merchantId: UUID): DeviceEntity?
}
