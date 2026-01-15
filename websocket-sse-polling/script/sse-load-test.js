import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';

// Custom metrics
const messageCounter = new Counter('sse_messages_received');
const latencyTrend = new Trend('sse_message_latency');
const connectionRate = new Rate('sse_connection_success');
const messageRate = new Rate('sse_message_success');

// Test configuration
export const options = {
  scenarios: {
    // 점진적으로 연결 증가
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },   // 30초에 걸쳐 10명으로
        { duration: '1m', target: 50 },    // 1분에 걸쳐 50명으로
        { duration: '2m', target: 100 },   // 2분에 걸쳐 100명으로
        { duration: '1m', target: 100 },   // 100명 유지
        { duration: '30s', target: 0 },    // 30초에 걸쳐 0명으로
      ],
      gracefulRampDown: '30s',
    },

    // 일정한 부하 유지
    // constant_load: {
    //   executor: 'constant-vus',
    //   vus: 50,
    //   duration: '5m',
    // },

    // 스파이크 테스트
    // spike: {
    //   executor: 'ramping-vus',
    //   startVUs: 0,
    //   stages: [
    //     { duration: '10s', target: 100 },
    //     { duration: '30s', target: 100 },
    //     { duration: '10s', target: 0 },
    //   ],
    // },
  },

  thresholds: {
    'sse_connection_success': ['rate>0.95'],       // 95% 이상 연결 성공
    'sse_message_success': ['rate>0.99'],          // 99% 이상 메시지 수신 성공
    'sse_message_latency': ['p(95)<100', 'p(99)<200'], // 95%는 100ms 이하, 99%는 200ms 이하
    'http_req_duration': ['p(95)<1000'],           // 95%는 1초 이내 응답
  },
};

// Environment variables
const BASE_URL = __ENV.BASE_URL || 'localhost:8080';
const HTTP_URL = `http://${BASE_URL}`;

export function setup() {
  console.log(`🚀 Starting SSE load test against ${BASE_URL}`);
  console.log(`📊 SSE URL: ${HTTP_URL}/sse/response`);

  // Health check
  const healthCheck = http.get(`${HTTP_URL}/actuator/health`);
  check(healthCheck, {
    'health check status is 200': (r) => r.status === 200,
  });

  // Start broadcasting (optional - 필요시 활성화)
  // const broadcastConfig = JSON.stringify({
  //   tps: 10,
  //   duration: 600  // 10 minutes
  // });
  // http.post(`${HTTP_URL}/sse/broadcast/start`, broadcastConfig, {
  //   headers: { 'Content-Type': 'application/json' },
  // });

  return { startTime: Date.now() };
}

export default function(data) {
  const sessionId = `sse-session-${__VU}-${Date.now()}`;

  // SSE 연결 - timeout을 60초로 설정
  const params = {
    timeout: '60s',
    headers: {
      'Accept': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
    },
    responseType: 'text',
  };

  const startTime = Date.now();
  const res = http.get(`${HTTP_URL}/sse/response`, params);

  const connectionSuccess = check(res, {
    'SSE connection status is 200': (r) => r.status === 200,
    'SSE connection has content': (r) => r.body && r.body.length > 0,
  });

  connectionRate.add(connectionSuccess);

  if (!connectionSuccess) {
    console.log(`❌ [${sessionId}] Failed to connect: status=${res.status}`);
    return;
  }

  console.log(`✅ [${sessionId}] SSE connection established`);

  // Parse SSE events
  try {
    const events = parseSSEEvents(res.body);
    console.log(`📨 [${sessionId}] Received ${events.length} SSE events`);

    events.forEach((event, index) => {
      messageCounter.add(1);

      // Calculate latency if event has timestamp
      if (event.data) {
        try {
          const payload = JSON.parse(event.data);
          if (payload.timestamp) {
            const messageTime = new Date(payload.timestamp).getTime();
            const now = Date.now();
            const latency = now - messageTime;

            latencyTrend.add(latency);
            messageRate.add(true);

            if (index === 0 || index === events.length - 1) {
              console.log(`📈 [${sessionId}] Event ${index + 1}: latency=${latency}ms`);
            }
          }
        } catch (e) {
          console.log(`⚠️  [${sessionId}] Failed to parse event data: ${e}`);
          messageRate.add(false);
        }
      }
    });

    const connectionDuration = Date.now() - startTime;
    console.log(`🔴 [${sessionId}] Connection closed after ${connectionDuration}ms, received ${events.length} events`);

  } catch (e) {
    console.log(`❌ [${sessionId}] Error parsing SSE events: ${e}`);
    messageRate.add(false);
  }

  // Wait a bit before next iteration
  sleep(1);
}

// Parse SSE event stream
function parseSSEEvents(body) {
  const events = [];
  const lines = body.split('\n');

  let currentEvent = { event: '', data: '', id: '' };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();

    if (line === '') {
      // Empty line indicates end of event
      if (currentEvent.data || currentEvent.event) {
        events.push({ ...currentEvent });
        currentEvent = { event: '', data: '', id: '' };
      }
      continue;
    }

    if (line.startsWith('event:')) {
      currentEvent.event = line.substring(6).trim();
    } else if (line.startsWith('data:')) {
      const data = line.substring(5).trim();
      currentEvent.data += (currentEvent.data ? '\n' : '') + data;
    } else if (line.startsWith('id:')) {
      currentEvent.id = line.substring(3).trim();
    }
  }

  // Add last event if exists
  if (currentEvent.data || currentEvent.event) {
    events.push(currentEvent);
  }

  return events;
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`\n✅ SSE test completed in ${duration.toFixed(2)} seconds`);
}
