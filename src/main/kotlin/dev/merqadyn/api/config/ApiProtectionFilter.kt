package dev.merqadyn.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

interface RateLimitStore {
    fun allow(key: String, limit: Int, windowSeconds: Long = 60): Boolean
}

@Component
@ConditionalOnProperty(name = ["merqadyn.security.rate-limit-backend"], havingValue = "redis", matchIfMissing = true)
class RedisRateLimitStore(
    private val redis: StringRedisTemplate,
) : RateLimitStore {
    private val script = DefaultRedisScript<Long>(
        """
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        return current
        """.trimIndent(),
        Long::class.java,
    )

    override fun allow(key: String, limit: Int, windowSeconds: Long): Boolean {
        val count = redis.execute(script, listOf("merqadyn:rate:$key"), windowSeconds.toString()) ?: return false
        return count <= limit
    }
}

@Component
@ConditionalOnProperty(name = ["merqadyn.security.rate-limit-backend"], havingValue = "memory")
class InMemoryRateLimitStore : RateLimitStore {
    private val windows = ConcurrentHashMap<String, RequestWindow>()

    override fun allow(key: String, limit: Int, windowSeconds: Long): Boolean {
        val window = Instant.now().epochSecond / windowSeconds
        val next = windows.compute(key) { _, current ->
            if (current == null || current.window != window) RequestWindow(window, 1) else current.copy(count = current.count + 1)
        } ?: return false
        if (windows.size > 10_000) windows.entries.removeIf { it.value.window < window }
        return next.count <= limit
    }

    private data class RequestWindow(val window: Long, val count: Int)
}

@Component
class ApiProtectionFilter(
    private val rateLimits: RateLimitStore,
    @Value("\${merqadyn.security.max-request-bytes:524288}") private val maxRequestBytes: Long,
    @Value("\${merqadyn.security.write-limit-per-minute:120}") private val writeLimit: Int,
    @Value("\${merqadyn.security.enrollment-limit-per-minute:10}") private val enrollmentLimit: Int,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/api/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val publicOverview = request.method == "GET" && request.requestURI == "/api/v1/public/overview"
        if (publicOverview) {
            response.setHeader("Cache-Control", "public, max-age=15, s-maxage=30, stale-while-revalidate=30")
        } else {
            response.setHeader("Cache-Control", "no-store")
            response.setHeader("Pragma", "no-cache")
        }

        val bodyMethod = request.method in setOf("POST", "PUT", "PATCH")
        if (bodyMethod && request.contentLengthLong < 0) {
            reject(response, HttpServletResponse.SC_LENGTH_REQUIRED, "length_required", "Content-Length is required")
            return
        }
        if (request.contentLengthLong > maxRequestBytes) {
            reject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "request_too_large", "Request body is too large")
            return
        }
        if (request.method !in setOf("GET", "HEAD", "OPTIONS")) {
            val enrollment = request.requestURI == "/api/v1/device-enrollments/redeem"
            val limit = if (enrollment) enrollmentLimit else writeLimit
            val identity = request.getHeader("X-Merqadyn-Device-Id")?.takeIf { it.length <= 64 } ?: request.remoteAddr
            val key = "${if (enrollment) "enrollment" else "write"}:$identity"
            if (!rateLimits.allow(key, limit)) {
                response.setHeader("Retry-After", "60")
                reject(response, 429, "rate_limited", "Too many requests")
                return
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun reject(response: HttpServletResponse, status: Int, code: String, message: String) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write("{\"code\":\"$code\",\"message\":\"$message\"}")
    }
}
