/**
 * k6 부하 테스트: 혼합 워크로드
 *
 * 목적: 실제 운영 환경 시뮬레이션
 * - 계좌 생성, 조회, 입금, 이체를 혼합하여 실행
 */

import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import {
  createAccount,
  getAccount,
  deposit,
  transfer,
  randomAmount,
  randomTwoIndices,
} from './common.js';

// ====================
// 커스텀 메트릭
// ====================

const operationCounts = {
  create: new Counter('op_create_account'),
  read: new Counter('op_read_account'),
  deposit: new Counter('op_deposit'),
  transfer: new Counter('op_transfer'),
};

// ====================
// 테스트 옵션
// ====================

export const options = {
  stages: [
    { duration: '30s', target: 15 },  // Warm-up
    { duration: '2m', target: 40 },   // Ramp-up
    { duration: '3m', target: 40 },   // Sustain
    { duration: '30s', target: 0 },   // Cool-down
  ],
  thresholds: {
    'http_req_duration': ['p(95)<600', 'p(99)<1200'],
    'http_req_failed': ['rate<0.02'],  // 혼합 워크로드는 2% 허용
  },
};

// ====================
// Setup: 초기 계좌 생성
// ====================

export function setup() {
  console.log('Setup: 혼합 워크로드 테스트용 초기 계좌 생성');

  const accounts = [];

  // 초기 계좌 10개 생성
  for (let i = 0; i < 10; i++) {
    const account = createAccount(`MixedTest-User-${i}`, 5000);
    console.log(`초기 계좌 생성: ID ${account.id}`);
    accounts.push({ id: account.id });
  }

  return { accounts };
}

// ====================
// 메인 테스트: 랜덤 작업 실행
// ====================

export default function (data) {
  const accounts = data.accounts;

  // 랜덤하게 작업 선택 (가중치)
  const operation = weightedRandomOperation();

  switch (operation) {
    case 'create':
      performCreateAccount();
      break;
    case 'read':
      performReadAccount(accounts);
      break;
    case 'deposit':
      performDeposit(accounts);
      break;
    case 'transfer':
      performTransfer(accounts);
      break;
  }

  sleep(Math.random() * 0.2); // 0~200ms 대기
}

// ====================
// 작업 함수들
// ====================

function performCreateAccount() {
  const ownerName = `DynamicUser-${Date.now()}-${__VU}-${__ITER}`;
  const account = createAccount(ownerName, randomAmount(10000));

  if (account && account.id) {
    operationCounts.create.add(1);
    check(account, {
      'account created': (a) => a.id > 0,
    });
  }
}

function performReadAccount(accounts) {
  const account = accounts[Math.floor(Math.random() * accounts.length)];
  const result = getAccount(account.id);

  if (result && result.id) {
    operationCounts.read.add(1);
    check(result, {
      'account read successfully': (a) => a.id === account.id,
    });
  }
}

function performDeposit(accounts) {
  const account = accounts[Math.floor(Math.random() * accounts.length)];
  const amount = randomAmount(3000) + 100;

  const result = deposit(account.id, amount);

  if (result && result.balance) {
    operationCounts.deposit.add(1);
    check(result, {
      'deposit successful': (r) => r.id === account.id,
    });
  }
}

function performTransfer(accounts) {
  if (accounts.length < 2) {
    return; // 최소 2개 계좌 필요
  }

  const [fromIdx, toIdx] = randomTwoIndices(accounts.length);
  const fromAccount = accounts[fromIdx];
  const toAccount = accounts[toIdx];
  const amount = randomAmount(1000);

  const result = transfer(fromAccount.id, toAccount.id, amount);

  if (result && result.id) {
    operationCounts.transfer.add(1);
    check(result, {
      'transfer successful': (r) => r.status === 'COMPLETED' || r.status === 'PENDING',
    });
  }
}

// ====================
// 가중치 기반 랜덤 작업 선택
// ====================

function weightedRandomOperation() {
  const rand = Math.random() * 100;

  // 가중치: 계좌 생성 10%, 조회 30%, 입금 30%, 이체 30%
  if (rand < 10) {
    return 'create';
  } else if (rand < 40) {
    return 'read';
  } else if (rand < 70) {
    return 'deposit';
  } else {
    return 'transfer';
  }
}

// ====================
// 요약 출력
// ====================

export function handleSummary(data) {
  console.log('========================================');
  console.log('혼합 워크로드 테스트 결과');
  console.log('========================================');

  const metrics = data.metrics;

  console.log('작업 분포:');
  if (metrics.op_create_account) {
    console.log(`  계좌 생성: ${metrics.op_create_account.values.count}회`);
  }
  if (metrics.op_read_account) {
    console.log(`  계좌 조회: ${metrics.op_read_account.values.count}회`);
  }
  if (metrics.op_deposit) {
    console.log(`  입금: ${metrics.op_deposit.values.count}회`);
  }
  if (metrics.op_transfer) {
    console.log(`  이체: ${metrics.op_transfer.values.count}회`);
  }

  if (metrics.http_req_duration) {
    console.log(`\n응답 시간:`);
    console.log(`  평균: ${metrics.http_req_duration.values.avg.toFixed(2)}ms`);
    console.log(`  p95: ${metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
    console.log(`  p99: ${metrics.http_req_duration.values['p(99)'].toFixed(2)}ms`);
  }

  console.log('========================================');

  return {
    'stdout': JSON.stringify(data, null, 2),
  };
}
