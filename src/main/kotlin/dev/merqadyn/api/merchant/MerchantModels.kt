package dev.merqadyn.api.merchant

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchants")
class MerchantEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 140)
    var name: String = "",
    @Column(nullable = false, length = 3)
    var currency: String = "JMD",
    @Column(nullable = false, length = 80)
    var timezone: String = "America/Jamaica",
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "locations")
class LocationEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 32)
    var code: String = "",
    @Column(nullable = false, length = 140)
    var name: String = "",
    @Column(nullable = false, length = 240)
    var address: String = "",
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "devices")
class DeviceEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Column(name = "location_id", nullable = false)
    var locationId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 120)
    var name: String = "",
    @Column(nullable = false, length = 40)
    var platform: String = "ANDROID",
    @Column(name = "app_version", nullable = false, length = 32)
    var appVersion: String = "1.0.0",
    @Column(name = "last_cursor", nullable = false)
    var lastCursor: Long = 0,
    @Column(name = "last_seen_at")
    var lastSeenAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
