package dev.merqadyn.api.catalog

import dev.merqadyn.api.api.BusinessRuleException
import dev.merqadyn.api.api.CreateProductRequest
import dev.merqadyn.api.api.NotFoundException
import dev.merqadyn.api.api.ProductView
import dev.merqadyn.api.api.PageView
import dev.merqadyn.api.sync.ChangeLogService
import dev.merqadyn.api.merchant.MerchantRepository
import org.springframework.stereotype.Service
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val changeLogService: ChangeLogService,
    private val merchantRepository: MerchantRepository,
) {
    @Transactional(readOnly = true)
    fun list(merchantId: UUID): List<ProductView> =
        page(merchantId, 0, 200).items

    @Transactional(readOnly = true)
    fun page(merchantId: UUID, page: Int, size: Int): PageView<ProductView> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)
        val slice = productRepository.findByMerchantIdOrderByNameAscIdAsc(
            merchantId,
            PageRequest.of(safePage, safeSize),
        )
        return PageView(slice.content.map { it.toView() }, safePage, safeSize, slice.hasNext())
    }

    @Transactional
    fun create(merchantId: UUID, request: CreateProductRequest): ProductView {
        if (!merchantRepository.existsById(merchantId)) {
            throw NotFoundException("Merchant $merchantId was not found")
        }
        if (productRepository.existsByMerchantIdAndSkuIgnoreCase(merchantId, request.sku.trim())) {
            throw BusinessRuleException("A product with SKU ${request.sku.trim()} already exists")
        }
        if (request.price > MAX_PRICE) throw BusinessRuleException("Product price is too large")
        val product = ProductEntity(
            merchantId = merchantId,
            sku = request.sku.trim().uppercase(),
            name = request.name.trim(),
            category = request.category.trim(),
            unit = request.unit.trim().lowercase(),
            price = request.price.setScale(2, RoundingMode.HALF_UP),
            updatedAt = Instant.now(),
        )
        val saved = productRepository.saveAndFlush(product)
        changeLogService.append(
            merchantId = merchantId,
            entityType = "PRODUCT",
            entityId = saved.id,
            operation = "CREATED",
            entityVersion = saved.version,
            payload = saved.toView(),
        )
        return saved.toView()
    }

    fun createFromMutation(merchantId: UUID, entityId: UUID?, payload: JsonNode): ProductEntity {
        val sku = payload.requiredText("sku", 64)
        if (productRepository.existsByMerchantIdAndSkuIgnoreCase(merchantId, sku)) {
            throw BusinessRuleException("A product with SKU $sku already exists")
        }
        val price = payload.path("price").takeUnless { it.isMissingNode || it.isNull }?.decimalValue()
            ?: throw BusinessRuleException("Product price is required")
        if (price < BigDecimal.ZERO) {
            throw BusinessRuleException("Product price cannot be negative")
        }
        if (price > MAX_PRICE) throw BusinessRuleException("Product price is too large")
        return productRepository.saveAndFlush(
            ProductEntity(
                id = entityId ?: UUID.randomUUID(),
                merchantId = merchantId,
                sku = sku.uppercase(),
                name = payload.requiredText("name", 180),
                category = payload.requiredText("category", 100),
                unit = payload.requiredText("unit", 32).lowercase(),
                price = price.setScale(2, RoundingMode.HALF_UP),
                updatedAt = Instant.now(),
            ),
        )
    }

    fun updateFromMutation(
        merchantId: UUID,
        entityId: UUID,
        baseVersion: Long?,
        payload: JsonNode,
    ): ProductUpdateOutcome {
        val product = productRepository.findByIdAndMerchantId(entityId, merchantId)
            ?: throw NotFoundException("Product $entityId was not found")
        if (!ProductConflictPolicy.canApply(baseVersion, product.version)) {
            return ProductUpdateOutcome.Conflict(product)
        }
        payload.path("name").takeUnless { it.isMissingNode || it.isNull }?.asString()?.trim()?.let {
            if (it.isBlank()) throw BusinessRuleException("Product name cannot be blank")
            if (it.length > 180) throw BusinessRuleException("Product name cannot exceed 180 characters")
            product.name = it
        }
        payload.path("category").takeUnless { it.isMissingNode || it.isNull }?.asString()?.trim()?.let {
            if (it.isBlank()) throw BusinessRuleException("Product category cannot be blank")
            if (it.length > 100) throw BusinessRuleException("Product category cannot exceed 100 characters")
            product.category = it
        }
        payload.path("unit").takeUnless { it.isMissingNode || it.isNull }?.asString()?.trim()?.let {
            if (it.isBlank()) throw BusinessRuleException("Product unit cannot be blank")
            if (it.length > 32) throw BusinessRuleException("Product unit cannot exceed 32 characters")
            product.unit = it.lowercase()
        }
        payload.path("price").takeUnless { it.isMissingNode || it.isNull }?.decimalValue()?.let {
            if (it < BigDecimal.ZERO) throw BusinessRuleException("Product price cannot be negative")
            if (it > MAX_PRICE) throw BusinessRuleException("Product price is too large")
            product.price = it.setScale(2, RoundingMode.HALF_UP)
        }
        payload.path("active").takeUnless { it.isMissingNode || it.isNull }?.let { product.active = it.asBoolean() }
        product.updatedAt = Instant.now()
        return ProductUpdateOutcome.Updated(productRepository.saveAndFlush(product))
    }

    private fun JsonNode.requiredText(field: String, maxLength: Int): String {
        val value = path(field).asString("").trim()
        if (value.isBlank()) throw BusinessRuleException("Product $field is required")
        if (value.length > maxLength) throw BusinessRuleException("Product $field cannot exceed $maxLength characters")
        return value
    }

    private companion object {
        val MAX_PRICE: BigDecimal = BigDecimal("999999999999.99")
    }
}

object ProductConflictPolicy {
    fun canApply(clientVersion: Long?, serverVersion: Long): Boolean =
        clientVersion != null && clientVersion == serverVersion
}

sealed interface ProductUpdateOutcome {
    data class Updated(val product: ProductEntity) : ProductUpdateOutcome
    data class Conflict(val product: ProductEntity) : ProductUpdateOutcome
}

fun ProductEntity.toView() = ProductView(
    id = id,
    sku = sku,
    name = name,
    category = category,
    unit = unit,
    price = price,
    active = active,
    version = version,
    updatedAt = updatedAt,
)
