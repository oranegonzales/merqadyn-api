package dev.merqadyn.api.config

import dev.merqadyn.api.merchant.DeviceAuthenticationFilter
import dev.merqadyn.api.merchant.DeviceCredentialService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter

@Configuration
class SecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun userDetailsService(
        @Value("\${merqadyn.security.username}") username: String,
        @Value("\${merqadyn.security.password}") password: String,
        passwordEncoder: PasswordEncoder,
    ): UserDetailsService {
        require(username.length in 3..80) { "MERQADYN_ADMIN_USER must contain between 3 and 80 characters" }
        require(password.length >= 20 && password !in setOf("change-me", "merqadyn", "password")) {
            "MERQADYN_ADMIN_PASSWORD must be a unique value of at least 20 characters"
        }
        return InMemoryUserDetailsManager(
            User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("ADMIN")
                .build(),
        )
    }

    @Bean
    fun deviceAuthenticationFilter(service: DeviceCredentialService) = DeviceAuthenticationFilter(service)

    @Bean
    fun deviceAuthenticationFilterRegistration(filter: DeviceAuthenticationFilter) =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    @Bean
    fun apiProtectionFilterRegistration(filter: ApiProtectionFilter) =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        deviceAuthenticationFilter: DeviceAuthenticationFilter,
        apiProtectionFilter: ApiProtectionFilter,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.disable() }
            .requestCache { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/", "/index.html", "/assets/**", "/favicon.svg", "/error").permitAll()
                it.requestMatchers("/actuator/health").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/v1/config", "/api/v1/public/overview").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/device-enrollments/redeem").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/merchants/*/devices/*/enrollment").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.DELETE, "/api/v1/merchants/*/devices/*/credential").hasAnyRole("ADMIN", "DEVICE")
                it.requestMatchers(HttpMethod.POST, "/api/v1/merchants/*/sync/batches").hasAnyRole("ADMIN", "DEVICE")
                it.requestMatchers(HttpMethod.GET, "/api/v1/merchants/**").hasAnyRole("ADMIN", "DEVICE")
                it.requestMatchers(HttpMethod.POST, "/api/v1/merchants/**").hasRole("ADMIN")
                it.anyRequest().authenticated()
            }
            .httpBasic(Customizer.withDefaults())
            .addFilterBefore(apiProtectionFilter, BasicAuthenticationFilter::class.java)
            .addFilterBefore(deviceAuthenticationFilter, BasicAuthenticationFilter::class.java)
            .headers {
                it.contentSecurityPolicy { policy ->
                    policy.policyDirectives(
                        "default-src 'self'; base-uri 'self'; script-src 'self'; style-src 'self'; " +
                            "img-src 'self'; connect-src 'self'; font-src 'self'; object-src 'none'; " +
                            "frame-ancestors 'none'; form-action 'none'",
                    )
                }
                it.frameOptions { frame -> frame.deny() }
                it.referrerPolicy { referrer ->
                    referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)
                }
                it.permissionsPolicyHeader { permissions ->
                    permissions.policy("camera=(), microphone=(), geolocation=(), payment=(), usb=()")
                }
                it.httpStrictTransportSecurity { hsts ->
                    hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(31_536_000)
                }
            }
        return http.build()
    }
}
