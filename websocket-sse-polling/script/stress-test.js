import ws from 'k6/ws';
import { check } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import http from 'k6/http';

// Custom metrics
const messageCounter = new Counter('messages_received');
const latencyTrend = new Trend('message_latency');
const connectionRate = new Rate('connection_success');

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
    'connection_success': ['rate>0.95'],
    'message_latency': ['p(95)<500', 'p(99)<1000'],  // Stress 테스트용으로 더 높게 설정
    'ws_connecting': ['p(95)<2000'],
  },
};

const BASE_URL = __ENV.BASE_URL || '43.200.188.232:8080';

export function setup() {
  console.log('🚀 Starting stress test - finding system limits');

  // Start broadcasting with high TPS
  const config = JSON.stringify({
    tps: 50,      // 50 messages per second
    duration: 3600 // 1 hour
  });

  http.post(`http://${BASE_URL}/broadcast/start`, config, {
    headers: { 'Content-Type': 'application/json' },
  });

  return { startTime: Date.now() };
}

export default function() {
  const url = `ws://${BASE_URL}/ws`;
  const sessionId = `stress-${__VU}-${Date.now()}`;
  let messageCount = 0;

  const res = ws.connect(url, {}, function(socket) {
    socket.on('open', function() {
      connectionRate.add(true);
      socket.send('CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\x00');
    });

    socket.on('message', function(data) {
      if (data.startsWith('CONNECTED')) {
        socket.send('SUBSCRIBE\nid:sub-0\ndestination:/topic/messages\n\n\x00');
      } else if (data.startsWith('MESSAGE')) {
        messageCount++;
        messageCounter.add(1);

        const bodyStart = data.indexOf('\n\n') + 2;
        const bodyEnd = data.indexOf('\x00', bodyStart);
        const body = data.substring(bodyStart, bodyEnd);

        try {
          const payload = JSON.parse(body);
          if (payload.timestamp) {
            const latency = Date.now() - new Date(payload.timestamp).getTime();
            latencyTrend.add(latency);
          }
        } catch (e) {}
      }
    });

    socket.on('error', function(e) {
      console.log(`❌ [${sessionId}] Error: ${e}`);
      connectionRate.add(false);
    });

    socket.on('close', function() {
      console.log(`🔴 [${sessionId}] Closed. Messages: ${messageCount}`);
    });

    // Keep connection for 2 minutes
    socket.setTimeout(() => socket.close(), 120000);
  });

  check(res, { 'status is 101': (r) => r && r.status === 101 });
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`\n✅ Stress test completed in ${duration.toFixed(2)} seconds`);
}
