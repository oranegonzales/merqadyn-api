package dev.merqadyn.api.sync

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ChangeLogRepository : JpaRepository<ChangeLogEntity, Long> {
    fun findByMerchantIdAndCursorGreaterThanOrderByCursorAsc(
        merchantId: UUID,
        cursor: Long,
        pageable: Pageable,
    ): List<ChangeLogEntity>

    fun findTop12ByMerchantIdOrderByCursorDesc(merchantId: UUID): List<ChangeLogEntity>

    @Query("select coalesce(max(c.cursor), 0) from ChangeLogEntity c where c.merchantId = :merchantId")
    fun latestCursor(@Param("merchantId") merchantId: UUID): Long
}

interface ProcessedMutationRepository : JpaRepository<ProcessedMutationEntity, UUID> {
    fun findByDeviceIdAndMutationId(deviceId: UUID, mutationId: UUID): ProcessedMutationEntity?
}

interface SyncConflictRepository : JpaRepository<SyncConflictEntity, UUID> {
    fun countByMerchantIdAndCreatedAtAfter(merchantId: UUID, createdAt: Instant): Long
    fun findTop8ByMerchantIdOrderByCreatedAtDesc(merchantId: UUID): List<SyncConflictEntity>
}
