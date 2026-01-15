/**
 * STOMP 프로토콜 처리
 *
 * 책임:
 * - STOMP 프레임 생성
 * - STOMP 프레임 파싱
 * - STOMP 명령 처리
 */

import { config } from './config.js';

/**
 * STOMP CONNECT 프레임 생성
 */
export function createConnectFrame() {
  const heartbeat = config.stomp.heartbeat;
  return `CONNECT\naccept-version:1.1,1.0\nheart-beat:${heartbeat.outgoing},${heartbeat.incoming}\n\n\x00`;
}

/**
 * STOMP SUBSCRIBE 프레임 생성
 *
 * @param {string} destination - 구독 목적지 (예: /topic/messages)
 * @param {string} subscriptionId - 구독 ID
 */
export function createSubscribeFrame(destination, subscriptionId = 'sub-0') {
  return `SUBSCRIBE\nid:${subscriptionId}\ndestination:${destination}\n\n\x00`;
}

/**
 * STOMP SEND 프레임 생성
 *
 * @param {string} destination - 메시지 목적지
 * @param {string} body - 메시지 본문 (JSON 문자열)
 */
export function createSendFrame(destination, body) {
  return `SEND\ndestination:${destination}\ncontent-type:application/json\n\n${body}\x00`;
}

/**
 * STOMP DISCONNECT 프레임 생성
 */
export function createDisconnectFrame() {
  return `DISCONNECT\n\n\x00`;
}

/**
 * STOMP 프레임 파싱
 *
 * @param {string} data - 수신한 프레임 데이터
 * @returns {Object} {command: string, headers: Object, body: string}
 */
export function parseFrame(data) {
  const lines = data.split('\n');
  const command = lines[0];
  const headers = {};
  let bodyStartIndex = -1;

  // 헤더 파싱
  for (let i = 1; i < lines.length; i++) {
    const line = lines[i];

    if (line === '') {
      bodyStartIndex = i + 1;
      break;
    }

    const colonIndex = line.indexOf(':');
    if (colonIndex > 0) {
      const key = line.substring(0, colonIndex);
      const value = line.substring(colonIndex + 1);
      headers[key] = value;
    }
  }

  // 본문 파싱
  let body = '';
  if (bodyStartIndex > 0 && bodyStartIndex < lines.length) {
    body = lines.slice(bodyStartIndex).join('\n');
    // null 문자 제거
    body = body.replace(/\x00/g, '');
  }

  return {
    command,
    headers,
    body,
  };
}

/**
 * CONNECTED 프레임인지 확인
 */
export function isConnectedFrame(frame) {
  return frame.command === 'CONNECTED';
}

/**
 * MESSAGE 프레임인지 확인
 */
export function isMessageFrame(frame) {
  return frame.command === 'MESSAGE';
}

/**
 * ERROR 프레임인지 확인
 */
export function isErrorFrame(frame) {
  return frame.command === 'ERROR';
}

/**
 * MESSAGE 프레임 본문을 JSON으로 파싱
 *
 * @param {Object} frame - 파싱된 프레임
 * @returns {Object|null}
 */
export function parseMessageBody(frame) {
  if (!isMessageFrame(frame)) {
    return null;
  }

  try {
    return JSON.parse(frame.body);
  } catch (e) {
    return null;
  }
}

/**
 * 타임스탬프에서 지연시간 계산
 *
 * @param {string} timestamp - ISO 8601 타임스탬프
 * @returns {number} 지연시간 (밀리초)
 */
export function calculateLatency(timestamp) {
  try {
    const messageTime = new Date(timestamp).getTime();
    return Date.now() - messageTime;
  } catch (e) {
    return -1;
  }
}
