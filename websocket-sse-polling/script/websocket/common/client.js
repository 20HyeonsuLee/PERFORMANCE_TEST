/**
 * WebSocket 클라이언트
 *
 * 책임:
 * - WebSocket 연결
 * - STOMP 프로토콜 처리
 * - 메시지 수신 및 처리
 * - 메트릭 기록
 */

import ws from 'k6/ws';
import { getWebSocketUrl } from './config.js';
import {
  createConnectFrame,
  createSubscribeFrame,
  parseFrame,
  isConnectedFrame,
  isMessageFrame,
  parseMessageBody,
  calculateLatency,
} from './stomp.js';
import {
  recordMessageReceived,
  recordLatency,
  recordMessageSuccess,
  recordConnectionSuccess,
} from './metrics.js';

/**
 * WebSocket 연결 및 메시지 수신
 *
 * @param {string} sessionId - 세션 ID (로깅용)
 * @param {string} destination - STOMP 구독 목적지 (예: /topic/messages)
 * @param {Object} options - 연결 옵션 {logEvents: boolean, maxMessages: number, duration: number}
 * @returns {Object} {success: boolean, messageCount: number, latencies: Array<number>}
 */
export function connectAndReceive(sessionId, destination = '/topic/messages', options = {}) {
  const {
    logEvents = false,
    maxMessages = null,
    duration = 60000, // 60초
  } = options;

  let messageCount = 0;
  const latencies = [];
  let isConnected = false;
  let isSubscribed = false;

  const url = getWebSocketUrl();

  const res = ws.connect(url, {}, function(socket) {
    // 연결 시작
    socket.on('open', function() {
      if (logEvents) {
        console.log(`✅ [${sessionId}] WebSocket connection opened`);
      }
      recordConnectionSuccess(true);

      // STOMP CONNECT
      socket.send(createConnectFrame());
    });

    // 메시지 수신
    socket.on('message', function(data) {
      const frame = parseFrame(data);

      // CONNECTED 프레임 처리
      if (isConnectedFrame(frame)) {
        isConnected = true;
        if (logEvents) {
          console.log(`🔌 [${sessionId}] STOMP connected`);
        }

        // SUBSCRIBE
        socket.send(createSubscribeFrame(destination));
        isSubscribed = true;
        if (logEvents) {
          console.log(`📡 [${sessionId}] Subscribed to ${destination}`);
        }
        return;
      }

      // MESSAGE 프레임 처리
      if (isMessageFrame(frame)) {
        messageCount++;
        recordMessageReceived();

        const payload = parseMessageBody(frame);

        if (payload && payload.timestamp) {
          const latency = calculateLatency(payload.timestamp);

          if (latency >= 0) {
            recordLatency(latency);
            latencies.push(latency);
            recordMessageSuccess(true);

            if (logEvents && (messageCount === 1 || messageCount <= 5)) {
              console.log(`📨 [${sessionId}] Message ${messageCount}: latency=${latency}ms`);
            }
          } else {
            recordMessageSuccess(false);
          }
        } else {
          recordMessageSuccess(true);
        }

        // 최대 메시지 수 도달 시 종료
        if (maxMessages && messageCount >= maxMessages) {
          if (logEvents) {
            console.log(`🏁 [${sessionId}] Reached max messages: ${maxMessages}`);
          }
          socket.close();
        }
      }
    });

    // 에러 처리
    socket.on('error', function(e) {
      console.error(`❌ [${sessionId}] WebSocket error: ${e}`);
      if (!isConnected) {
        recordConnectionSuccess(false);
      }
    });

    // 연결 종료
    socket.on('close', function() {
      if (logEvents) {
        console.log(`🔴 [${sessionId}] Connection closed: received ${messageCount} messages`);
      }
    });

    // 지정된 시간 동안 대기
    socket.setTimeout(function() {
      if (logEvents) {
        console.log(`⏱️  [${sessionId}] Timeout: closing connection`);
      }
      socket.close();
    }, duration);
  });

  return {
    success: isConnected && isSubscribed,
    messageCount,
    latencies,
  };
}

/**
 * 간단한 WebSocket 연결 (메트릭만)
 */
export function connectSimple(sessionId, destination = '/topic/messages') {
  return connectAndReceive(sessionId, destination, {
    logEvents: false,
    maxMessages: null,
    duration: 60000,
  });
}

/**
 * 상세 로깅 WebSocket 연결
 */
export function connectVerbose(sessionId, destination = '/topic/messages') {
  return connectAndReceive(sessionId, destination, {
    logEvents: true,
    maxMessages: null,
    duration: 60000,
  });
}
