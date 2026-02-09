package com.labs.ledger.adapter.`in`.web

import com.labs.ledger.adapter.`in`.web.dto.ErrorResponse
import com.labs.ledger.domain.exception.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException::class)
    fun handleAccountNotFound(e: AccountNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(error = "ACCOUNT_NOT_FOUND", message = e.message ?: "Account not found"))
    }

    @ExceptionHandler(InsufficientBalanceException::class)
    fun handleInsufficientBalance(e: InsufficientBalanceException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "INSUFFICIENT_BALANCE", message = e.message ?: "Insufficient balance"))
    }

    @ExceptionHandler(InvalidAccountStatusException::class)
    fun handleInvalidAccountStatus(e: InvalidAccountStatusException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "INVALID_ACCOUNT_STATUS", message = e.message ?: "Invalid account status"))
    }

    @ExceptionHandler(InvalidAmountException::class)
    fun handleInvalidAmount(e: InvalidAmountException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "INVALID_AMOUNT", message = e.message ?: "Invalid amount"))
    }

    @ExceptionHandler(DuplicateTransferException::class)
    fun handleDuplicateTransfer(e: DuplicateTransferException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(error = "DUPLICATE_TRANSFER", message = e.message ?: "Duplicate transfer"))
    }

    @ExceptionHandler(OptimisticLockException::class)
    fun handleOptimisticLock(e: OptimisticLockException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(error = "OPTIMISTIC_LOCK_FAILED", message = e.message ?: "Concurrent modification detected"))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "INVALID_REQUEST", message = e.message ?: "Invalid request"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(error = "INTERNAL_ERROR", message = "An unexpected error occurred"))
    }
}
