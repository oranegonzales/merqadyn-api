package dev.merqadyn.api.api

import dev.merqadyn.api.catalog.ProductService
import dev.merqadyn.api.dashboard.OverviewService
import dev.merqadyn.api.inventory.InventoryService
import dev.merqadyn.api.merchant.DeviceCredentialService
import dev.merqadyn.api.merchant.DevicePrincipal
import dev.merqadyn.api.sync.ChangeLogService
import dev.merqadyn.api.sync.SyncService
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class PublicConfigController(
    @Value("\${merqadyn.demo-merchant-id}") private val demoMerchantId: UUID,
    private val overviewService: OverviewService,
    private val deviceCredentialService: DeviceCredentialService,
) {
    @GetMapping("/api/v1/config")
    fun config() = PublicConfig(demoMerchantId)

    @GetMapping("/api/v1/public/overview")
    fun overview(): PublicOverviewResponse = overviewService.overview(demoMerchantId).toPublicView()

    @PostMapping("/api/v1/device-enrollments/redeem")
    fun redeem(@Valid @RequestBody request: RedeemEnrollmentRequest): DeviceCredentialsResponse =
        deviceCredentialService.redeem(request.deviceId, request.code)
}

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}")
class MerchantController(
    private val productService: ProductService,
    private val inventoryService: InventoryService,
    private val syncService: SyncService,
    private val changeLogService: ChangeLogService,
    private val overviewService: OverviewService,
    private val deviceCredentialService: DeviceCredentialService,
) {
    @PostMapping("/devices/{deviceId}/enrollment")
    fun createEnrollment(
        @PathVariable merchantId: UUID,
        @PathVariable deviceId: UUID,
    ): DeviceEnrollmentResponse = deviceCredentialService.createEnrollment(merchantId, deviceId)

    @DeleteMapping("/devices/{deviceId}/credential")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeDeviceCredential(
        @PathVariable merchantId: UUID,
        @PathVariable deviceId: UUID,
        @AuthenticationPrincipal principal: Any?,
    ) {
        if (principal is DevicePrincipal && principal.deviceId != deviceId) {
            throw AccessDeniedException("A device can revoke only its own credential")
        }
        deviceCredentialService.revoke(merchantId, deviceId)
    }

    @GetMapping("/overview")
    fun overview(@PathVariable merchantId: UUID): OverviewResponse = overviewService.overview(merchantId)

    @GetMapping("/context")
    fun context(@PathVariable merchantId: UUID): MerchantContextResponse = overviewService.context(merchantId)

    @GetMapping("/products")
    fun products(@PathVariable merchantId: UUID): List<ProductView> = productService.list(merchantId)

    @GetMapping("/products/page")
    fun productPage(
        @PathVariable merchantId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
    ): PageView<ProductView> = productService.page(merchantId, page, size)

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    fun createProduct(
        @PathVariable merchantId: UUID,
        @Valid @RequestBody request: CreateProductRequest,
    ): ProductView = productService.create(merchantId, request)

    @GetMapping("/inventory")
    fun inventory(
        @PathVariable merchantId: UUID,
        @RequestParam(required = false) locationId: UUID?,
    ): List<InventoryView> = inventoryService.list(merchantId, locationId)

    @GetMapping("/inventory/page")
    fun inventoryPage(
        @PathVariable merchantId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
    ): PageView<InventoryView> = inventoryService.page(merchantId, page, size)

    @GetMapping("/sync/changes")
    fun changes(
        @PathVariable merchantId: UUID,
        @RequestParam(defaultValue = "0") after: Long,
        @RequestParam(defaultValue = "100") limit: Int,
    ): ChangePage = changeLogService.page(merchantId, after, limit)

    @PostMapping("/sync/batches")
    fun sync(
        @PathVariable merchantId: UUID,
        @Valid @RequestBody request: SyncBatchRequest,
        @AuthenticationPrincipal principal: Any?,
    ): SyncBatchResponse {
        if (principal is DevicePrincipal && principal.deviceId != request.deviceId) {
            throw AccessDeniedException("A device can submit only its own mutation batch")
        }
        return syncService.process(merchantId, request)
    }
}

private fun OverviewResponse.toPublicView() = PublicOverviewResponse(
    merchant = PublicMerchantSummary(merchant.name, merchant.currency),
    productCount = productCount,
    locationCount = locationCount,
    deviceCount = deviceCount,
    unitsOnHand = unitsOnHand,
    inventoryValue = inventoryValue,
    lowStockItems = lowStockItems,
    locations = locations.map {
        PublicLocationSummary(it.code, it.name, it.address, it.itemCount, it.unitsOnHand)
    },
    devices = devices.map {
        PublicDeviceSummary(it.name, it.locationName, it.platform, it.appVersion, it.lastCursor)
    },
    inventory = inventory.map {
        PublicInventoryView(
            locationCode = it.locationCode,
            locationName = it.locationName,
            sku = it.sku,
            productName = it.productName,
            onHand = it.onHand,
            reserved = it.reserved,
            available = it.available,
            unit = it.unit,
            version = it.version,
        )
    },
    recentActivity = recentActivity,
)
