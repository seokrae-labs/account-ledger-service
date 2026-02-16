/**
 * k6 부하 테스트: 고부하 입금
 *
 * 목적: 동시 입금 처리 성능 측정
 */

import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { createAccount, deposit, randomAmount } from './common.js';

// ====================
// 커스텀 메트릭
// ====================

const depositsSuccessful = new Counter('deposits_successful');

// ====================
// 테스트 옵션
// ====================

export const options = {
  stages: [
    { duration: '20s', target: 30 },  // Ramp-up: 0→30 VUs
    { duration: '1m', target: 30 },   // Sustain: 30 VUs
    { duration: '10s', target: 0 },   // Cool-down: 30→0 VUs
  ],
  thresholds: {
    'http_req_duration': ['p(95)<400', 'p(99)<800'],
    'http_req_failed': ['rate<0.01'],
    'deposits_successful': ['count>200'],
  },
};

// ====================
// Setup: 테스트 계좌 생성
// ====================

export function setup() {
  console.log('Setup: 입금 테스트용 계좌 5개 생성');

  const accounts = [];

  for (let i = 0; i < 5; i++) {
    const account = createAccount(`DepositTest-User-${i}`, 1000);
    console.log(`계좌 생성: ID ${account.id}`);
    accounts.push({ id: account.id });
  }

  return { accounts };
}

// ====================
// 메인 테스트
// ====================

export default function (data) {
  const accounts = data.accounts;

  // 랜덤 계좌 선택
  const account = accounts[Math.floor(Math.random() * accounts.length)];

  // 랜덤 입금 (100 ~ 5000원)
  const amount = randomAmount(5000) + 100;

  const result = deposit(account.id, amount);

  if (result && result.balance >= amount) {
    depositsSuccessful.add(1);
    check(result, {
      'deposit increased balance': (r) => r.balance >= amount,
      'deposit returned valid account': (r) => r.id === account.id,
    });
  }

  sleep(Math.random() * 0.05); // 0~50ms 대기
}

// ====================
// 요약 출력
// ====================

export function handleSummary(data) {
  console.log('========================================');
  console.log('고부하 입금 테스트 결과');
  console.log('========================================');

  const metrics = data.metrics;

  if (metrics.deposits_successful) {
    console.log(`성공한 입금: ${metrics.deposits_successful.values.count}회`);
  }

  if (metrics.http_req_duration) {
    console.log(`응답 시간 p95: ${metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
  }

  console.log('========================================');

  return {
    'stdout': JSON.stringify(data, null, 2),
  };
}
