package dev.merqadyn.api.merchant

import dev.merqadyn.api.api.EnrollmentException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class DeviceCredentialServiceTests @Autowired constructor(
    private val service: DeviceCredentialService,
    private val merchants: MerchantRepository,
    private val locations: LocationRepository,
    private val devices: DeviceRepository,
) {
    @Test
    fun `enrollment code is one time and token is merchant scoped`() {
        val merchantId = UUID.randomUUID()
        val otherMerchantId = UUID.randomUUID()
        val locationId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        merchants.save(MerchantEntity(id = merchantId, name = "Credential test merchant"))
        merchants.save(MerchantEntity(id = otherMerchantId, name = "Other merchant"))
        locations.save(LocationEntity(id = locationId, merchantId = merchantId, code = "AUTH", name = "Auth", address = "Test"))
        devices.save(DeviceEntity(id = deviceId, merchantId = merchantId, locationId = locationId, name = "Credential test phone"))

        val enrollment = service.createEnrollment(merchantId, deviceId)
        val credentials = service.redeem(deviceId, enrollment.code)

        assertEquals(merchantId, credentials.merchantId)
        assertNotNull(service.authenticate(merchantId, deviceId, credentials.deviceToken))
        assertNull(service.authenticate(otherMerchantId, deviceId, credentials.deviceToken))
        assertNull(service.authenticate(merchantId, deviceId, "not-the-device-token"))
        assertThrows(EnrollmentException::class.java) { service.redeem(deviceId, enrollment.code) }
        service.revoke(merchantId, deviceId)
        assertNull(service.authenticate(merchantId, deviceId, credentials.deviceToken))
    }

    @Test
    fun `creating a new enrollment rotates the existing token`() {
        val merchantId = UUID.randomUUID()
        val locationId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        merchants.save(MerchantEntity(id = merchantId, name = "Rotation test merchant"))
        locations.save(LocationEntity(id = locationId, merchantId = merchantId, code = "ROT", name = "Rotation", address = "Test"))
        devices.save(DeviceEntity(id = deviceId, merchantId = merchantId, locationId = locationId, name = "Rotation test phone"))

        val first = service.redeem(deviceId, service.createEnrollment(merchantId, deviceId).code)
        val second = service.redeem(deviceId, service.createEnrollment(merchantId, deviceId).code)

        assertNull(service.authenticate(merchantId, deviceId, first.deviceToken))
        assertNotNull(service.authenticate(merchantId, deviceId, second.deviceToken))
    }
}
