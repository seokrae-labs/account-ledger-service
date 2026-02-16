package com.labs.ledger.adapter.`in`.web

import com.labs.ledger.adapter.`in`.web.dto.ErrorResponse
import com.labs.ledger.adapter.`in`.web.dto.FieldError
import com.labs.ledger.domain.exception.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.MDC
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.MethodNotAllowedException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    private fun getTraceId(): String? = MDC.get("traceId")

    @ExceptionHandler(DomainException::class)
    fun handleDomainException(e: DomainException): ResponseEntity<ErrorResponse> {
        val (status, errorCode) = when (e) {
            is AccountNotFoundException -> HttpStatus.NOT_FOUND to "ACCOUNT_NOT_FOUND"
            is InsufficientBalanceException -> HttpStatus.BAD_REQUEST to "INSUFFICIENT_BALANCE"
            is InvalidAccountStatusException -> HttpStatus.BAD_REQUEST to "INVALID_ACCOUNT_STATUS"
            is InvalidAmountException -> HttpStatus.BAD_REQUEST to "INVALID_AMOUNT"
            is DuplicateTransferException -> HttpStatus.CONFLICT to "DUPLICATE_TRANSFER"
            is OptimisticLockException -> HttpStatus.CONFLICT to "OPTIMISTIC_LOCK_FAILED"
            is InvalidTransferStatusTransitionException -> HttpStatus.CONFLICT to "INVALID_TRANSFER_STATUS_TRANSITION"
        }

        logger.warn { "$errorCode: ${e.message}" }
        return ResponseEntity
            .status(status)
            .body(ErrorResponse(error = errorCode, message = e.message ?: errorCode, traceId = getTraceId()))
    }

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidationException(e: WebExchangeBindException): ResponseEntity<ErrorResponse> {
        val fieldErrors = e.bindingResult.fieldErrors.map { fieldError ->
            FieldError(
                field = fieldError.field,
                message = fieldError.defaultMessage ?: "Validation failed",
                rejectedValue = fieldError.rejectedValue
            )
        }
        logger.warn { "Validation failed: ${fieldErrors.joinToString { "${it.field}: ${it.message}" }}" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = "VALIDATION_FAILED",
                message = "Request validation failed",
                errors = fieldErrors,
                traceId = getTraceId()
            ))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn { "Invalid request: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "INVALID_REQUEST", message = e.message ?: "Invalid request", traceId = getTraceId()))
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun handleServerWebInputException(e: ServerWebInputException): ResponseEntity<ErrorResponse> {
        logger.warn { "Invalid input: ${e.reason}" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = "INVALID_INPUT",
                message = e.reason ?: "Invalid request body or parameters",
                traceId = getTraceId()
            ))
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolationException(e: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        if (isTransferIdempotencyDuplicate(e)) {
            logger.warn { "Duplicate transfer (idempotency-key): ${e.message}" }
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse(
                    error = "DUPLICATE_TRANSFER",
                    message = "Transfer with the same idempotency key already exists",
                    traceId = getTraceId()
                ))
        }

        return handleDataAccessException(e)
    }

    @ExceptionHandler(DataAccessException::class)
    fun handleDataAccessException(e: DataAccessException): ResponseEntity<ErrorResponse> {
        logger.error(e) { "Database error: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse(
                error = "DATABASE_ERROR",
                message = "Database service is temporarily unavailable",
                traceId = getTraceId()
            ))
    }

    @ExceptionHandler(MethodNotAllowedException::class)
    fun handleMethodNotAllowed(e: MethodNotAllowedException): ResponseEntity<ErrorResponse> {
        val allowedMethods = e.supportedMethods.joinToString(", ")
        logger.warn { "Method not allowed: ${e.httpMethod} (Allowed: $allowedMethods)" }
        return ResponseEntity
            .status(HttpStatus.METHOD_NOT_ALLOWED)
            .header("Allow", allowedMethods)
            .body(ErrorResponse(
                error = "METHOD_NOT_ALLOWED",
                message = "HTTP method ${e.httpMethod} is not supported for this endpoint. Allowed methods: $allowedMethods",
                traceId = getTraceId()
            ))
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<ErrorResponse> {
        logger.warn { "Access denied: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(
                error = "ACCESS_DENIED",
                message = "Access is denied. You do not have permission to access this resource.",
                traceId = getTraceId()
            ))
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(e: ResponseStatusException): ResponseEntity<ErrorResponse> {
        logger.warn { "Response status exception: ${e.statusCode} - ${e.reason}" }
        return ResponseEntity
            .status(e.statusCode)
            .body(ErrorResponse(
                error = e.statusCode.toString().replace(" ", "_").uppercase(),
                message = e.reason ?: e.statusCode.toString(),
                traceId = getTraceId()
            ))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error(e) { "Unexpected error: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(error = "INTERNAL_ERROR", message = "An unexpected error occurred", traceId = getTraceId()))
    }

    private fun isTransferIdempotencyDuplicate(e: Throwable): Boolean {
        var current: Throwable? = e

        while (current != null) {
            val message = current.message?.lowercase().orEmpty()
            if (message.contains("transfers_idempotency_key_key")) {
                return true
            }
            if (message.contains("idempotency_key") && message.contains("duplicate")) {
                return true
            }
            current = current.cause
        }

        return false
    }
}
