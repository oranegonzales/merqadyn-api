package dev.merqadyn.api.api

import dev.merqadyn.api.catalog.ProductService
import dev.merqadyn.api.dashboard.OverviewService
import dev.merqadyn.api.inventory.InventoryService
import dev.merqadyn.api.sync.ChangeLogService
import dev.merqadyn.api.sync.SyncService
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
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
) {
    @GetMapping("/api/v1/config")
    fun config() = PublicConfig(demoMerchantId)
}

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}")
class MerchantController(
    private val productService: ProductService,
    private val inventoryService: InventoryService,
    private val syncService: SyncService,
    private val changeLogService: ChangeLogService,
    private val overviewService: OverviewService,
) {
    @GetMapping("/overview")
    fun overview(@PathVariable merchantId: UUID): OverviewResponse = overviewService.overview(merchantId)

    @GetMapping("/products")
    fun products(@PathVariable merchantId: UUID): List<ProductView> = productService.list(merchantId)

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
    ): SyncBatchResponse = syncService.process(merchantId, request)
}
