package com.labs.ledger.adapter.`in`.web

import com.labs.ledger.domain.exception.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.server.MethodNotAllowedException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `DomainException - AccountNotFoundException`() {
        // given
        val exception = AccountNotFoundException("Account 123 not found")

        // when
        val response = handler.handleDomainException(exception)

        // then
        assert(response.statusCode == HttpStatus.NOT_FOUND)
        assert(response.body?.error == "ACCOUNT_NOT_FOUND")
        assert(response.body?.message == "Account 123 not found")
    }

    @Test
    fun `DomainException - InsufficientBalanceException`() {
        // given
        val exception = InsufficientBalanceException("Insufficient balance")

        // when
        val response = handler.handleDomainException(exception)

        // then
        assert(response.statusCode == HttpStatus.BAD_REQUEST)
        assert(response.body?.error == "INSUFFICIENT_BALANCE")
    }

    @Test
    fun `DomainException - DuplicateTransferException`() {
        // given
        val exception = DuplicateTransferException("Duplicate transfer")

        // when
        val response = handler.handleDomainException(exception)

        // then
        assert(response.statusCode == HttpStatus.CONFLICT)
        assert(response.body?.error == "DUPLICATE_TRANSFER")
    }

    @Test
    fun `DomainException - OptimisticLockException`() {
        // given
        val exception = OptimisticLockException("Optimistic lock failed")

        // when
        val response = handler.handleDomainException(exception)

        // then
        assert(response.statusCode == HttpStatus.CONFLICT)
        assert(response.body?.error == "OPTIMISTIC_LOCK_FAILED")
    }

    @Test
    fun `IllegalArgumentException 처리`() {
        // given
        val exception = IllegalArgumentException("Invalid argument")

        // when
        val response = handler.handleIllegalArgument(exception)

        // then
        assert(response.statusCode == HttpStatus.BAD_REQUEST)
        assert(response.body?.error == "INVALID_REQUEST")
        assert(response.body?.message == "Invalid argument")
    }

    @Test
    fun `ServerWebInputException 처리 - 잘못된 JSON`() {
        // given
        val exception = ServerWebInputException("Invalid JSON format")

        // when
        val response = handler.handleServerWebInputException(exception)

        // then
        assert(response.statusCode == HttpStatus.BAD_REQUEST)
        assert(response.body?.error == "INVALID_INPUT")
        assert(response.body?.message?.contains("Invalid JSON") == true)
    }

    @Test
    fun `DataIntegrityViolationException 처리 - idempotency key 중복`() {
        // given
        val exception = DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"transfers_idempotency_key_key\""
        )

        // when
        val response = handler.handleDataIntegrityViolationException(exception)

        // then
        assert(response.statusCode == HttpStatus.CONFLICT)
        assert(response.body?.error == "DUPLICATE_TRANSFER")
    }

    @Test
    fun `DataIntegrityViolationException 처리 - 일반 제약 위반은 DB 오류`() {
        // given
        val exception = DataIntegrityViolationException("violates check constraint check_amount_positive")

        // when
        val response = handler.handleDataIntegrityViolationException(exception)

        // then
        assert(response.statusCode == HttpStatus.SERVICE_UNAVAILABLE)
        assert(response.body?.error == "DATABASE_ERROR")
    }

    @Test
    fun `DataAccessException 처리 - 데이터베이스 오류`() {
        // given
        val exception = object : DataAccessException("Database connection failed") {}

        // when
        val response = handler.handleDataAccessException(exception)

        // then
        assert(response.statusCode == HttpStatus.SERVICE_UNAVAILABLE)
        assert(response.body?.error == "DATABASE_ERROR")
        assert(response.body?.message == "Database service is temporarily unavailable")
    }

    @Test
    fun `MethodNotAllowedException 처리 - POST만 허용`() {
        // given
        val exception = MethodNotAllowedException(
            HttpMethod.GET,
            setOf(HttpMethod.POST)
        )

        // when
        val response = handler.handleMethodNotAllowed(exception)

        // then
        assert(response.statusCode == HttpStatus.METHOD_NOT_ALLOWED)
        assert(response.body?.error == "METHOD_NOT_ALLOWED")
        assert(response.headers["Allow"]?.contains("POST") == true)
        assert(response.body?.message?.contains("GET") == true)
    }

    @Test
    fun `MethodNotAllowedException 처리 - 여러 메서드 허용`() {
        // given
        val exception = MethodNotAllowedException(
            HttpMethod.DELETE,
            setOf(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT)
        )

        // when
        val response = handler.handleMethodNotAllowed(exception)

        // then
        assert(response.statusCode == HttpStatus.METHOD_NOT_ALLOWED)
        val allowHeader = response.headers["Allow"]?.firstOrNull()
        assert(allowHeader?.contains("GET") == true)
        assert(allowHeader?.contains("POST") == true)
        assert(allowHeader?.contains("PUT") == true)
    }

    @Test
    fun `ResponseStatusException 처리 - 404`() {
        // given
        val exception = ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found")

        // when
        val response = handler.handleResponseStatusException(exception)

        // then
        assert(response.statusCode == HttpStatus.NOT_FOUND)
        assert(response.body?.message == "Resource not found")
    }

    @Test
    fun `ResponseStatusException 처리 - 403`() {
        // given
        val exception = ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")

        // when
        val response = handler.handleResponseStatusException(exception)

        // then
        assert(response.statusCode == HttpStatus.FORBIDDEN)
        assert(response.body?.message == "Access denied")
    }

    @Test
    fun `Generic Exception 처리`() {
        // given
        val exception = RuntimeException("Unexpected error")

        // when
        val response = handler.handleGenericException(exception)

        // then
        assert(response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR)
        assert(response.body?.error == "INTERNAL_ERROR")
        assert(response.body?.message == "An unexpected error occurred")
    }

    @Test
    fun `NullPointerException 처리`() {
        // given
        val exception = NullPointerException("Null value encountered")

        // when
        val response = handler.handleGenericException(exception)

        // then
        assert(response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR)
        assert(response.body?.error == "INTERNAL_ERROR")
    }

    @Test
    fun `모든 예외 응답에 error 필드 포함`() {
        // given
        val exceptions = listOf(
            AccountNotFoundException("test"),
            InsufficientBalanceException("test"),
            IllegalArgumentException("test"),
            ServerWebInputException("test"),
            DataIntegrityViolationException("duplicate key value violates unique constraint \"transfers_idempotency_key_key\""),
            object : DataAccessException("test") {},
            RuntimeException("test")
        )

        // when & then
        exceptions.forEach { exception ->
            val response = when (exception) {
                is AccountNotFoundException -> handler.handleDomainException(exception)
                is IllegalArgumentException -> handler.handleIllegalArgument(exception)
                is ServerWebInputException -> handler.handleServerWebInputException(exception)
                is DataIntegrityViolationException -> handler.handleDataIntegrityViolationException(exception)
                is DataAccessException -> handler.handleDataAccessException(exception)
                else -> handler.handleGenericException(exception)
            }

            assert(response.body?.error != null) {
                "Error field should not be null for ${exception::class.simpleName}"
            }
        }
    }
}
