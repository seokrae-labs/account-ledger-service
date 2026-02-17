# 05. 용어 사전 (Glossary)

이 프로젝트와 회계 도메인을 이해하는 데 필요한 주요 용어를 정리합니다.

| 용어 (Term) | 설명 (Description) | 관련 코드 |
| --- | --- | --- |
| **Ledger (원장)** | 모든 거래 기록의 완전한 집합. 시스템의 단일 진실 공급원. | - |
| **Account (계정)** | 자산(돈)의 증감을 기록하고 잔액을 관리하는 기본 단위. | `Account.kt` |
| **Journal Entry (분개)** | 하나의 거래를 차변과 대변으로 나누어 기록하는 행위. | - |
| **Ledger Entry (원장 항목)** | 시스템에서 '분개'에 해당하는 데이터 모델. | `LedgerEntry.kt` |
| **Debit (차변)** | (우리 시스템 기준) 계좌에 자산이 **증가**하는 것. '입금'에 해당. | `LedgerEntryType.DEBIT` |
| **Credit (대변)** | (우리 시스템 기준) 계좌에서 자산이 **감소**하는 것. '출금'에 해당. | `LedgerEntryType.CREDIT` |
| **Double-Entry (복식부기)** | 모든 거래를 차변과 대변에 같은 금액으로 기록하여 대차평균의 원리를 유지하는 회계 방식. | `TransferService.kt` |
| **Transaction (거래)** | 계정의 상태에 변화를 일으키는 회계상의 사건. | `Transfer.kt` |
| **Audit Trail (감사 추적)** | 거래의 처음부터 끝까지 모든 단계를 추적할 수 있도록 남겨진 기록. | `TransferAuditEvent.kt`|
| **DLQ (Dead Letter Queue)** | 처리 실패한 메시지/이벤트를 나중에 분석하고 처리할 수 있도록 격리하는 공간. | `DeadLetterEntry.kt` |
| **Optimistic Lock (낙관적 락)**| 데이터 충돌이 드물 것이라 가정하고, 실제 업데이트 시점에 버전 정보를 비교하여 데이터 정합성을 맞추는 동시성 제어 방식. |`Account.version` |
