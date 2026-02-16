package com.labs.ledger.application.support

import com.labs.ledger.domain.exception.OptimisticLockException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RetryUtilTest {

    @Test
    fun `도메인 OptimisticLockException 발생시 재시도 후 성공`() {
        var attempts = 0

        val result = runBlocking {
            retryOnOptimisticLock(maxAttempts = 3) {
                attempts++
                if (attempts < 3) {
                    throw OptimisticLockException("domain lock conflict")
                }
                "ok"
            }
        }

        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `비재시도 예외는 즉시 실패`() {
        var attempts = 0

        assertThrows<IllegalStateException> {
            runBlocking {
                retryOnOptimisticLock(maxAttempts = 3) {
                    attempts++
                    throw IllegalStateException("not retryable")
                }
            }
        }

        assertEquals(1, attempts)
    }

    @Test
    fun `재시도 최대 횟수 초과시 예외 전파`() {
        var attempts = 0

        assertThrows<OptimisticLockException> {
            runBlocking {
                retryOnOptimisticLock(maxAttempts = 3) {
                    attempts++
                    throw OptimisticLockException("still conflicting")
                }
            }
        }

        assertEquals(3, attempts)
    }
}
