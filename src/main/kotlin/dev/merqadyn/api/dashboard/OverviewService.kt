package dev.merqadyn.api.dashboard

import dev.merqadyn.api.api.ActivityView
import dev.merqadyn.api.api.DeviceSummary
import dev.merqadyn.api.api.LocationSummary
import dev.merqadyn.api.api.MerchantSummary
import dev.merqadyn.api.api.MerchantContextResponse
import dev.merqadyn.api.api.NotFoundException
import dev.merqadyn.api.api.OverviewResponse
import dev.merqadyn.api.catalog.ProductRepository
import dev.merqadyn.api.inventory.InventoryItemRepository
import dev.merqadyn.api.inventory.InventoryService
import dev.merqadyn.api.merchant.DeviceRepository
import dev.merqadyn.api.merchant.LocationRepository
import dev.merqadyn.api.merchant.MerchantRepository
import dev.merqadyn.api.sync.ChangeLogRepository
import dev.merqadyn.api.sync.SyncConflictRepository
import org.springframework.stereotype.Service
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class OverviewService(
    private val merchantRepository: MerchantRepository,
    private val locationRepository: LocationRepository,
    private val deviceRepository: DeviceRepository,
    private val productRepository: ProductRepository,
    private val inventoryItemRepository: InventoryItemRepository,
    private val inventoryService: InventoryService,
    private val changeLogRepository: ChangeLogRepository,
    private val syncConflictRepository: SyncConflictRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun overview(merchantId: UUID): OverviewResponse {
        val merchant = merchantRepository.findById(merchantId).orElseThrow {
            NotFoundException("Merchant $merchantId was not found")
        }
        val locations = locationRepository.findAllByMerchantIdOrderByName(merchantId)
        val devices = deviceRepository.findByMerchantIdOrderByNameAsc(merchantId, PageRequest.of(0, 100)).content
        val inventory = inventoryService.page(merchantId, 0, 200).items
        val unitsOnHand = inventoryItemRepository.totalOnHand(merchantId)
        val inventoryValue = inventoryItemRepository.inventoryValue(merchantId).setScale(2, RoundingMode.HALF_UP)
        val inventoryByLocation = inventoryItemRepository.locationSummaries(merchantId).associateBy { it.locationId }
        val locationNames = locations.associate { it.id to it.name }
        return OverviewResponse(
            merchant = MerchantSummary(
                id = merchant.id,
                name = merchant.name,
                currency = merchant.currency,
                timezone = merchant.timezone,
            ),
            productCount = productRepository.countByMerchantIdAndActiveTrue(merchantId).toInt(),
            locationCount = locations.size,
            deviceCount = deviceRepository.countByMerchantId(merchantId).toInt(),
            unitsOnHand = unitsOnHand,
            inventoryValue = inventoryValue,
            lowStockItems = inventoryItemRepository.lowStockCount(merchantId).toInt(),
            conflictsLast24Hours = syncConflictRepository.countByMerchantIdAndCreatedAtAfter(
                merchantId,
                Instant.now().minus(24, ChronoUnit.HOURS),
            ),
            latestCursor = changeLogRepository.latestCursor(merchantId),
            locations = locations.map { location ->
                val locationInventory = inventoryByLocation[location.id]
                LocationSummary(
                    id = location.id,
                    code = location.code,
                    name = location.name,
                    address = location.address,
                    itemCount = locationInventory?.itemCount?.toInt() ?: 0,
                    unitsOnHand = locationInventory?.unitsOnHand ?: BigDecimal.ZERO,
                )
            },
            devices = devices.map { device ->
                DeviceSummary(
                    id = device.id,
                    name = device.name,
                    locationName = locationNames[device.locationId] ?: "Unknown location",
                    platform = device.platform,
                    appVersion = device.appVersion,
                    lastCursor = device.lastCursor,
                    lastSeenAt = device.lastSeenAt,
                )
            },
            inventory = inventory,
            recentActivity = changeLogRepository.findTop12ByMerchantIdOrderByCursorDesc(merchantId).map { change ->
                val payload = objectMapper.readTree(change.payload)
                val summary = when (change.entityType) {
                    "PRODUCT" -> payload.path("name").asString("Product record")
                    "INVENTORY" -> {
                        val delta = payload.path("delta").asString("0")
                        val location = payload.path("locationCode").asString("")
                        listOf("Stock $delta", location).filter { it.isNotBlank() }.joinToString(" at ")
                    }
                    else -> "${change.entityType.lowercase().replaceFirstChar { it.uppercase() }} record"
                }
                ActivityView(
                    cursor = change.cursor,
                    entityType = change.entityType,
                    operation = change.operation,
                    summary = summary,
                    occurredAt = change.occurredAt,
                )
            },
        )
    }

    @Transactional(readOnly = true)
    fun context(merchantId: UUID): MerchantContextResponse {
        val merchant = merchantRepository.findById(merchantId).orElseThrow {
            NotFoundException("Merchant $merchantId was not found")
        }
        return MerchantContextResponse(
            merchant = MerchantSummary(merchant.id, merchant.name, merchant.currency, merchant.timezone),
            latestCursor = changeLogRepository.latestCursor(merchantId),
        )
    }
}
