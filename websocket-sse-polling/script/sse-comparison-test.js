import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// Custom metrics for comparison
const sseMessages = new Counter('sse_messages');
const sseLatency = new Trend('sse_latency');
const sseConnections = new Counter('sse_connections');

// Comparison test - SSE vs Long Polling vs WebSocket 비교용
export const options = {
  scenarios: {
    sse_test: {
      executor: 'constant-vus',
      vus: 50,
      duration: '5m',
      exec: 'sseTest',
      tags: { protocol: 'sse' },
    },
    // 필요시 long polling과 비교
    // polling_test: {
    //   executor: 'constant-vus',
    //   vus: 50,
    //   duration: '5m',
    //   exec: 'pollingTest',
    //   tags: { protocol: 'polling' },
    // },
  },

  thresholds: {
    'sse_latency': ['p(95)<200'],
    'http_req_duration{protocol:sse}': ['p(95)<1000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'localhost:8080';

export function setup() {
  console.log('🔬 Starting SSE comparison test');

  // Start broadcasting
  const config = JSON.stringify({
    tps: 10,
    duration: 600
  });

  http.post(`http://${BASE_URL}/sse/broadcast/start`, config, {
    headers: { 'Content-Type': 'application/json' },
  });

  return { startTime: Date.now() };
}

export function sseTest() {
  const params = {
    timeout: '60s',
    headers: {
      'Accept': 'text/event-stream',
    },
    responseType: 'text',
    tags: { protocol: 'sse' },
  };

  const res = http.get(`http://${BASE_URL}/sse/response`, params);

  if (res.status === 200) {
    sseConnections.add(1);

    const events = parseEvents(res.body);
    sseMessages.add(events.length);

    events.forEach(event => {
      try {
        const payload = JSON.parse(event.data);
        if (payload.timestamp) {
          const latency = Date.now() - new Date(payload.timestamp).getTime();
          sseLatency.add(latency);
        }
      } catch (e) {}
    });
  }

  sleep(1);
}

// 참고: Long Polling 비교 테스트 (필요시 사용)
export function pollingTest() {
  const params = {
    timeout: '30s',
    tags: { protocol: 'polling' },
  };

  const res = http.get(`http://${BASE_URL}/api/poll`, params);

  if (res.status === 200) {
    try {
      const data = JSON.parse(res.body);
      if (data.timestamp) {
        const latency = Date.now() - new Date(data.timestamp).getTime();
        // Add to comparison metrics
      }
    } catch (e) {}
  }

  sleep(0.1); // Polling interval
}

function parseEvents(body) {
  const events = [];
  if (!body) return events;

  const lines = body.split('\n');
  let currentEvent = { data: '' };

  for (let line of lines) {
    line = line.trim();
    if (line === '') {
      if (currentEvent.data) {
        events.push({ ...currentEvent });
        currentEvent = { data: '' };
      }
    } else if (line.startsWith('data:')) {
      currentEvent.data += line.substring(5).trim();
    }
  }

  if (currentEvent.data) {
    events.push(currentEvent);
  }

  return events;
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`\n✅ Comparison test completed in ${duration.toFixed(2)} seconds`);
}
