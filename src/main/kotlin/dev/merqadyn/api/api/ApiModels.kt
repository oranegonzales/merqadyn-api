package dev.merqadyn.api.api

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateProductRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val sku: String,
    @field:NotBlank
    @field:Size(max = 180)
    val name: String,
    @field:NotBlank
    @field:Size(max = 100)
    val category: String,
    @field:NotBlank
    @field:Size(max = 32)
    val unit: String,
    @field:DecimalMin("0.00")
    val price: BigDecimal,
)

data class ProductView(
    val id: UUID,
    val sku: String,
    val name: String,
    val category: String,
    val unit: String,
    val price: BigDecimal,
    val active: Boolean,
    val version: Long,
    val updatedAt: Instant,
)

data class InventoryView(
    val id: UUID,
    val locationId: UUID,
    val locationCode: String,
    val locationName: String,
    val productId: UUID,
    val sku: String,
    val productName: String,
    val onHand: BigDecimal,
    val reserved: BigDecimal,
    val available: BigDecimal,
    val unit: String,
    val version: Long,
    val updatedAt: Instant,
)

enum class MutationType {
    CREATE_PRODUCT,
    UPDATE_PRODUCT,
    ADJUST_STOCK,
}

data class MutationRequest(
    @field:NotNull
    val mutationId: UUID,
    @field:NotNull
    val type: MutationType,
    val entityId: UUID? = null,
    val baseVersion: Long? = null,
    @field:NotNull
    val payload: JsonNode,
    val occurredAt: Instant? = null,
)

data class SyncBatchRequest(
    @field:NotNull
    val deviceId: UUID,
    val lastPulledCursor: Long = 0,
    @field:Valid
    @field:Size(min = 1, max = 100)
    val mutations: List<MutationRequest>,
)

data class MutationResult(
    val mutationId: UUID,
    val status: String,
    val entityId: UUID? = null,
    val entityVersion: Long? = null,
    val message: String,
    val replayed: Boolean = false,
)

data class ChangeView(
    val cursor: Long,
    val entityType: String,
    val entityId: UUID,
    val operation: String,
    val entityVersion: Long,
    val payload: JsonNode,
    val occurredAt: Instant,
)

data class ChangePage(
    val changes: List<ChangeView>,
    val nextCursor: Long,
    val hasMore: Boolean,
)

data class PageView<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val hasMore: Boolean,
)

data class SyncBatchResponse(
    val deviceId: UUID,
    val accepted: Int,
    val replayed: Int,
    val conflicted: Int,
    val rejected: Int,
    val results: List<MutationResult>,
    val changes: List<ChangeView>,
    val nextCursor: Long,
    val hasMore: Boolean,
    val completedAt: Instant,
)

data class MerchantSummary(
    val id: UUID,
    val name: String,
    val currency: String,
    val timezone: String,
)

data class MerchantContextResponse(
    val merchant: MerchantSummary,
    val latestCursor: Long,
)

data class PublicConfig(
    val demoMerchantId: UUID,
)

data class DeviceEnrollmentResponse(
    val deviceId: UUID,
    val code: String,
    val expiresAt: Instant,
)

data class RedeemEnrollmentRequest(
    @field:NotNull
    val deviceId: UUID,
    @field:Pattern(regexp = "^[23456789A-HJ-NP-Z]{10}$")
    val code: String,
)

data class DeviceCredentialsResponse(
    val merchantId: UUID,
    val deviceId: UUID,
    val deviceToken: String,
)

data class LocationSummary(
    val id: UUID,
    val code: String,
    val name: String,
    val address: String,
    val itemCount: Int,
    val unitsOnHand: BigDecimal,
)

data class DeviceSummary(
    val id: UUID,
    val name: String,
    val locationName: String,
    val platform: String,
    val appVersion: String,
    val lastCursor: Long,
    val lastSeenAt: Instant?,
)

data class ActivityView(
    val cursor: Long,
    val entityType: String,
    val operation: String,
    val summary: String,
    val occurredAt: Instant,
)

data class OverviewResponse(
    val merchant: MerchantSummary,
    val productCount: Int,
    val locationCount: Int,
    val deviceCount: Int,
    val unitsOnHand: BigDecimal,
    val inventoryValue: BigDecimal,
    val lowStockItems: Int,
    val conflictsLast24Hours: Long,
    val latestCursor: Long,
    val locations: List<LocationSummary>,
    val devices: List<DeviceSummary>,
    val inventory: List<InventoryView>,
    val recentActivity: List<ActivityView>,
)

data class PublicMerchantSummary(
    val name: String,
    val currency: String,
)

data class PublicLocationSummary(
    val code: String,
    val name: String,
    val address: String,
    val itemCount: Int,
    val unitsOnHand: BigDecimal,
)

data class PublicInventoryView(
    val locationCode: String,
    val locationName: String,
    val sku: String,
    val productName: String,
    val onHand: BigDecimal,
    val reserved: BigDecimal,
    val available: BigDecimal,
    val unit: String,
    val version: Long,
)

data class PublicDeviceSummary(
    val name: String,
    val locationName: String,
    val platform: String,
    val appVersion: String,
    val lastCursor: Long,
)

data class PublicOverviewResponse(
    val merchant: PublicMerchantSummary,
    val productCount: Int,
    val locationCount: Int,
    val deviceCount: Int,
    val unitsOnHand: BigDecimal,
    val inventoryValue: BigDecimal,
    val lowStockItems: Int,
    val locations: List<PublicLocationSummary>,
    val devices: List<PublicDeviceSummary>,
    val inventory: List<PublicInventoryView>,
    val recentActivity: List<ActivityView>,
)

data class ApiProblem(
    val code: String,
    val message: String,
    val path: String,
    val timestamp: Instant = Instant.now(),
    val fieldErrors: Map<String, String>? = null,
)
