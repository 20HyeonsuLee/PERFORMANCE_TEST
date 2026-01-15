/**
 * WebSocket 커스텀 메트릭 정의
 *
 * 책임:
 * - 커스텀 메트릭 객체 생성
 * - 메트릭 기록
 */

import { Counter, Trend, Rate } from 'k6/metrics';

// 메시지 관련 메트릭
export const messageCounter = new Counter('messages_received');
export const latencyTrend = new Trend('message_latency');
export const messageRate = new Rate('message_success');

// 연결 관련 메트릭
export const connectionRate = new Rate('connection_success');

/**
 * 메시지 수신 기록
 */
export function recordMessageReceived(count = 1) {
  messageCounter.add(count);
}

/**
 * 메시지 지연시간 기록
 */
export function recordLatency(latencyMs) {
  latencyTrend.add(latencyMs);
}

/**
 * 메시지 처리 성공/실패 기록
 */
export function recordMessageSuccess(success) {
  messageRate.add(success);
}

/**
 * 연결 성공/실패 기록
 */
export function recordConnectionSuccess(success) {
  connectionRate.add(success);
}
