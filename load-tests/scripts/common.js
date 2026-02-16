/**
 * k6 부하 테스트 공통 유틸리티
 *
 * Base URL, 헤더, 공통 함수 등 정의
 */

import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// ====================
// 설정
// ====================

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const HEADERS = {
  'Content-Type': 'application/json',
  'Accept': 'application/json',
};

// ====================
// API 함수
// ====================

/**
 * 계좌 생성
 * @param {string} ownerName - 계좌 소유자 이름
 * @param {number} initialBalance - 초기 잔액
 * @returns {Object} 생성된 계좌 정보
 */
export function createAccount(ownerName, initialBalance = 0) {
  const payload = JSON.stringify({
    ownerName: ownerName,
    initialBalance: initialBalance,
  });

  const res = http.post(`${BASE_URL}/api/accounts`, payload, { headers: HEADERS });

  check(res, {
    'account created successfully': (r) => r.status === 201,
  });

  return res.json();
}

/**
 * 계좌 조회
 * @param {number} accountId - 계좌 ID
 * @returns {Object} 계좌 정보
 */
export function getAccount(accountId) {
  const res = http.get(`${BASE_URL}/api/accounts/${accountId}`, { headers: HEADERS });

  check(res, {
    'account retrieved successfully': (r) => r.status === 200,
  });

  return res.json();
}

/**
 * 입금
 * @param {number} accountId - 계좌 ID
 * @param {number} amount - 입금 금액
 * @returns {Object} 입금 결과
 */
export function deposit(accountId, amount) {
  const payload = JSON.stringify({
    amount: amount,
  });

  const res = http.post(
    `${BASE_URL}/api/accounts/${accountId}/deposits`,
    payload,
    { headers: HEADERS }
  );

  check(res, {
    'deposit successful': (r) => r.status === 200,
  });

  return res.json();
}

/**
 * 이체
 * @param {number} fromAccountId - 출금 계좌 ID
 * @param {number} toAccountId - 입금 계좌 ID
 * @param {number} amount - 이체 금액
 * @param {string} idempotencyKey - 멱등성 키 (생략 시 자동 생성)
 * @returns {Object} 이체 결과
 */
export function transfer(fromAccountId, toAccountId, amount, idempotencyKey = null) {
  const key = idempotencyKey || uuidv4();

  const payload = JSON.stringify({
    fromAccountId: fromAccountId,
    toAccountId: toAccountId,
    amount: amount,
    description: `Load test transfer ${key}`,
  });

  const customHeaders = {
    ...HEADERS,
    'Idempotency-Key': key,
  };

  const res = http.post(`${BASE_URL}/api/transfers`, payload, { headers: customHeaders });

  check(res, {
    'transfer successful': (r) => r.status === 200 || r.status === 201,
  });

  return res.json();
}

// ====================
// 유틸리티 함수
// ====================

/**
 * 랜덤 금액 생성 (1 ~ max)
 * @param {number} max - 최대 금액
 * @returns {number} 랜덤 금액
 */
export function randomAmount(max = 1000) {
  return Math.floor(Math.random() * max) + 1;
}

/**
 * 랜덤 인덱스 선택
 * @param {number} arrayLength - 배열 길이
 * @returns {number} 랜덤 인덱스
 */
export function randomIndex(arrayLength) {
  return Math.floor(Math.random() * arrayLength);
}

/**
 * 두 개의 서로 다른 랜덤 인덱스 선택
 * @param {number} arrayLength - 배열 길이
 * @returns {Array<number>} [index1, index2]
 */
export function randomTwoIndices(arrayLength) {
  const index1 = randomIndex(arrayLength);
  let index2 = randomIndex(arrayLength);

  // 같은 인덱스가 나오면 다시 선택
  while (index1 === index2) {
    index2 = randomIndex(arrayLength);
  }

  return [index1, index2];
}

/**
 * 테스트 요약 출력
 * @param {Object} data - handleSummary에서 받은 데이터
 */
export function printSummary(data) {
  const metrics = data.metrics;

  console.log('============================================');
  console.log('부하 테스트 결과 요약');
  console.log('============================================');

  if (metrics.http_reqs) {
    console.log(`총 요청 수: ${metrics.http_reqs.values.count}`);
    console.log(`요청 성공률: ${metrics.http_reqs.values.rate.toFixed(2)}%`);
  }

  if (metrics.http_req_duration) {
    console.log(`응답 시간 평균: ${metrics.http_req_duration.values.avg.toFixed(2)}ms`);
    console.log(`응답 시간 p95: ${metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
    console.log(`응답 시간 p99: ${metrics.http_req_duration.values['p(99)'].toFixed(2)}ms`);
  }

  if (metrics.http_req_failed) {
    const failRate = (metrics.http_req_failed.values.rate * 100).toFixed(2);
    console.log(`요청 실패율: ${failRate}%`);
  }

  console.log('============================================');
}
