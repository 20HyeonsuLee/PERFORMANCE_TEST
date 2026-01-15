/**
 * SSE 이벤트 파싱
 *
 * 책임:
 * - SSE 이벤트 스트림 파싱
 * - 이벤트 필드 추출 (event, data, id)
 * - JSON 페이로드 파싱
 */

/**
 * SSE 이벤트 스트림 파싱
 *
 * @param {string} body - SSE 응답 본문
 * @returns {Array<{event: string, data: string, id: string}>}
 */
export function parseEvents(body) {
  const events = [];

  if (!body) {
    return events;
  }

  const lines = body.split('\n');
  let currentEvent = { event: '', data: '', id: '' };

  for (let line of lines) {
    line = line.trim();

    // 빈 줄은 이벤트 경계
    if (line === '') {
      if (currentEvent.data || currentEvent.event) {
        events.push({ ...currentEvent });
        currentEvent = { event: '', data: '', id: '' };
      }
      continue;
    }

    // 필드 파싱
    if (line.startsWith('event:')) {
      currentEvent.event = line.substring(6).trim();
    } else if (line.startsWith('data:')) {
      const data = line.substring(5).trim();
      currentEvent.data += (currentEvent.data ? '\n' : '') + data;
    } else if (line.startsWith('id:')) {
      currentEvent.id = line.substring(3).trim();
    }
  }

  // 마지막 이벤트 추가
  if (currentEvent.data || currentEvent.event) {
    events.push(currentEvent);
  }

  return events;
}

/**
 * 이벤트 데이터를 JSON으로 파싱
 *
 * @param {string} data - 이벤트 data 필드
 * @returns {Object|null}
 */
export function parseEventData(data) {
  try {
    return JSON.parse(data);
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

/**
 * 연결 이벤트인지 확인
 */
export function isConnectedEvent(event) {
  return event.event === 'connected';
}

/**
 * 하트비트 이벤트인지 확인
 */
export function isHeartbeatEvent(event) {
  return event.event === 'heartbeat';
}
