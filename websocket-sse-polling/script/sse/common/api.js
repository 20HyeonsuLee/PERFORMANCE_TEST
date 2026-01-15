/**
 * SSE API 호출
 *
 * 책임:
 * - 브로드캐스트 시작/중지
 * - 헬스체크
 * - 연결 관리
 */

import http from 'k6/http';
import { check } from 'k6';
import {
  getBroadcastStartUrl,
  getBroadcastUrl,
  getHealthCheckUrl,
  getRemoveAllUrl,
  config
} from './config.js';

/**
 * 헬스체크
 *
 * @returns {boolean} 헬스체크 성공 여부
 */
export function healthCheck() {
  const res = http.get(getHealthCheckUrl(), { timeout: config.http.timeout });

  const success = check(res, {
    'health check status is 200': (r) => r.status === 200,
  });

  return success;
}

/**
 * 브로드캐스트 시작
 *
 * @param {Object} broadcastConfig - {tps: number, duration: number}
 * @returns {boolean} 시작 성공 여부
 */
export function startBroadcast(broadcastConfig) {
  const payload = JSON.stringify(broadcastConfig);

  const res = http.post(getBroadcastStartUrl(), payload, {
    headers: config.http.headers,
    timeout: config.http.timeout,
  });

  const success = check(res, {
    'broadcast start status is 204': (r) => r.status === 204,
  });

  if (success) {
    console.log(`✅ Broadcast started: TPS=${broadcastConfig.tps}, Duration=${broadcastConfig.duration}s`);
  } else {
    console.error(`❌ Failed to start broadcast: status=${res.status}`);
  }

  return success;
}

/**
 * 단일 브로드캐스트 (즉시)
 *
 * @returns {boolean} 브로드캐스트 성공 여부
 */
export function broadcastOnce() {
  const res = http.post(getBroadcastUrl(), null, {
    headers: config.http.headers,
    timeout: config.http.timeout,
  });

  return check(res, {
    'broadcast status is 200': (r) => r.status === 200,
  });
}

/**
 * 모든 연결 제거
 *
 * @returns {boolean} 제거 성공 여부
 */
export function removeAllConnections() {
  const res = http.del(getRemoveAllUrl(), null, {
    timeout: config.http.timeout,
  });

  const success = check(res, {
    'remove all status is 200': (r) => r.status === 200,
  });

  if (success) {
    console.log('✅ All connections removed');
  }

  return success;
}
