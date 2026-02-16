package com.labs.ledger.application.support

import com.labs.ledger.domain.exception.InsufficientBalanceException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.system.measureTimeMillis

/**
 * ExponentialBackoffRetry 단위 테스트
 *
 * 검증 항목:
 * 1. 재시도 성공 시나리오 (1차 실패 → 2차 성공)
 * 2. 재시도 실패 시나리오 (3회 모두 실패)
 * 3. Exponential backoff 타이밍
 * 4. 재시도 불가능한 예외 (Domain Exception)
 */
class ExponentialBackoffRetryTest {

    @Test
    fun `1차 실패 후 2차 성공 - 재시도 1회`() = runTest {
        // given
        val retryPolicy = ExponentialBackoffRetry(maxAttempts = 3)
        var attemptCount = 0
        val operation: suspend () -> String = {
            attemptCount++
            if (attemptCount == 1) {
                throw RuntimeException("Transient error")
            } else {
                "Success"
            }
        }

        // when
        val result = retryPolicy.execute(operation)

        // then
        assert(result == "Success") { "Expected success, got $result" }
        assert(attemptCount == 2) { "Expected 2 attempts, got $attemptCount" }
    }

    @Test
    fun `3회 모두 실패 - null 반환`() = runTest {
        // given
        val retryPolicy = ExponentialBackoffRetry(maxAttempts = 3)
        var attemptCount = 0
        val operation: suspend () -> String = {
            attemptCount++
            throw RuntimeException("Persistent error")
        }

        // when
        val result = retryPolicy.execute(operation)

        // then
        assert(result == null) { "Expected null, got $result" }
        assert(attemptCount == 3) { "Expected 3 attempts, got $attemptCount" }
    }

    @Test
    fun `첫 시도에서 성공 - 재시도 없음`() = runTest {
        // given
        val retryPolicy = ExponentialBackoffRetry(maxAttempts = 3)
        var attemptCount = 0
        val operation: suspend () -> String = {
            attemptCount++
            "Immediate success"
        }

        // when
        val result = retryPolicy.execute(operation)

        // then
        assert(result == "Immediate success")
        assert(attemptCount == 1) { "Expected 1 attempt, got $attemptCount" }
    }

    @Test
    fun `Exponential backoff 타이밍 검증`() = runBlocking {
        // given
        val retryPolicy = ExponentialBackoffRetry(
            maxAttempts = 3,
            initialDelayMs = 100,
            maxDelayMs = 1000
        )
        var attemptCount = 0
        val operation: suspend () -> String = {
            attemptCount++
            throw RuntimeException("Error")
        }

        // when
        val duration = measureTimeMillis {
            retryPolicy.execute(operation)
        }

        // then
        // 예상 시간: 0ms (1차) + 100ms 대기 + 0ms (2차) + 200ms 대기 + 0ms (3차) = ~300ms
        // CI 환경 고려하여 범위 넓게 설정
        assert(duration in 200..600) {
            "Expected ~300ms (±150ms for CI), got ${duration}ms"
        }
        assert(attemptCount == 3)
    }

    @Test
    fun `Domain Exception은 재시도하지 않음`() = runTest {
        // given
        val retryPolicy = ExponentialBackoffRetry(maxAttempts = 3)
        var attemptCount = 0
        val operation: suspend () -> String = {
            attemptCount++
            throw InsufficientBalanceException("Insufficient balance")
        }

        // when
        val result = retryPolicy.execute(operation)

        // then
        assert(result == null) { "Expected null for non-retriable exception" }
        assert(attemptCount == 1) { "Domain exception should not retry, got $attemptCount attempts" }
    }

    @Test
    fun `maxDelayMs 제한 검증`() = runBlocking {
        // given
        val retryPolicy = ExponentialBackoffRetry(
            maxAttempts = 5,
            initialDelayMs = 100,
            maxDelayMs = 200  // 최대 200ms로 제한
        )
        var attemptCount = 0
        val operation: suspend () -> String = {
            attemptCount++
            throw RuntimeException("Error")
        }

        // when
        val duration = measureTimeMillis {
            retryPolicy.execute(operation)
        }

        // then
        // 예상: 100ms + 200ms (cap) + 200ms (cap) + 200ms (cap) = ~700ms
        // (지수 증가지만 maxDelayMs로 제한됨)
        // CI 환경 고려하여 범위 넓게 설정
        assert(duration in 550..950) {
            "Expected ~700ms with cap (±150ms for CI), got ${duration}ms"
        }
        assert(attemptCount == 5)
    }

    @Test
    fun `suspend 함수 재시도 검증`() = runTest {
        // given
        val retryPolicy = ExponentialBackoffRetry(maxAttempts = 3)
        val mockOperation: suspend () -> String = mockk()

        coEvery { mockOperation() } throws RuntimeException("Error 1") andThenThrows
            RuntimeException("Error 2") andThen "Success"

        // when
        val result = retryPolicy.execute(mockOperation)

        // then
        assert(result == "Success")
        coVerify(exactly = 3) { mockOperation() }
    }

    @Test
    fun `null 반환 케이스 - 모든 재시도 소진`() = runTest {
        // given
        val retryPolicy = ExponentialBackoffRetry(maxAttempts = 2)
        val operation: suspend () -> Int = {
            throw IllegalStateException("Consistent failure")
        }

        // when
        val result = retryPolicy.execute(operation)

        // then
        assert(result == null) { "All retries exhausted should return null" }
    }
}
