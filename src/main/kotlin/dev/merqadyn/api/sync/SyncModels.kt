package dev.merqadyn.api.sync

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "change_log")
class ChangeLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var cursor: Long = 0,
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Column(name = "entity_type", nullable = false, length = 40)
    var entityType: String = "",
    @Column(name = "entity_id", nullable = false)
    var entityId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 40)
    var operation: String = "",
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Long = 0,
    @Column(nullable = false, columnDefinition = "TEXT")
    var payload: String = "{}",
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now(),
)

@Entity
@Table(name = "processed_mutations")
class ProcessedMutationEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Column(name = "device_id", nullable = false)
    var deviceId: UUID = UUID.randomUUID(),
    @Column(name = "mutation_id", nullable = false)
    var mutationId: UUID = UUID.randomUUID(),
    @Column(name = "mutation_type", nullable = false, length = 40)
    var mutationType: String = "",
    @Column(nullable = false, length = 40)
    var status: String = "",
    @Column(name = "response_payload", nullable = false, columnDefinition = "TEXT")
    var responsePayload: String = "{}",
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "sync_conflicts")
class SyncConflictEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Column(name = "device_id", nullable = false)
    var deviceId: UUID = UUID.randomUUID(),
    @Column(name = "mutation_id", nullable = false)
    var mutationId: UUID = UUID.randomUUID(),
    @Column(name = "entity_type", nullable = false, length = 40)
    var entityType: String = "",
    @Column(name = "entity_id", nullable = false)
    var entityId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 160)
    var reason: String = "",
    @Column(name = "client_version")
    var clientVersion: Long? = null,
    @Column(name = "server_version", nullable = false)
    var serverVersion: Long = 0,
    @Column(name = "client_payload", nullable = false, columnDefinition = "TEXT")
    var clientPayload: String = "{}",
    @Column(name = "server_payload", nullable = false, columnDefinition = "TEXT")
    var serverPayload: String = "{}",
    @Column(nullable = false, length = 40)
    var resolution: String = "SERVER_WINS",
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
