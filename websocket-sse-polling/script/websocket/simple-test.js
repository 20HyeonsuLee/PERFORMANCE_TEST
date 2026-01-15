/**
 * WebSocket 간단한 부하 테스트
 *
 * 책임:
 * - 테스트 시나리오 정의
 * - Setup/Teardown 관리
 * - 테스트 실행 흐름 제어
 */

import { sleep } from 'k6';
import { healthCheck, startBroadcast } from './common/api.js';
import { connectSimple } from './common/client.js';

// 테스트 설정
export const options = {
  vus: 10,           // 10 virtual users
  duration: '30s',   // 30 seconds
};

/**
 * Setup: 테스트 시작 전 준비
 */
export function setup() {
  console.log('🚀 Starting WebSocket simple test');

  // 헬스체크
  const healthy = healthCheck();
  if (!healthy) {
    throw new Error('Health check failed');
  }

  // 브로드캐스트 자동 시작
  const broadcastConfig = {
    tps: 5,       // 5 messages per second
    duration: 60   // 60 seconds
  };

  const started = startBroadcast(broadcastConfig);
  if (!started) {
    throw new Error('Failed to start broadcast');
  }

  // 브로드캐스트가 시작될 시간 대기
  sleep(2);

  return {
    startTime: Date.now(),
  };
}

/**
 * Main: 각 VU가 실행할 테스트
 */
export default function(data) {
  const sessionId = `simple-${__VU}-${__ITER}`;

  // WebSocket 연결 및 메시지 수신 (60초)
  const result = connectSimple(sessionId);

  if (result.success && result.messageCount > 0) {
    console.log(`✅ [${sessionId}] Received ${result.messageCount} messages`);
  }

  sleep(1);
}

/**
 * Teardown: 테스트 종료 후 정리
 */
export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`\n✅ Simple test completed in ${duration.toFixed(2)} seconds`);
}
