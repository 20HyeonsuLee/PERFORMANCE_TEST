/**
 * SSE 고급 부하 테스트
 *
 * 책임:
 * - 점진적 부하 증가 시나리오
 * - Threshold 설정
 * - 상세 메트릭 수집
 */

import { sleep } from 'k6';
import { healthCheck, startBroadcast } from './common/api.js';
import { connectVerbose } from './common/client.js';

// 테스트 설정
export const options = {
  scenarios: {
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },   // 30초에 걸쳐 10명으로
        { duration: '1m', target: 50 },    // 1분에 걸쳐 50명으로
        { duration: '2m', target: 100 },   // 2분에 걸쳐 100명으로
        { duration: '1m', target: 100 },   // 100명 유지
        { duration: '30s', target: 0 },    // 30초에 걸쳐 0명으로
      ],
      gracefulRampDown: '30s',
    },
  },

  thresholds: {
    'sse_connection_success': ['rate>0.95'],         // 95% 이상 연결 성공
    'sse_message_success': ['rate>0.99'],            // 99% 이상 메시지 수신 성공
    'sse_message_latency': ['p(95)<100', 'p(99)<200'], // 지연시간 임계값
    'http_req_duration': ['p(95)<1000'],             // HTTP 요청 시간
  },
};

/**
 * Setup: 테스트 시작 전 준비
 */
export function setup() {
  console.log('🚀 Starting SSE load test');

  // 헬스체크
  const healthy = healthCheck();
  if (!healthy) {
    throw new Error('Health check failed');
  }

  // 브로드캐스트 자동 시작
  const broadcastConfig = {
    tps: 10,      // 10 messages per second
    duration: 600  // 10 minutes (테스트 시간보다 길게)
  };

  const started = startBroadcast(broadcastConfig);
  if (!started) {
    throw new Error('Failed to start broadcast');
  }

  console.log('⏳ Waiting for broadcast to start...');
  sleep(2);

  return {
    startTime: Date.now(),
  };
}

/**
 * Main: 각 VU가 실행할 테스트
 */
export default function(data) {
  const sessionId = `load-${__VU}-${__ITER}`;

  // 상세 로깅으로 SSE 연결
  const result = connectVerbose(sessionId);

  if (result.success) {
    const avgLatency = result.latencies.length > 0
      ? result.latencies.reduce((a, b) => a + b, 0) / result.latencies.length
      : 0;

    console.log(`🔴 [${sessionId}] Connection closed: events=${result.eventCount}, avgLatency=${avgLatency.toFixed(2)}ms`);
  }

  sleep(1);
}

/**
 * Teardown: 테스트 종료 후 정리
 */
export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`\n✅ Load test completed in ${duration.toFixed(2)} seconds`);
}
