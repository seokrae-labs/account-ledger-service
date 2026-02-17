# 02-1. 핵심 개념: 계정 (Account)

## 1. 회계적 정의

회계에서 **계정(Account)** 은 자산, 부채, 자본, 수익, 비용의 증감을 기록하고 관리하기 위한 기본 단위입니다. 우리 시스템에서 `Account`는 사용자의 자산을 보관하는 디지털 지갑 또는 예금 계좌와 같은 역할을 합니다.

각 계정은 독립적인 잔액(Balance)을 가지며, 모든 자금 이동의 주체가 됩니다.

## 2. 기술적 구현

시스템의 계정은 `src/main/kotlin/com/labs/ledger/domain/model/Account.kt` 도메인 모델로 표현됩니다.

```kotlin
// src/main/kotlin/com/labs/ledger/domain/model/Account.kt (일부)
data class Account(
    val id: Long,
    val balance: BigDecimal,
    val status: AccountStatus,
    val version: Int,
    ...
)
```

### 주요 속성(Properties)

- **`id`**: 각 계정을 식별하는 고유한 번호입니다.
- **`balance`**: 해당 계정의 현재 잔액입니다. `BigDecimal` 타입을 사용하여 소수점 계산의 정확도를 보장합니다. 금융 시스템에서 부동소수점(`Float`, `Double`)을 사용하는 것은 재앙을 초래할 수 있습니다.
- **`status`**: 계정의 현재 상태를 나타냅니다. (`AccountStatus.kt` 참고)
    - `ACTIVE`: 정상적으로 사용 가능한 활성 계정
    - `SUSPENDED`: 일시적으로 거래가 중지된 계정
    - `CLOSED`: 영구적으로 폐쇄된 계정
- **`version`**: 동시성 제어를 위한 속성입니다. 여러 거래가 동시에 한 계좌의 잔액을 변경하려고 할 때, 데이터가 꼬이는 것을 방지하는 낙관적 락(Optimistic Lock) 메커니즘에 사용됩니다.
