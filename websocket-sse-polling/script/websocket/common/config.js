/**
 * WebSocket 테스트 설정
 *
 * 책임:
 * - 환경 변수 관리
 * - 기본 설정값 제공
 * - URL 생성
 */

export const config = {
  baseUrl: __ENV.BASE_URL || 'localhost:8080',

  // WebSocket 연결 설정
  ws: {
    timeout: '60s',
  },

  // HTTP 설정
  http: {
    timeout: '30s',
    headers: {
      'Content-Type': 'application/json',
    },
  },

  // STOMP 설정
  stomp: {
    heartbeat: {
      outgoing: 10000,
      incoming: 10000,
    },
  },
};

/**
 * WebSocket 연결 URL 생성
 */
export function getWebSocketUrl() {
  return `ws://${config.baseUrl}/ws`;
}

/**
 * 브로드캐스트 시작 URL
 */
export function getBroadcastStartUrl() {
  return `http://${config.baseUrl}/broadcast/start`;
}

/**
 * 단일 브로드캐스트 URL
 */
export function getBroadcastUrl() {
  return `http://${config.baseUrl}/api/broadcast`;
}

/**
 * 헬스체크 URL
 */
export function getHealthCheckUrl() {
  return `http://${config.baseUrl}/actuator/health`;
}
