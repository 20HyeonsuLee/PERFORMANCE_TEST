import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';

// Custom metrics
const messageCounter = new Counter('sse_messages_received');
const latencyTrend = new Trend('sse_message_latency');
const connectionRate = new Rate('sse_connection_success');

// Stress test configuration - 점진적으로 증가하여 시스템 한계 찾기
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
    'sse_connection_success': ['rate>0.95'],
    'sse_message_latency': ['p(95)<500', 'p(99)<1000'],  // Stress 테스트용으로 더 높게 설정
    'http_req_duration': ['p(95)<2000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'localhost:8080';

export function setup() {
  console.log('🚀 Starting SSE stress test - finding system limits');

  // Start broadcasting with high TPS
  const config = JSON.stringify({
    tps: 50,      // 50 messages per second
    duration: 3600 // 1 hour
  });

  http.post(`http://${BASE_URL}/sse/broadcast/start`, config, {
    headers: { 'Content-Type': 'application/json' },
  });

  return { startTime: Date.now() };
}

export default function() {
  const sessionId = `stress-${__VU}-${Date.now()}`;

  const params = {
    timeout: '120s',  // 2 minutes
    headers: {
      'Accept': 'text/event-stream',
    },
    responseType: 'text',
  };

  const res = http.get(`http://${BASE_URL}/sse/response`, params);

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
  });

  connectionRate.add(success);

  if (success && res.body) {
    // Parse events
    const events = parseSSEEvents(res.body);
    messageCounter.add(events.length);

    console.log(`[${sessionId}] Received ${events.length} events`);

    // Calculate latency for first and last events
    if (events.length > 0) {
      [0, events.length - 1].forEach(idx => {
        if (idx >= 0 && idx < events.length) {
          try {
            const payload = JSON.parse(events[idx].data);
            if (payload.timestamp) {
              const latency = Date.now() - new Date(payload.timestamp).getTime();
              latencyTrend.add(latency);
            }
          } catch (e) {}
        }
      });
    }
  } else {
    console.log(`❌ [${sessionId}] Connection failed: ${res.status}`);
  }

  sleep(1);
}

function parseSSEEvents(body) {
  const events = [];
  const lines = body.split('\n');
  let currentEvent = { event: '', data: '', id: '' };

  for (let line of lines) {
    line = line.trim();
    if (line === '') {
      if (currentEvent.data) {
        events.push({ ...currentEvent });
        currentEvent = { event: '', data: '', id: '' };
      }
    } else if (line.startsWith('data:')) {
      currentEvent.data += line.substring(5).trim();
    } else if (line.startsWith('event:')) {
      currentEvent.event = line.substring(6).trim();
    }
  }

  if (currentEvent.data) {
    events.push(currentEvent);
  }

  return events;
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`\n✅ SSE stress test completed in ${duration.toFixed(2)} seconds`);
}
