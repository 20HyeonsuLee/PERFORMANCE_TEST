/**
 * WebSocket 스트레스 테스트
 *
 * 책임:
 * - 시스템 한계 테스트
 * - 점진적 부하 증가 (400명까지)
 * - 높은 TPS 테스트
 */

import { sleep } from 'k6';
import { healthCheck, startBroadcast } from './common/api.js';
import { connectSimple } from './common/client.js';

// 스트레스 테스트 설정
export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 100 },    // 2분에 100명
        { duration: '5m', target: 100 },    // 5분간 100명 유지
        { duration: '2m', target: 200 },    // 2분에 200명
        { duration: '5m', target: 200 },    // 5분간 200명 유지
        { duration: '2m', target: 300 },    // 2분에 300명
        { duration: '5m', target: 300 },    // 5분간 300명 유지
        { duration: '2m', target: 400 },    // 2분에 400명
        { duration: '5m', target: 400 },    // 5분간 400명 유지
        { duration: '10m', target: 0 },     // 10분에 걸쳐 종료
      ],
      gracefulRampDown: '30s',
    },
  },

  thresholds: {
    'connection_success': ['rate>0.95'],
    'message_latency': ['p(95)<500', 'p(99)<1000'],  // 스트레스 테스트용으로 더 높게
    'ws_connecting': ['p(95)<2000'],
  },
};

/**
 * Setup: 테스트 시작 전 준비
 */
export function setup() {
  console.log('🚀 Starting WebSocket stress test - finding system limits');

  // 헬스체크
  const healthy = healthCheck();
  if (!healthy) {
    throw new Error('Health check failed');
  }

  // 높은 TPS로 브로드캐스트 시작
  const broadcastConfig = {
    tps: 50,      // 50 messages per second
    duration: 3600 // 1 hour
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
  const sessionId = `stress-${__VU}-${Date.now()}`;

  const result = connectSimple(sessionId);

  if (result.success) {
    console.log(`[${sessionId}] Received ${result.messageCount} messages`);
  } else {
    console.error(`❌ [${sessionId}] Connection failed`);
  }

  sleep(1);
}

/**
 * Teardown: 테스트 종료 후 정리
 */
export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`\n✅ Stress test completed in ${duration.toFixed(2)} seconds`);
}
