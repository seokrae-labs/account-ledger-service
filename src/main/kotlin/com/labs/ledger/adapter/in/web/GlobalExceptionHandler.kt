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

    @ExceptionHandler(AccountNotFoundException::class)
    fun handleAccountNotFound(e: AccountNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn { "Account not found: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(error = "ACCOUNT_NOT_FOUND", message = e.message ?: "Account not found", traceId = getTraceId()))
    }

    @ExceptionHandler(InsufficientBalanceException::class)
    fun handleInsufficientBalance(e: InsufficientBalanceException): ResponseEntity<ErrorResponse> {
        logger.warn { "Insufficient balance: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "INSUFFICIENT_BALANCE", message = e.message ?: "Insufficient balance", traceId = getTraceId()))
    }

    @ExceptionHandler(InvalidAccountStatusException::class)
    fun handleInvalidAccountStatus(e: InvalidAccountStatusException): ResponseEntity<ErrorResponse> {
        logger.warn { "Invalid account status: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "INVALID_ACCOUNT_STATUS", message = e.message ?: "Invalid account status", traceId = getTraceId()))
    }

    @ExceptionHandler(InvalidAmountException::class)
    fun handleInvalidAmount(e: InvalidAmountException): ResponseEntity<ErrorResponse> {
        logger.warn { "Invalid amount: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "INVALID_AMOUNT", message = e.message ?: "Invalid amount", traceId = getTraceId()))
    }

    @ExceptionHandler(DuplicateTransferException::class)
    fun handleDuplicateTransfer(e: DuplicateTransferException): ResponseEntity<ErrorResponse> {
        logger.warn { "Duplicate transfer: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(error = "DUPLICATE_TRANSFER", message = e.message ?: "Duplicate transfer", traceId = getTraceId()))
    }

    @ExceptionHandler(OptimisticLockException::class)
    fun handleOptimisticLock(e: OptimisticLockException): ResponseEntity<ErrorResponse> {
        logger.warn { "Optimistic lock failed: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(error = "OPTIMISTIC_LOCK_FAILED", message = e.message ?: "Concurrent modification detected", traceId = getTraceId()))
    }

    @ExceptionHandler(InvalidTransferStatusTransitionException::class)
    fun handleInvalidTransferStatusTransition(e: InvalidTransferStatusTransitionException): ResponseEntity<ErrorResponse> {
        logger.warn { "Invalid transfer status transition: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(error = "INVALID_TRANSFER_STATUS_TRANSITION", message = e.message ?: "Invalid transfer status transition", traceId = getTraceId()))
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
