/**
 * SSE 비교 테스트
 *
 * 책임:
 * - SSE vs Long Polling 비교
 * - 프로토콜별 메트릭 분리
 */

import { sleep } from 'k6';
import http from 'k6/http';
import { healthCheck, startBroadcast } from './common/api.js';
import { connectSimple } from './common/client.js';
import { config } from './common/config.js';

// 비교 테스트 설정
export const options = {
  scenarios: {
    sse_test: {
      executor: 'constant-vus',
      vus: 50,
      duration: '5m',
      exec: 'sseTest',
      tags: { protocol: 'sse' },
    },
    // 필요시 long polling과 비교
    // polling_test: {
    //   executor: 'constant-vus',
    //   vus: 50,
    //   duration: '5m',
    //   exec: 'pollingTest',
    //   tags: { protocol: 'polling' },
    // },
  },

  thresholds: {
    'sse_message_latency': ['p(95)<200'],
    'http_req_duration{protocol:sse}': ['p(95)<1000'],
  },
};

/**
 * Setup: 테스트 시작 전 준비
 */
export function setup() {
  console.log('🔬 Starting SSE comparison test');

  // 헬스체크
  const healthy = healthCheck();
  if (!healthy) {
    throw new Error('Health check failed');
  }

  // 브로드캐스트 자동 시작
  const broadcastConfig = {
    tps: 10,
    duration: 600
  };

  const started = startBroadcast(broadcastConfig);
  if (!started) {
    throw new Error('Failed to start broadcast');
  }

  sleep(2);

  return {
    startTime: Date.now(),
  };
}

/**
 * SSE 테스트 시나리오
 */
export function sseTest() {
  const sessionId = `sse-${__VU}-${__ITER}`;
  connectSimple(sessionId);
  sleep(1);
}

/**
 * Long Polling 테스트 시나리오 (참고용)
 */
export function pollingTest() {
  const params = {
    timeout: '30s',
    tags: { protocol: 'polling' },
  };

  const res = http.get(`http://${config.baseUrl}/api/poll`, params);

  // Polling 처리 로직
  if (res.status === 200) {
    try {
      const data = JSON.parse(res.body);
      // 메트릭 기록
    } catch (e) {
      console.error('Failed to parse polling response');
    }
  }

  sleep(0.1); // Polling interval
}

/**
 * Teardown: 테스트 종료 후 정리
 */
export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`\n✅ Comparison test completed in ${duration.toFixed(2)} seconds`);
}
