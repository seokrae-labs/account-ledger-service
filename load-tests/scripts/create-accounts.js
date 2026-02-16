/**
 * k6 부하 테스트: 계좌 생성 Throughput
 *
 * 목적: 계좌 생성 API의 처리량 측정
 */

import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { createAccount } from './common.js';

// ====================
// 커스텀 메트릭
// ====================

const accountsCreated = new Counter('accounts_created');

// ====================
// 테스트 옵션
// ====================

export const options = {
  stages: [
    { duration: '30s', target: 20 },  // Ramp-up: 0→20 VUs
    { duration: '1m', target: 20 },   // Sustain: 20 VUs
    { duration: '10s', target: 0 },   // Cool-down: 20→0 VUs
  ],
  thresholds: {
    'http_req_duration': ['p(95)<300', 'p(99)<500'],
    'http_req_failed': ['rate<0.01'],
    'accounts_created': ['count>100'],  // 최소 100개 계좌 생성
  },
};

// ====================
// 메인 테스트
// ====================

export default function () {
  const ownerName = `User-${Date.now()}-${__VU}-${__ITER}`;
  const initialBalance = Math.floor(Math.random() * 10000);

  const account = createAccount(ownerName, initialBalance);

  if (account && account.id) {
    accountsCreated.add(1);
    check(account, {
      'account has valid ID': (a) => a.id > 0,
      'account has correct owner': (a) => a.ownerName === ownerName,
      'account has correct balance': (a) => a.balance === initialBalance,
      'account is ACTIVE': (a) => a.status === 'ACTIVE',
    });
  }

  sleep(0.1); // 100ms 대기
}

// ====================
// 요약 출력
// ====================

export function handleSummary(data) {
  console.log('========================================');
  console.log('계좌 생성 Throughput 테스트 결과');
  console.log('========================================');

  const metrics = data.metrics;

  if (metrics.accounts_created) {
    console.log(`생성된 계좌 수: ${metrics.accounts_created.values.count}개`);
  }

  if (metrics.http_req_duration) {
    console.log(`응답 시간 평균: ${metrics.http_req_duration.values.avg.toFixed(2)}ms`);
    console.log(`응답 시간 p95: ${metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
  }

  console.log('========================================');

  return {
    'stdout': JSON.stringify(data, null, 2),
  };
}
