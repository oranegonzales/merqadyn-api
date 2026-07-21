package dev.merqadyn.api.merchant

import dev.merqadyn.api.api.DeviceCredentialsResponse
import dev.merqadyn.api.api.DeviceEnrollmentResponse
import dev.merqadyn.api.api.EnrollmentException
import dev.merqadyn.api.api.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

data class DevicePrincipal(val deviceId: UUID, val merchantId: UUID)

@Service
class DeviceCredentialService(
    private val deviceRepository: DeviceRepository,
) {
    private val secureRandom = SecureRandom()
    private val codeAlphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    @Transactional
    fun createEnrollment(merchantId: UUID, deviceId: UUID): DeviceEnrollmentResponse {
        val device = deviceRepository.findForCredentialUpdate(deviceId, merchantId)
            ?: throw NotFoundException("Device was not found for this merchant")
        val code = buildString(10) {
            repeat(10) { append(codeAlphabet[secureRandom.nextInt(codeAlphabet.length)]) }
        }
        val expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES)
        device.enrollmentCodeHash = hash(code)
        device.enrollmentExpiresAt = expiresAt
        deviceRepository.save(device)
        return DeviceEnrollmentResponse(device.id, code, expiresAt)
    }

    @Transactional
    fun redeem(deviceId: UUID, code: String): DeviceCredentialsResponse {
        val device = deviceRepository.findForCredentialUpdate(deviceId) ?: throw EnrollmentException()
        val expectedHash = device.enrollmentCodeHash
        val expiresAt = device.enrollmentExpiresAt
        val valid = expectedHash != null &&
            expiresAt != null &&
            expiresAt.isAfter(Instant.now()) &&
            constantTimeEquals(expectedHash, hash(code.trim().uppercase()))
        if (!valid) throw EnrollmentException()

        val bytes = ByteArray(32).also(secureRandom::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        device.credentialHash = hash(token)
        device.enrollmentCodeHash = null
        device.enrollmentExpiresAt = null
        deviceRepository.save(device)
        return DeviceCredentialsResponse(device.merchantId, device.id, token)
    }

    @Transactional(readOnly = true)
    fun authenticate(merchantId: UUID, deviceId: UUID, token: String): DevicePrincipal? {
        if (token.length !in 32..128) return null
        val device = deviceRepository.findByIdAndMerchantId(deviceId, merchantId) ?: return null
        val expectedHash = device.credentialHash ?: return null
        return if (constantTimeEquals(expectedHash, hash(token))) DevicePrincipal(device.id, device.merchantId) else null
    }

    @Transactional
    fun revoke(merchantId: UUID, deviceId: UUID) {
        val device = deviceRepository.findForCredentialUpdate(deviceId, merchantId)
            ?: throw NotFoundException("Device was not found for this merchant")
        device.credentialHash = null
        device.enrollmentCodeHash = null
        device.enrollmentExpiresAt = null
        deviceRepository.save(device)
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(left: String, right: String): Boolean =
        MessageDigest.isEqual(left.toByteArray(Charsets.US_ASCII), right.toByteArray(Charsets.US_ASCII))
}
