package dev.merqadyn.api.api

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class NotFoundException(message: String) : RuntimeException(message)

class BusinessRuleException(message: String) : RuntimeException(message)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException::class)
    fun notFound(exception: NotFoundException, request: HttpServletRequest): ResponseEntity<ApiProblem> =
        problem(HttpStatus.NOT_FOUND, "not_found", exception.message ?: "Resource not found", request)

    @ExceptionHandler(BusinessRuleException::class)
    fun businessRule(exception: BusinessRuleException, request: HttpServletRequest): ResponseEntity<ApiProblem> =
        problem(HttpStatus.UNPROCESSABLE_ENTITY, "business_rule", exception.message ?: "Request rejected", request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(exception: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiProblem> {
        val errors = exception.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid value") }
        return ResponseEntity.badRequest().body(
            ApiProblem(
                code = "validation_failed",
                message = "One or more fields are invalid",
                path = request.requestURI,
                fieldErrors = errors,
            ),
        )
    }

    private fun problem(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiProblem> = ResponseEntity.status(status).body(
        ApiProblem(code = code, message = message, path = request.requestURI),
    )
}
