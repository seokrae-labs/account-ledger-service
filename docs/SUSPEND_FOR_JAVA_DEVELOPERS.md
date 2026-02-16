# Kotlin Suspend 함수 - Java 개발자를 위한 가이드

> **대상 독자**: 3년 이상 Java/Spring 경력자
> **목표**: CompletableFuture/Reactor 경험을 바탕으로 Kotlin Coroutines 빠르게 이해
> **작성일**: 2026-02-16
> **관련 Issue**: [#22](https://github.com/seokrae-labs/account-ledger-service/issues/22)

## 📋 목차
1. [Java 개발자의 Pain Points](#1-java-개발자의-pain-points)
2. [Suspend의 해결책](#2-suspend의-해결책)
3. [핵심 개념 비교표](#3-핵심-개념-비교표)
4. [Virtual Threads vs Coroutines](#4-virtual-threads-vs-coroutines)
5. [마이그레이션 가이드](#5-마이그레이션-가이드)
6. [실무 체크리스트](#6-실무-체크리스트)

---

## 1. Java 개발자의 Pain Points

### 1.1 Blocking I/O (JDBC)의 문제점

```java
// Traditional Java - Blocking
@RestController
public class AccountController {

    @GetMapping("/accounts/{id}")
    public Account getAccount(@PathVariable Long id) {
        return accountRepository.findById(id);  // ❌ Thread blocks here
    }
}
```

**문제**:
- 스레드가 DB 응답을 기다리는 동안 **idle 상태**
- 동시 요청 = 스레드 수 (스레드 풀 고갈)
- 1000 concurrent users = 1000 threads needed

**성능**:
```
Thread Pool: 200개
Request: 1000개/sec
DB Response Time: 50ms
→ 결과: 800개 요청 대기 (Queue), 응답 지연
```

---

### 1.2 CompletableFuture의 Callback Hell

```java
// Java CompletableFuture
public CompletableFuture<TransferResponse> transfer(TransferRequest req) {
    return accountRepository.findByIdAsync(req.fromAccountId())
        .thenCompose(fromAccount ->                    // Callback 1
            accountRepository.findByIdAsync(req.toAccountId())
                .thenApply(toAccount ->                // Callback 2
                    Pair.of(fromAccount, toAccount)
                )
        )
        .thenCompose(accounts -> {                     // Callback 3
            Account from = accounts.getFirst();
            Account to = accounts.getSecond();

            from.withdraw(req.amount());
            to.deposit(req.amount());

            return accountRepository.saveAsync(from)
                .thenCompose(savedFrom ->              // Callback 4
                    accountRepository.saveAsync(to)
                        .thenApply(savedTo ->          // Callback 5
                            new TransferResponse(savedFrom, savedTo)
                        )
                );
        })
        .exceptionally(ex -> {                         // Error handling
            log.error("Transfer failed", ex);
            throw new TransferException(ex);
        });
}
```

**문제**:
- ❌ **Callback Hell**: 5단계 중첩
- ❌ **가독성 저하**: 비즈니스 로직이 callback에 묻힘
- ❌ **예외 처리 복잡**: `exceptionally`, `handle` 체이닝
- ❌ **디버깅 어려움**: Stack trace 단절

---

### 1.3 Project Reactor의 높은 학습 곡선

```java
// Spring WebFlux - Reactor
@RestController
public class TransferController {

    @PostMapping("/transfers")
    public Mono<TransferResponse> transfer(@RequestBody TransferRequest req) {
        return Mono.zip(
                accountRepository.findById(req.fromAccountId()),
                accountRepository.findById(req.toAccountId())
            )
            .flatMap(tuple -> {
                Account from = tuple.getT1();  // 😕 getT1()? getT2()?
                Account to = tuple.getT2();

                from.withdraw(req.amount());
                to.deposit(req.amount());

                return Mono.zip(
                    accountRepository.save(from),
                    accountRepository.save(to)
                );
            })
            .map(tuple -> new TransferResponse(tuple.getT1(), tuple.getT2()))
            .onErrorMap(ex -> new TransferException(ex));
    }
}
```

**문제**:
- ❌ **복잡한 API**: `flatMap`, `zipWith`, `switchIfEmpty`, `defer`...
- ❌ **Tuple Hell**: `getT1()`, `getT2()`... (가독성 저하)
- ❌ **Operator 선택 어려움**: `map` vs `flatMap`? `zip` vs `zipWith`?
- ❌ **Hot vs Cold**: Mono/Flux 동작 이해 필요
- ❌ **학습 곡선**: 팀원 온보딩 비용 높음

---

## 2. Suspend의 해결책

### 2.1 Kotlin Suspend - 동기 코드처럼 작성

```kotlin
// Kotlin Coroutines - 동일한 로직, 훨씬 간단
@RestController
class TransferController(
    private val accountRepository: AccountRepository
) {

    @PostMapping("/transfers")
    suspend fun transfer(@RequestBody req: TransferRequest): TransferResponse {
        // ✅ 동기 코드처럼 읽힘 (실제로는 비동기)
        val fromAccount = accountRepository.findById(req.fromAccountId)
        val toAccount = accountRepository.findById(req.toAccountId)

        fromAccount.withdraw(req.amount)
        toAccount.deposit(req.amount)

        accountRepository.save(fromAccount)
        accountRepository.save(toAccount)

        return TransferResponse(fromAccount, toAccount)
    }
}
```

**장점**:
- ✅ **동기 코드처럼 작성**: top-to-bottom 순차 읽기
- ✅ **try-catch 가능**: 일반 예외 처리 방식
- ✅ **가독성**: Callback Hell 없음
- ✅ **성능**: Non-blocking (Reactor와 동일)

---

### 2.2 코드 비교: 세 가지 방식

#### Scenario: 계좌 조회 → 잔액 검증 → 이체 → 저장

**1) Java Blocking (JDBC)**
```java
public Transfer transfer(TransferRequest req) {
    Account from = accountRepo.findById(req.fromAccountId());  // Block 1
    Account to = accountRepo.findById(req.toAccountId());      // Block 2

    from.withdraw(req.amount());
    to.deposit(req.amount());

    accountRepo.save(from);                                    // Block 3
    accountRepo.save(to);                                      // Block 4
    return transferRepo.save(new Transfer(...));               // Block 5
}
// ⏱️ Total: 5 blocking calls
```

**2) Java CompletableFuture**
```java
public CompletableFuture<Transfer> transfer(TransferRequest req) {
    return accountRepo.findByIdAsync(req.fromAccountId())
        .thenCombineAsync(
            accountRepo.findByIdAsync(req.toAccountId()),
            (from, to) -> {
                from.withdraw(req.amount());
                to.deposit(req.amount());
                return Pair.of(from, to);
            }
        )
        .thenCompose(pair ->
            accountRepo.saveAsync(pair.getFirst())
                .thenCombineAsync(
                    accountRepo.saveAsync(pair.getSecond()),
                    (savedFrom, savedTo) ->
                        transferRepo.saveAsync(new Transfer(...))
                )
        );
}
// 😵 Callback Hell: 4-level nesting
```

**3) Kotlin Suspend**
```kotlin
suspend fun transfer(req: TransferRequest): Transfer {
    val from = accountRepo.findById(req.fromAccountId)  // Suspend 1
    val to = accountRepo.findById(req.toAccountId)      // Suspend 2

    from.withdraw(req.amount)
    to.deposit(req.amount)

    accountRepo.save(from)                              // Suspend 3
    accountRepo.save(to)                                // Suspend 4
    return transferRepo.save(Transfer(...))             // Suspend 5
}
// ✅ 동기처럼, 비동기로 실행
```

**Result**:
- **가독성**: Blocking = Suspend > CompletableFuture
- **성능**: CompletableFuture ≈ Suspend > Blocking
- **학습 비용**: Suspend < Blocking < CompletableFuture

---

### 2.3 예외 처리 비교

**Java CompletableFuture**
```java
return accountRepo.findByIdAsync(id)
    .thenApply(account -> account.withdraw(amount))
    .exceptionally(ex -> {
        if (ex instanceof InsufficientBalanceException) {
            // handle
        } else if (ex instanceof AccountNotFoundException) {
            // handle
        }
        return null;
    })
    .handle((result, ex) -> {
        // cleanup
    });
```

**Kotlin Suspend**
```kotlin
try {
    val account = accountRepo.findById(id)
    account.withdraw(amount)
} catch (ex: InsufficientBalanceException) {
    // handle
} catch (ex: AccountNotFoundException) {
    // handle
} finally {
    // cleanup
}
```

✅ **Suspend는 일반 try-catch 사용 가능**

---

## 3. 핵심 개념 비교표

### 3.1 API 비교

| 기능 | Java Blocking | CompletableFuture | Reactor | Kotlin Suspend |
|------|--------------|-------------------|---------|---------------|
| **비동기 API** | N/A | `CompletableFuture<T>` | `Mono<T>` | `suspend fun` |
| **Callback** | N/A | `thenApply`, `thenCompose` | `map`, `flatMap` | ❌ 불필요 |
| **예외 처리** | `try-catch` | `exceptionally`, `handle` | `onErrorMap`, `onErrorResume` | `try-catch` |
| **병렬 실행** | `ExecutorService` | `thenCombineAsync` | `Mono.zip` | `async/await` |
| **순차 실행** | 기본 | `thenCompose` 체인 | `flatMap` 체인 | 기본 |
| **가독성** | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| **성능** | ⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

---

### 3.2 동시성 모델

| 특성 | Java Threads | CompletableFuture | Virtual Threads (21+) | Kotlin Coroutines |
|------|-------------|-------------------|----------------------|-------------------|
| **스레드 매핑** | 1:1 (OS Thread) | M:N (Thread Pool) | M:N (Virtual) | M:N (Coroutine) |
| **메모리** | 1MB/thread | Shared pool | ~1KB/thread | ~1KB/coroutine |
| **동시 작업** | ~수천 | ~수만 | ~수백만 | ~수백만 |
| **Context Switch** | 느림 (OS) | 보통 | 빠름 (JVM) | 매우 빠름 (in-memory) |
| **Cancellation** | `interrupt()` | `cancel()` | `interrupt()` | `cancel()` |

---

### 3.3 학습 곡선

```
난이도 (낮음 → 높음):

Java Blocking ━━━━━╸ (Simple, 익숙)
                ↓
Kotlin Suspend ━━━━━━╸ (약간의 키워드 학습)
                ↓
CompletableFuture ━━━━━━━━━╸ (Callback Hell)
                ↓
Project Reactor ━━━━━━━━━━━━━╸ (Operator 70+, Hot/Cold)
```

---

## 4. Virtual Threads vs Coroutines

### 4.1 동작 방식 비교

#### Java Virtual Threads (JDK 21+)
```java
// Virtual Thread (Platform Thread 위에서 실행)
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> {
        String data = blockingHttpCall();  // ✅ Blocking OK
        process(data);
    });
}
```

**특징**:
- JVM이 OS Thread 위에 Virtual Thread 스케줄링
- **Blocking call도 OK**: Virtual Thread만 block, Platform Thread는 계속 사용
- **기존 코드 호환**: JDBC, Blocking I/O 그대로 사용 가능

---

#### Kotlin Coroutines
```kotlin
// Coroutine (언어 레벨 지원)
coroutineScope {
    launch {
        val data = suspendingHttpCall()  // ✅ suspend 필요
        process(data)
    }
}
```

**특징**:
- 컴파일러가 suspend 함수를 state machine으로 변환
- **Non-blocking만 가능**: Blocking call은 `Dispatchers.IO` 필요
- **언어 통합**: `suspend` 키워드, structured concurrency

---

### 4.2 성능 특성

| 벤치마크 시나리오 | Virtual Threads | Kotlin Coroutines |
|-----------------|----------------|-------------------|
| **순수 I/O (R2DBC)** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ (약간 우세) |
| **Mixed Blocking (JDBC)** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ (Dispatcher 필요) |
| **메모리 사용량** | ~100KB/thread | ~1KB/coroutine |
| **Context Switch** | JVM 스케줄링 | In-memory (더 빠름) |

**결론**:
- **Pure Reactive**: Coroutines 약간 우세
- **레거시 코드 마이그레이션**: Virtual Threads 유리 (JDBC 그대로 사용)

---

### 4.3 생태계 성숙도 (2026년 기준)

| 항목 | Virtual Threads | Kotlin Coroutines |
|------|----------------|-------------------|
| **출시 시기** | 2023년 (JDK 21) | 2018년 (stable) |
| **Spring 지원** | 🟡 일부 (3.2+) | ✅ 완전 지원 |
| **R2DBC 지원** | ⚠️ 불필요 (JDBC 사용) | ✅ Native |
| **라이브러리 호환** | ✅ 모든 Java 라이브러리 | 🟡 suspend 라이브러리 필요 |
| **디버깅** | ✅ 기존 도구 사용 | 🟡 Coroutine 디버거 필요 |

---

### 4.4 선택 가이드

**Virtual Threads를 선택하라:**
- ✅ 레거시 JDBC 코드가 많음
- ✅ Java 21+ 사용 가능
- ✅ 빠른 마이그레이션 필요 (코드 변경 최소)

**Kotlin Coroutines를 선택하라:**
- ✅ 새 프로젝트 (Greenfield)
- ✅ Reactive Stack (R2DBC, MongoDB Reactive)
- ✅ Kotlin 사용 중
- ✅ 언어 레벨 통합 선호

**현실적 조언**:
- 2026년 기준, **Virtual Threads는 아직 초기 단계**
- 프로덕션 안정성은 **Coroutines > Virtual Threads**
- 레거시가 없다면 **Coroutines 추천**

---

## 5. 마이그레이션 가이드

### 5.1 JDBC → R2DBC 전환

**Before (Blocking JDBC)**
```java
// Spring Data JPA
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Account findByIdForUpdate(Long id);  // SELECT ... FOR UPDATE
}

@Service
@Transactional
public class TransferService {
    public Transfer transfer(TransferRequest req) {
        Account from = accountRepo.findByIdForUpdate(req.fromAccountId());
        Account to = accountRepo.findByIdForUpdate(req.toAccountId());
        // ...
    }
}
```

**After (Non-blocking R2DBC)**
```kotlin
// Spring Data R2DBC
interface AccountRepository : CoroutineCrudRepository<Account, Long> {
    @Query("SELECT * FROM accounts WHERE id = :id FOR UPDATE")
    suspend fun findByIdForUpdate(id: Long): Account?
}

@Service
class TransferService(
    private val transactionExecutor: TransactionExecutor
) {
    suspend fun transfer(req: TransferRequest): Transfer {
        return transactionExecutor.execute {
            val from = accountRepo.findByIdForUpdate(req.fromAccountId)!!
            val to = accountRepo.findByIdForUpdate(req.toAccountId)!!
            // ...
        }
    }
}
```

**주의사항**:
- ⚠️ **@Transactional 대신 TransactionalOperator 사용** (R2DBC + Coroutine 안정성)
- ⚠️ **FOR UPDATE 쿼리는 명시적으로 작성** (CoroutineCrudRepository가 지원 안 함)
- ⚠️ **LazyLoading 없음** (R2DBC는 Eager Loading만 지원)

---

### 5.2 Spring MVC → WebFlux 전환

**Before (Spring MVC)**
```java
@RestController
public class AccountController {

    @GetMapping("/accounts/{id}")
    public Account getAccount(@PathVariable Long id) {
        return accountService.findById(id);  // Blocking
    }
}
```

**After (WebFlux + Suspend)**
```kotlin
@RestController
class AccountController(
    private val accountService: AccountService
) {

    @GetMapping("/accounts/{id}")
    suspend fun getAccount(@PathVariable id: Long): Account {
        return accountService.findById(id)  // Non-blocking
    }
}
```

**변경 사항**:
- ✅ `suspend fun` 추가
- ✅ Return type에 `Mono/Flux` 불필요 (Spring이 자동 변환)
- ✅ `@Async` 불필요

---

### 5.3 기존 Java 코드와의 상호운용성

#### Kotlin suspend → Java CompletableFuture
```kotlin
// Kotlin suspend function
suspend fun findAccount(id: Long): Account

// Java에서 호출
CompletableFuture<Account> future = FutureKt.future(
    GlobalScope.INSTANCE,
    Dispatchers.getDefault(),
    (continuation) -> accountService.findAccount(1L, continuation)
);
```

⚠️ **복잡함**: 가능하지만 권장하지 않음

---

#### Java Blocking → Kotlin suspend (Wrapper)
```kotlin
// Java Blocking code
class LegacyService {
    fun blockingCall(): String = Thread.sleep(1000).let { "result" }
}

// Kotlin Suspend Wrapper
suspend fun callLegacy(): String = withContext(Dispatchers.IO) {
    legacyService.blockingCall()  // Blocking call을 IO dispatcher로 격리
}
```

✅ **권장**: `Dispatchers.IO`로 Blocking call 격리

---

### 5.4 트랜잭션 관리 전환

**Spring MVC (JPA)**
```java
@Transactional
public void transfer() {
    // Spring이 ThreadLocal로 트랜잭션 관리
}
```

**WebFlux (R2DBC)**
```kotlin
// ❌ BAD: @Transactional은 Coroutine context 전파 불안정
@Transactional
suspend fun transfer() { ... }

// ✅ GOOD: TransactionalOperator 사용
suspend fun transfer() {
    return transactionExecutor.execute {
        // 명시적 트랜잭션 경계
    }
}
```

**Setup**:
```kotlin
@Configuration
class TransactionConfig {
    @Bean
    fun transactionExecutor(
        operator: TransactionalOperator
    ) = object : TransactionExecutor {
        override suspend fun <T> execute(block: suspend () -> T): T {
            return operator.executeAndAwait { block() }!!
        }
    }
}
```

---

## 6. 실무 체크리스트

### 6.1 마이그레이션 전 체크

#### Infrastructure
- [ ] **JDK 버전**: 17+ (Coroutines 최적화)
- [ ] **Kotlin 버전**: 1.9+ (최신 Coroutines 지원)
- [ ] **Spring Boot 버전**: 3.2+ (Coroutines 안정성)
- [ ] **데이터베이스**: PostgreSQL/MySQL/MariaDB (R2DBC 지원)

#### Dependencies
```gradle
// R2DBC
implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
implementation("org.postgresql:r2dbc-postgresql")  // 또는 r2dbc-mysql

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.+")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.8.+")
```

---

### 6.2 개발 중 체크

#### Architecture
- [ ] **Domain Models**: `suspend` 없음 (순수 함수)
- [ ] **Ports (Interfaces)**: `suspend fun` 사용
- [ ] **Adapters**: `suspend fun` + Flow → List 변환
- [ ] **Controllers**: `suspend fun` 반환

#### Best Practices
- [ ] **Mono/Flux 미노출**: `suspend fun`으로 통일
- [ ] **트랜잭션**: TransactionalOperator 사용
- [ ] **Dispatcher**: 명시하지 않음 (R2DBC는 non-blocking)
- [ ] **runBlocking**: 테스트에서만 사용

---

### 6.3 테스트 전략

```kotlin
// Unit Test (도메인 로직)
class AccountTest {
    @Test
    fun `withdraw는 순수 함수다`() {
        val account = Account(balance = 1000.toBigDecimal())
        val result = account.withdraw(500.toBigDecimal())
        assertThat(result.balance).isEqualTo(500.toBigDecimal())
        // runBlocking 불필요 (순수 함수)
    }
}

// Integration Test (suspend 함수)
@SpringBootTest
class TransferServiceTest {
    @Test
    fun `이체가 정상 동작한다`() = runBlocking {
        // runBlocking으로 suspend 함수 테스트
        val result = transferService.execute(request)
        assertThat(result.status).isEqualTo(COMPLETED)
    }
}

// API Test (WebTestClient)
@SpringBootTest(webEnvironment = RANDOM_PORT)
class TransferControllerTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `POST transfers는 비동기로 처리된다`() {
        webTestClient
            .post().uri("/api/transfers")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
        // WebTestClient가 자동으로 비동기 처리
    }
}
```

---

### 6.4 성능 검증

```kotlin
@Test
fun `동시 1000개 요청 처리 가능`() = runBlocking {
    val requests = (1..1000).map { id ->
        async {
            webTestClient.get()
                .uri("/api/accounts/$id")
                .exchange()
                .expectStatus().isOk
        }
    }

    val start = System.currentTimeMillis()
    requests.awaitAll()
    val duration = System.currentTimeMillis() - start

    // Blocking이었다면 10초 이상
    // Suspend는 1-2초 이내 완료
    assertThat(duration).isLessThan(2000)
}
```

---

## 📚 추가 학습 자료

### 프로젝트 내부 문서
- **상세 구현 분석**: `docs/SUSPEND_BEST_PRACTICES.md` (내부 구현 깊이 이해)
- **ArchUnit 규칙**: `src/test/kotlin/com/labs/ledger/architecture/SuspendFunctionRuleTest.kt`

### 공식 문서
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Spring Framework - Kotlin Coroutines](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html)
- [Spring Data R2DBC](https://docs.spring.io/spring-data/r2dbc/docs/current/reference/html/)

### 비교 분석
- [Virtual Threads vs Coroutines (JEP 444)](https://openjdk.org/jeps/444)
- [Project Loom: Modern Scalable Concurrency](https://www.youtube.com/watch?v=lKSSBvRDmTg)

---

## 🎯 요약

### Java → Kotlin Suspend 전환 시 얻는 것

| 항목 | Before (Java) | After (Kotlin Suspend) |
|------|--------------|----------------------|
| **가독성** | Callback Hell | 동기 코드처럼 |
| **성능** | Blocking → 느림 | Non-blocking → 빠름 |
| **동시성** | Thread = Request | 수만 개 동시 처리 |
| **예외 처리** | `.exceptionally()` | `try-catch` |
| **학습 곡선** | Reactor: 높음 | Suspend: 낮음 |

### 핵심 메시지
> **"Kotlin Suspend는 동기 코드의 가독성 + 비동기의 성능을 동시에 제공"**

---

**작성**: Claude Code
**검토 대상**: Java → Kotlin 마이그레이션 팀
**마지막 업데이트**: 2026-02-16
