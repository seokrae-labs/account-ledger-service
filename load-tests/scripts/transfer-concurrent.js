/**
 * k6 부하 테스트: 동시 양방향 이체
 *
 * 목적: Deadlock 방지 메커니즘 검증
 * - 10개 계좌 간 랜덤 이체
 * - 양방향 이체 시도 (A→B, B→A 동시)
 * - Optimistic Locking 검증
 */

import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';
import {
  createAccount,
  deposit,
  transfer,
  randomTwoIndices,
  randomAmount,
} from './common.js';

// ====================
// 커스텀 메트릭
// ====================

const transferSuccesses = new Counter('transfer_successes');
const transferFailures = new Counter('transfer_failures');
const transferDuration = new Trend('transfer_duration_ms');

// ====================
// 테스트 옵션
// ====================

export const options = {
  stages: [
    { duration: '30s', target: 10 },  // Warm-up: 0→10 VUs
    { duration: '1m', target: 50 },   // Ramp-up: 10→50 VUs
    { duration: '2m', target: 50 },   // Sustain: 50 VUs
    { duration: '30s', target: 0 },   // Cool-down: 50→0 VUs
  ],
  thresholds: {
    'http_req_duration': ['p(95)<500', 'p(99)<1000'], // 95% < 500ms, 99% < 1s
    'http_req_failed': ['rate<0.01'],                 // 에러율 < 1%
    'transfer_successes': ['count>0'],                // 최소 1회 성공
  },
};

// ====================
// Setup: 테스트 계좌 생성
// ====================

export function setup() {
  console.log('========================================');
  console.log('Setup: 테스트 계좌 생성 시작');
  console.log('========================================');

  const accounts = [];
  const initialBalance = 10000; // 충분한 초기 잔액

  // 10개 계좌 생성
  for (let i = 0; i < 10; i++) {
    const account = createAccount(`LoadTest-User-${i}`, 0);
    console.log(`계좌 ${i + 1} 생성: ID ${account.id}`);

    // 초기 잔액 입금
    deposit(account.id, initialBalance);
    console.log(`계좌 ${account.id} 초기 잔액 입금: ${initialBalance}원`);

    accounts.push({
      id: account.id,
      ownerName: account.ownerName,
    });
  }

  console.log('========================================');
  console.log(`Setup 완료: ${accounts.length}개 계좌 생성`);
  console.log('========================================');

  return { accounts };
}

// ====================
// 메인 테스트: 랜덤 이체
// ====================

export default function (data) {
  const accounts = data.accounts;

  // 랜덤하게 2개 계좌 선택 (서로 다른 계좌)
  const [fromIdx, toIdx] = randomTwoIndices(accounts.length);
  const fromAccount = accounts[fromIdx];
  const toAccount = accounts[toIdx];

  // 랜덤 금액 (1 ~ 1000원)
  const amount = randomAmount(1000);

  const startTime = Date.now();

  // 이체 실행
  const result = transfer(fromAccount.id, toAccount.id, amount);

  const duration = Date.now() - startTime;
  transferDuration.add(duration);

  // 결과 검증
  if (result && result.id) {
    transferSuccesses.add(1);
    check(result, {
      'transfer completed': (r) => r.status === 'COMPLETED' || r.status === 'PENDING',
      'transfer has valid ID': (r) => r.id > 0,
      'transfer amount matches': (r) => r.amount === amount,
    });
  } else {
    transferFailures.add(1);
  }

  // 랜덤 대기 (0 ~ 100ms) - 실제 사용자 패턴 시뮬레이션
  sleep(Math.random() * 0.1);
}

// ====================
// Teardown: 최종 계좌 상태 확인
// ====================

export function teardown(data) {
  console.log('========================================');
  console.log('Teardown: 최종 계좌 상태 확인');
  console.log('========================================');

  // 생략: 필요 시 최종 잔액 합산 검증 추가 가능
  console.log('테스트 완료');
  console.log('========================================');
}

// ====================
// 요약 출력
// ====================

export function handleSummary(data) {
  console.log('========================================');
  console.log('동시 이체 테스트 결과 요약');
  console.log('========================================');

  const metrics = data.metrics;

  if (metrics.http_reqs) {
    console.log(`총 HTTP 요청: ${metrics.http_reqs.values.count}`);
  }

  if (metrics.transfer_successes) {
    console.log(`이체 성공: ${metrics.transfer_successes.values.count}회`);
  }

  if (metrics.transfer_failures) {
    console.log(`이체 실패: ${metrics.transfer_failures.values.count}회`);
  }

  if (metrics.http_req_duration) {
    console.log(`응답 시간 p95: ${metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
    console.log(`응답 시간 p99: ${metrics.http_req_duration.values['p(99)'].toFixed(2)}ms`);
  }

  if (metrics.http_req_failed) {
    const failRate = (metrics.http_req_failed.values.rate * 100).toFixed(2);
    console.log(`HTTP 요청 실패율: ${failRate}%`);
  }

  console.log('========================================');

  return {
    'stdout': JSON.stringify(data, null, 2),
  };
}
