package dev.merqadyn.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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

@Configuration
class SecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun userDetailsService(
        @Value("\${merqadyn.security.username}") username: String,
        @Value("\${merqadyn.security.password}") password: String,
        passwordEncoder: PasswordEncoder,
    ): UserDetailsService = InMemoryUserDetailsManager(
        User.withUsername(username)
            .password(passwordEncoder.encode(password))
            .roles("ADMIN")
            .build(),
    )

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/", "/index.html", "/assets/**", "/favicon.svg", "/error").permitAll()
                it.requestMatchers("/actuator/health", "/actuator/info").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()
                it.anyRequest().authenticated()
            }
            .httpBasic(Customizer.withDefaults())
            .headers {
                it.contentSecurityPolicy { policy ->
                    policy.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; font-src 'self'",
                    )
                }
            }
        return http.build()
    }
}
