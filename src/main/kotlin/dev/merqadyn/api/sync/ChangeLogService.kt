package dev.merqadyn.api.sync

import dev.merqadyn.api.api.ChangePage
import dev.merqadyn.api.api.ChangeView
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@Service
class ChangeLogService(
    private val changeLogRepository: ChangeLogRepository,
    private val objectMapper: ObjectMapper,
) {
    fun append(
        merchantId: UUID,
        entityType: String,
        entityId: UUID,
        operation: String,
        entityVersion: Long,
        payload: Any,
        occurredAt: Instant = Instant.now(),
    ): ChangeLogEntity = changeLogRepository.saveAndFlush(
        ChangeLogEntity(
            merchantId = merchantId,
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            entityVersion = entityVersion,
            payload = objectMapper.writeValueAsString(payload),
            occurredAt = occurredAt,
        ),
    )

    fun page(merchantId: UUID, after: Long, limit: Int): ChangePage {
        val safeLimit = limit.coerceIn(1, 200)
        val rows = changeLogRepository.findByMerchantIdAndCursorGreaterThanOrderByCursorAsc(
            merchantId,
            after.coerceAtLeast(0),
            PageRequest.of(0, safeLimit + 1),
        )
        val hasMore = rows.size > safeLimit
        val visible = rows.take(safeLimit).map { it.toView(objectMapper) }
        return ChangePage(
            changes = visible,
            nextCursor = visible.lastOrNull()?.cursor ?: after.coerceAtLeast(0),
            hasMore = hasMore,
        )
    }
}

fun ChangeLogEntity.toView(objectMapper: ObjectMapper) = ChangeView(
    cursor = cursor,
    entityType = entityType,
    entityId = entityId,
    operation = operation,
    entityVersion = entityVersion,
    payload = objectMapper.readTree(payload),
    occurredAt = occurredAt,
)
