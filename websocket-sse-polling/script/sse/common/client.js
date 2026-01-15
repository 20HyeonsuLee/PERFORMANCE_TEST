/**
 * SSE 클라이언트
 *
 * 책임:
 * - SSE 연결
 * - 이벤트 수신 및 처리
 * - 메트릭 기록
 */

import http from 'k6/http';
import { check } from 'k6';
import { getSseUrl, config } from './config.js';
import { parseEvents, parseEventData, calculateLatency, isConnectedEvent } from './parser.js';
import {
  recordMessageReceived,
  recordLatency,
  recordMessageSuccess,
  recordConnectionSuccess
} from './metrics.js';

/**
 * SSE 연결 및 이벤트 수신
 *
 * @param {string} sessionId - 세션 ID (로깅용)
 * @param {Object} options - 연결 옵션 {logEvents: boolean, maxEvents: number}
 * @returns {Object} {success: boolean, eventCount: number, latencies: Array<number>}
 */
export function connectAndReceive(sessionId, options = {}) {
  const {
    logEvents = false,
    maxEvents = null,
  } = options;

  const params = {
    ...config.sse,
    responseType: 'text',
  };

  const res = http.get(getSseUrl(), params);

  // 연결 성공 확인
  const connectionSuccess = check(res, {
    'SSE connection status is 200': (r) => r.status === 200,
    'SSE connection has content': (r) => r.body && r.body.length > 0,
  });

  recordConnectionSuccess(connectionSuccess);

  if (!connectionSuccess) {
    console.error(`❌ [${sessionId}] Failed to connect: status=${res.status}`);
    return {
      success: false,
      eventCount: 0,
      latencies: [],
    };
  }

  if (logEvents) {
    console.log(`✅ [${sessionId}] SSE connection established`);
  }

  // 이벤트 파싱
  const events = parseEvents(res.body);
  const latencies = [];

  // 연결 확인 이벤트 제외
  const messageEvents = events.filter(event => !isConnectedEvent(event));

  recordMessageReceived(messageEvents.length);

  if (logEvents) {
    console.log(`📨 [${sessionId}] Received ${messageEvents.length} events`);
  }

  // 각 이벤트 처리
  messageEvents.forEach((event, index) => {
    if (maxEvents && index >= maxEvents) {
      return;
    }

    processEvent(event, sessionId, index, logEvents, latencies);
  });

  return {
    success: true,
    eventCount: messageEvents.length,
    latencies: latencies,
  };
}

/**
 * 개별 이벤트 처리
 */
function processEvent(event, sessionId, index, logEvents, latencies) {
  if (!event.data) {
    recordMessageSuccess(false);
    return;
  }

  try {
    const payload = parseEventData(event.data);

    if (!payload) {
      recordMessageSuccess(false);
      return;
    }

    // 타임스탬프가 있으면 지연시간 계산
    if (payload.timestamp) {
      const latency = calculateLatency(payload.timestamp);

      if (latency >= 0) {
        recordLatency(latency);
        latencies.push(latency);
        recordMessageSuccess(true);

        if (logEvents && (index === 0 || index < 5)) {
          console.log(`📈 [${sessionId}] Event ${index + 1}: latency=${latency}ms`);
        }
      } else {
        recordMessageSuccess(false);
      }
    } else {
      recordMessageSuccess(true);
    }
  } catch (e) {
    console.error(`⚠️  [${sessionId}] Failed to process event: ${e}`);
    recordMessageSuccess(false);
  }
}

/**
 * 간단한 SSE 연결 (메트릭만)
 */
export function connectSimple(sessionId) {
  return connectAndReceive(sessionId, {
    logEvents: false,
    maxEvents: null,
  });
}

/**
 * 상세 로깅 SSE 연결
 */
export function connectVerbose(sessionId) {
  return connectAndReceive(sessionId, {
    logEvents: true,
    maxEvents: 10, // 처음 10개만 로깅
  });
}
