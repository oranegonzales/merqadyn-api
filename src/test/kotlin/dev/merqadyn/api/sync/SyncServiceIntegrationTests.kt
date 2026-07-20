package dev.merqadyn.api.sync

import dev.merqadyn.api.api.MutationRequest
import dev.merqadyn.api.api.MutationType
import dev.merqadyn.api.api.SyncBatchRequest
import dev.merqadyn.api.catalog.ProductEntity
import dev.merqadyn.api.catalog.ProductRepository
import dev.merqadyn.api.inventory.InventoryItemRepository
import dev.merqadyn.api.merchant.DeviceEntity
import dev.merqadyn.api.merchant.DeviceRepository
import dev.merqadyn.api.merchant.LocationEntity
import dev.merqadyn.api.merchant.LocationRepository
import dev.merqadyn.api.merchant.MerchantEntity
import dev.merqadyn.api.merchant.MerchantRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest
@Transactional
class SyncServiceIntegrationTests @Autowired constructor(
    private val syncService: SyncService,
    private val merchantRepository: MerchantRepository,
    private val locationRepository: LocationRepository,
    private val deviceRepository: DeviceRepository,
    private val productRepository: ProductRepository,
    private val inventoryItemRepository: InventoryItemRepository,
    private val conflictRepository: SyncConflictRepository,
    private val objectMapper: ObjectMapper,
) {
    private val merchantId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val locationId = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val deviceId = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc")
    private val productId = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd")

    @BeforeEach
    fun seedMerchant() {
        merchantRepository.save(MerchantEntity(id = merchantId, name = "Test merchant"))
        locationRepository.save(
            LocationEntity(
                id = locationId,
                merchantId = merchantId,
                code = "TEST",
                name = "Test location",
                address = "Kingston",
            ),
        )
        deviceRepository.save(
            DeviceEntity(
                id = deviceId,
                merchantId = merchantId,
                locationId = locationId,
                name = "Test device",
            ),
        )
        productRepository.saveAndFlush(
            ProductEntity(
                id = productId,
                merchantId = merchantId,
                sku = "TEST-001",
                name = "Test product",
                category = "Test",
                price = BigDecimal("100.00"),
            ),
        )
    }

    @Test
    fun `replayed stock mutation is applied only once`() {
        val mutationId = UUID.randomUUID()
        val payload = objectMapper.createObjectNode()
            .put("locationId", locationId.toString())
            .put("delta", 4)
            .put("reason", "Counted while disconnected")
        val request = SyncBatchRequest(
            deviceId = deviceId,
            mutations = listOf(
                MutationRequest(
                    mutationId = mutationId,
                    type = MutationType.ADJUST_STOCK,
                    entityId = productId,
                    payload = payload,
                ),
            ),
        )

        val first = syncService.process(merchantId, request)
        val replay = syncService.process(merchantId, request)

        val inventory = inventoryItemRepository.findByMerchantIdAndLocationIdAndProductId(
            merchantId,
            locationId,
            productId,
        )
        assertEquals(1, first.accepted)
        assertEquals(1, replay.replayed)
        assertEquals(BigDecimal("4.000"), inventory?.onHand)
    }

    @Test
    fun `stale product mutation records conflict and retains server value`() {
        val payload = objectMapper.createObjectNode().put("name", "Stale device name")
        val response = syncService.process(
            merchantId,
            SyncBatchRequest(
                deviceId = deviceId,
                mutations = listOf(
                    MutationRequest(
                        mutationId = UUID.randomUUID(),
                        type = MutationType.UPDATE_PRODUCT,
                        entityId = productId,
                        baseVersion = 99,
                        payload = payload,
                    ),
                ),
            ),
        )

        assertEquals(1, response.conflicted)
        assertEquals("Test product", productRepository.findById(productId).orElseThrow().name)
        assertTrue(conflictRepository.findTop8ByMerchantIdOrderByCreatedAtDesc(merchantId).isNotEmpty())
    }
}
