package dev.merqadyn.api

import dev.merqadyn.api.merchant.DeviceEntity
import dev.merqadyn.api.merchant.DeviceCredentialService
import dev.merqadyn.api.merchant.DeviceRepository
import dev.merqadyn.api.merchant.LocationEntity
import dev.merqadyn.api.merchant.LocationRepository
import dev.merqadyn.api.merchant.MerchantEntity
import dev.merqadyn.api.merchant.MerchantRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val merchants: MerchantRepository,
    private val locations: LocationRepository,
    private val devices: DeviceRepository,
    private val credentials: DeviceCredentialService,
) {
    private val merchantId = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val locationId = UUID.fromString("22222222-2222-4222-8222-222222222299")
    private val deviceId = UUID.fromString("55555555-5555-4555-8555-555555555599")

    @BeforeEach
    fun seed() {
        if (!merchants.existsById(merchantId)) merchants.save(MerchantEntity(id = merchantId, name = "Security test merchant"))
        locations.save(LocationEntity(id = locationId, merchantId = merchantId, code = "SEC", name = "Security", address = "Test"))
        devices.save(DeviceEntity(id = deviceId, merchantId = merchantId, locationId = locationId, name = "Security test phone"))
    }

    @Test
    fun `private merchant context requires authentication`() {
        mockMvc.get("/api/v1/merchants/$merchantId/context").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `administrator can enroll a phone and device token can read its merchant only`() {
        mockMvc.post("/api/v1/merchants/$merchantId/devices/$deviceId/enrollment") {
            with(httpBasic("test-admin", "test-password-that-is-long-and-unique"))
        }.andExpect { status { isOk() } }

        val enrollment = credentials.createEnrollment(merchantId, deviceId)
        val token = credentials.redeem(deviceId, enrollment.code).deviceToken
        mockMvc.get("/api/v1/merchants/$merchantId/context") {
            header("X-Merqadyn-Device-Id", deviceId.toString())
            header("X-Merqadyn-Device-Token", token)
        }.andExpect {
            status { isOk() }
            header { string("Cache-Control", "no-store") }
        }

        mockMvc.get("/api/v1/merchants/${UUID.randomUUID()}/context") {
            header("X-Merqadyn-Device-Id", deviceId.toString())
            header("X-Merqadyn-Device-Token", token)
        }.andExpect { status { isUnauthorized() } }
    }
}
