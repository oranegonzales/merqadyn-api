package dev.merqadyn.api.sync

import dev.merqadyn.api.api.BusinessRuleException
import dev.merqadyn.api.api.MutationRequest
import dev.merqadyn.api.api.MutationResult
import dev.merqadyn.api.api.MutationType
import dev.merqadyn.api.api.NotFoundException
import dev.merqadyn.api.api.SyncBatchRequest
import dev.merqadyn.api.api.SyncBatchResponse
import dev.merqadyn.api.catalog.ProductService
import dev.merqadyn.api.catalog.ProductUpdateOutcome
import dev.merqadyn.api.catalog.toView
import dev.merqadyn.api.inventory.InventoryService
import dev.merqadyn.api.merchant.DeviceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class SyncService(
    private val deviceRepository: DeviceRepository,
    private val processedMutationRepository: ProcessedMutationRepository,
    private val syncConflictRepository: SyncConflictRepository,
    private val productService: ProductService,
    private val inventoryService: InventoryService,
    private val changeLogService: ChangeLogService,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun process(merchantId: UUID, request: SyncBatchRequest): SyncBatchResponse {
        val device = deviceRepository.findByIdAndMerchantId(request.deviceId, merchantId)
            ?: throw NotFoundException("Device ${request.deviceId} was not found for this merchant")
        val results = request.mutations.map { mutation ->
            val processed = processedMutationRepository.findByDeviceIdAndMutationId(device.id, mutation.mutationId)
            if (processed != null) {
                objectMapper.readValue(processed.responsePayload, MutationResult::class.java).copy(replayed = true)
            } else {
                val result = try {
                    applyMutation(merchantId, device.id, mutation)
                } catch (exception: BusinessRuleException) {
                    rejected(mutation, exception.message ?: "Mutation rejected")
                } catch (exception: NotFoundException) {
                    rejected(mutation, exception.message ?: "Referenced resource was not found")
                }
                processedMutationRepository.save(
                    ProcessedMutationEntity(
                        merchantId = merchantId,
                        deviceId = device.id,
                        mutationId = mutation.mutationId,
                        mutationType = mutation.type.name,
                        status = result.status,
                        responsePayload = objectMapper.writeValueAsString(result),
                    ),
                )
                result
            }
        }
        val changes = changeLogService.page(merchantId, request.lastPulledCursor, 200)
        device.lastCursor = changes.nextCursor
        device.lastSeenAt = Instant.now()
        deviceRepository.save(device)
        return SyncBatchResponse(
            deviceId = device.id,
            accepted = results.count { it.status == "APPLIED" },
            replayed = results.count { it.replayed },
            conflicted = results.count { it.status == "CONFLICT" },
            rejected = results.count { it.status == "REJECTED" },
            results = results,
            changes = changes.changes,
            nextCursor = changes.nextCursor,
            hasMore = changes.hasMore,
            completedAt = Instant.now(),
        )
    }

    private fun applyMutation(merchantId: UUID, deviceId: UUID, mutation: MutationRequest): MutationResult =
        when (mutation.type) {
            MutationType.CREATE_PRODUCT -> createProduct(merchantId, mutation)
            MutationType.UPDATE_PRODUCT -> updateProduct(merchantId, deviceId, mutation)
            MutationType.ADJUST_STOCK -> adjustStock(merchantId, deviceId, mutation)
        }

    private fun createProduct(merchantId: UUID, mutation: MutationRequest): MutationResult {
        val product = productService.createFromMutation(merchantId, mutation.entityId, mutation.payload)
        changeLogService.append(
            merchantId = merchantId,
            entityType = "PRODUCT",
            entityId = product.id,
            operation = "CREATED",
            entityVersion = product.version,
            payload = product.toView(),
            occurredAt = mutation.occurredAt ?: Instant.now(),
        )
        return MutationResult(
            mutationId = mutation.mutationId,
            status = "APPLIED",
            entityId = product.id,
            entityVersion = product.version,
            message = "Product created",
        )
    }

    private fun updateProduct(merchantId: UUID, deviceId: UUID, mutation: MutationRequest): MutationResult {
        val entityId = mutation.entityId ?: throw BusinessRuleException("Product entityId is required")
        return when (
            val outcome = productService.updateFromMutation(
                merchantId,
                entityId,
                mutation.baseVersion,
                mutation.payload,
            )
        ) {
            is ProductUpdateOutcome.Updated -> {
                changeLogService.append(
                    merchantId = merchantId,
                    entityType = "PRODUCT",
                    entityId = outcome.product.id,
                    operation = "UPDATED",
                    entityVersion = outcome.product.version,
                    payload = outcome.product.toView(),
                    occurredAt = mutation.occurredAt ?: Instant.now(),
                )
                MutationResult(
                    mutationId = mutation.mutationId,
                    status = "APPLIED",
                    entityId = outcome.product.id,
                    entityVersion = outcome.product.version,
                    message = "Product updated",
                )
            }
            is ProductUpdateOutcome.Conflict -> {
                syncConflictRepository.save(
                    SyncConflictEntity(
                        merchantId = merchantId,
                        deviceId = deviceId,
                        mutationId = mutation.mutationId,
                        entityType = "PRODUCT",
                        entityId = outcome.product.id,
                        reason = "Client version does not match the current product version",
                        clientVersion = mutation.baseVersion,
                        serverVersion = outcome.product.version,
                        clientPayload = objectMapper.writeValueAsString(mutation.payload),
                        serverPayload = objectMapper.writeValueAsString(outcome.product.toView()),
                    ),
                )
                MutationResult(
                    mutationId = mutation.mutationId,
                    status = "CONFLICT",
                    entityId = outcome.product.id,
                    entityVersion = outcome.product.version,
                    message = "Server version retained; pull changes before retrying",
                )
            }
        }
    }

    private fun adjustStock(merchantId: UUID, deviceId: UUID, mutation: MutationRequest): MutationResult {
        val productId = mutation.entityId ?: throw BusinessRuleException("Product entityId is required")
        val locationId = mutation.payload.path("locationId").asString("").let {
            try {
                UUID.fromString(it)
            } catch (_: IllegalArgumentException) {
                throw BusinessRuleException("A valid locationId is required")
            }
        }
        val deltaNode = mutation.payload.path("delta")
        if (deltaNode.isMissingNode || deltaNode.isNull || !deltaNode.isNumber) {
            throw BusinessRuleException("A numeric stock delta is required")
        }
        val delta: BigDecimal = deltaNode.decimalValue()
        val outcome = inventoryService.adjustFromMutation(
            merchantId = merchantId,
            deviceId = deviceId,
            mutationId = mutation.mutationId,
            productId = productId,
            locationId = locationId,
            delta = delta,
            reason = mutation.payload.path("reason").asString("Offline stock adjustment"),
            occurredAt = mutation.occurredAt ?: Instant.now(),
        )
        changeLogService.append(
            merchantId = merchantId,
            entityType = "INVENTORY",
            entityId = outcome.inventory.id,
            operation = "ADJUSTED",
            entityVersion = outcome.inventory.version,
            payload = mapOf(
                "productId" to productId,
                "locationId" to locationId,
                "onHand" to outcome.inventory.onHand,
                "delta" to delta,
                "reason" to outcome.movement.reason,
            ),
            occurredAt = outcome.movement.occurredAt,
        )
        return MutationResult(
            mutationId = mutation.mutationId,
            status = "APPLIED",
            entityId = outcome.inventory.id,
            entityVersion = outcome.inventory.version,
            message = "Stock adjusted",
        )
    }

    private fun rejected(mutation: MutationRequest, message: String) = MutationResult(
        mutationId = mutation.mutationId,
        status = "REJECTED",
        entityId = mutation.entityId,
        message = message,
    )
}
