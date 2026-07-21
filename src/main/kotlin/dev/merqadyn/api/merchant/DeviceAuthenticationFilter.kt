package dev.merqadyn.api.merchant

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

class DeviceAuthenticationFilter(
    private val credentialService: DeviceCredentialService,
) : OncePerRequestFilter() {
    private val merchantPath = Regex("^/api/v1/merchants/([0-9a-fA-F-]{36})(?:/.*)?$")

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/api/v1/merchants/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val deviceIdHeader = request.getHeader(DEVICE_ID_HEADER)
        val token = request.getHeader(DEVICE_TOKEN_HEADER)
        if (deviceIdHeader == null && token == null) {
            filterChain.doFilter(request, response)
            return
        }
        val merchantId = merchantPath.matchEntire(request.requestURI)?.groupValues?.get(1)?.toUuidOrNull()
        val deviceId = deviceIdHeader?.toUuidOrNull()
        val principal = if (merchantId != null && deviceId != null && token != null) {
            credentialService.authenticate(merchantId, deviceId, token)
        } else {
            null
        }
        if (principal == null) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("{\"code\":\"invalid_device_credentials\",\"message\":\"Device credentials are invalid\"}")
            return
        }
        val authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            listOf(SimpleGrantedAuthority("ROLE_DEVICE")),
        )
        SecurityContextHolder.getContext().authentication = authentication
        filterChain.doFilter(request, response)
    }

    private fun String.toUuidOrNull(): UUID? = try {
        UUID.fromString(this)
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        const val DEVICE_ID_HEADER = "X-Merqadyn-Device-Id"
        const val DEVICE_TOKEN_HEADER = "X-Merqadyn-Device-Token"
    }
}
