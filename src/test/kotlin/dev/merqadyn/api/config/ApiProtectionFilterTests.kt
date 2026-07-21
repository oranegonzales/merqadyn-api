package dev.merqadyn.api.config

import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class ApiProtectionFilterTests {
    @Test
    fun `memory limiter resets keys and enforces a window quota`() {
        val store = InMemoryRateLimitStore()
        assertTrue(store.allow("test", 2, 60))
        assertTrue(store.allow("test", 2, 60))
        assertFalse(store.allow("test", 2, 60))
        assertTrue(store.allow("another", 1, 60))
    }

    @Test
    fun `oversized request is rejected before reaching the application`() {
        val filter = ApiProtectionFilter(InMemoryRateLimitStore(), 8, 10, 10)
        val request = MockHttpServletRequest("POST", "/api/v1/test").apply {
            setContent("more than eight bytes".toByteArray())
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, response.status)
        assertTrue(response.contentAsString.contains("request_too_large"))
        assertEquals(null, chain.request)
    }

    @Test
    fun `streamed write without a declared length is rejected`() {
        val filter = ApiProtectionFilter(InMemoryRateLimitStore(), 1024, 10, 10)
        val request = MockHttpServletRequest("POST", "/api/v1/test").apply {
            setContentType("application/json")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(HttpServletResponse.SC_LENGTH_REQUIRED, response.status)
        assertTrue(response.contentAsString.contains("length_required"))
    }

    @Test
    fun `public overview is cacheable while private API data is not`() {
        val filter = ApiProtectionFilter(InMemoryRateLimitStore(), 1024, 10, 10)
        val publicResponse = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest("GET", "/api/v1/public/overview"), publicResponse, MockFilterChain())
        assertTrue(publicResponse.getHeader("Cache-Control")!!.contains("s-maxage=30"))

        val privateResponse = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest("GET", "/api/v1/merchants/id/context"), privateResponse, MockFilterChain())
        assertEquals("no-store", privateResponse.getHeader("Cache-Control"))
    }
}
