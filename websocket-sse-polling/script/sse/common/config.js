/**
 * SSE 테스트 설정
 *
 * 책임:
 * - 환경 변수 관리
 * - 기본 설정값 제공
 * - URL 생성
 */

export const config = {
  baseUrl: __ENV.BASE_URL || 'localhost:8080',

  // SSE 연결 설정
  sse: {
    timeout: '60s',
    headers: {
      'Accept': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
    },
  },

  // HTTP 설정
  http: {
    timeout: '30s',
    headers: {
      'Content-Type': 'application/json',
    },
  },
};

/**
 * SSE 연결 URL 생성
 */
export function getSseUrl() {
  return `http://${config.baseUrl}/sse/response`;
}

/**
 * 브로드캐스트 시작 URL
 */
export function getBroadcastStartUrl() {
  return `http://${config.baseUrl}/sse/broadcast/start`;
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

/**
 * 연결 제거 URL
 */
export function getRemoveAllUrl() {
  return `http://${config.baseUrl}/api/remove-all`;
}
