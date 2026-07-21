package dev.merqadyn.api.merchant

import org.springframework.data.jpa.repository.JpaRepository
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MerchantRepository : JpaRepository<MerchantEntity, UUID>

interface LocationRepository : JpaRepository<LocationEntity, UUID> {
    fun findAllByMerchantIdOrderByName(merchantId: UUID): List<LocationEntity>
    fun findByIdAndMerchantId(id: UUID, merchantId: UUID): LocationEntity?
}

interface DeviceRepository : JpaRepository<DeviceEntity, UUID> {
    fun findAllByMerchantIdOrderByName(merchantId: UUID): List<DeviceEntity>
    fun findByIdAndMerchantId(id: UUID, merchantId: UUID): DeviceEntity?
    fun findByMerchantIdOrderByNameAsc(merchantId: UUID, pageable: Pageable): Slice<DeviceEntity>
    fun countByMerchantId(merchantId: UUID): Long

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DeviceEntity d where d.id = :deviceId and d.merchantId = :merchantId")
    fun findForCredentialUpdate(
        @Param("deviceId") deviceId: UUID,
        @Param("merchantId") merchantId: UUID,
    ): DeviceEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DeviceEntity d where d.id = :deviceId")
    fun findForCredentialUpdate(@Param("deviceId") deviceId: UUID): DeviceEntity?
}
