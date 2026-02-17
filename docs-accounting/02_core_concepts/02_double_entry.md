# 02-2. 핵심 개념: 복식부기 (Double-Entry Bookkeeping)

## 1. 회계 원칙

**복식부기(Double-Entry Bookkeeping)** 는 모든 회계 시스템의 근간을 이루는 가장 중요한 원리입니다. 이 원칙의 핵심은 **"모든 거래는 반드시 차변(Debit)과 대변(Credit)으로 동시에, 같은 금액으로 기록되어야 한다"** 는 것입니다.

- **차변 (Debit):** 자산의 증가 또는 부채/자본의 감소를 의미합니다. (왼쪽)
- **대변 (Credit):** 자산의 감소 또는 부채/자본의 증가를 의미합니다. (오른쪽)

이 원칙 덕분에 "대차평균의 원리"가 유지되며, 시스템 전체의 자금은 절대 저절로 생성되거나 사라지지 않고 단지 이동할 뿐이라는 점을 보장할 수 있습니다.

## 2. 기술적 구현

우리 시스템은 `LedgerEntry.kt` 와 `LedgerEntryType.kt` 를 통해 복식부기 원칙을 구현합니다.

하나의 `Transfer`(자금 이체) 거래가 발생하면, 시스템은 **원자적(atomic)으로** 최소 두 개의 `LedgerEntry`(원장 항목)를 생성합니다.

- **출금 계좌:** 자산의 감소 -> **대변 (Credit)** 항목 기록
- **입금 계좌:** 자산의 증가 -> **차변 (Debit)** 항목 기록

```kotlin
// src/main/kotlin/com/labs/ledger/domain/model/LedgerEntryType.kt
enum class LedgerEntryType {
    DEBIT,  // 차변
    CREDIT  // 대변
}

// src/main/kotlin/com/labs/ledger/domain/model/LedgerEntry.kt (일부)
data class LedgerEntry(
    val id: Long,
    val accountId: Long,
    val transferId: Long,
    val amount: BigDecimal,
    val type: LedgerEntryType, // DEBIT or CREDIT
    ...
)
```

예를 들어, 계좌 A에서 계좌 B로 100원을 이체하는 경우:

1.  계좌 A에 대한 `LedgerEntry` 생성: `{ accountId: A, amount: 100, type: CREDIT }`
2.  계좌 B에 대한 `LedgerEntry` 생성: `{ accountId: B, amount: 100, type: DEBIT }`

이 두 항목은 동일한 `transferId`를 공유하며, 항상 하나의 트랜잭션(Transaction)으로 묶여 처리됩니다. 따라서 둘 중 하나만 성공하거나 실패하는 경우는 절대 발생하지 않습니다. 이를 통해 시스템은 항상 데이터 정합성을 유지합니다.
