package com.labs.ledger.adapter.`in`.web

import com.labs.ledger.adapter.`in`.web.dto.ErrorResponse
import com.labs.ledger.adapter.`in`.web.dto.FieldError
import com.labs.ledger.domain.exception.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException

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

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error(e) { "Unexpected error: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(error = "INTERNAL_ERROR", message = "An unexpected error occurred", traceId = getTraceId()))
    }
}
