import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import http from 'k6/http';

// Custom metrics
const messageCounter = new Counter('messages_received');
const latencyTrend = new Trend('message_latency');
const connectionRate = new Rate('connection_success');
const messageRate = new Rate('message_success');

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
    //     { duration: '10s', target: 100 },  // 급격히 증가
    //     { duration: '30s', target: 100 },  // 유지
    //     { duration: '10s', target: 0 },    // 급격히 감소
    //   ],
    // },
  },

  thresholds: {
    'connection_success': ['rate>0.95'],       // 95% 이상 연결 성공
    'message_success': ['rate>0.99'],          // 99% 이상 메시지 수신 성공
    'message_latency': ['p(95)<100', 'p(99)<200'], // 95%는 100ms 이하, 99%는 200ms 이하
    'ws_connecting': ['p(95)<1000'],           // 95%는 1초 이내 연결
  },
};

// Environment variables
const BASE_URL = __ENV.BASE_URL || '43.200.188.232:8080';
const WS_URL = `ws://${BASE_URL}/ws`;
const HTTP_URL = `http://${BASE_URL}`;

export function setup() {
  console.log(`🚀 Starting WebSocket load test against ${BASE_URL}`);
  console.log(`📊 WebSocket URL: ${WS_URL}`);

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
  // http.post(`${HTTP_URL}/broadcast/start`, broadcastConfig, {
  //   headers: { 'Content-Type': 'application/json' },
  // });

  return { startTime: Date.now() };
}

export default function(data) {
  const sessionId = `session-${__VU}-${Date.now()}`;
  let messageCount = 0;
  const messageLatencies = [];

  const res = ws.connect(WS_URL, {}, function(socket) {
    socket.on('open', function() {
      console.log(`✅ [${sessionId}] WebSocket connection opened`);
      connectionRate.add(true);

      // STOMP CONNECT frame
      const connectFrame =
        'CONNECT\n' +
        'accept-version:1.1,1.0\n' +
        'heart-beat:10000,10000\n' +
        '\n' +
        '\x00';

      socket.send(connectFrame);
    });

    socket.on('message', function(message) {
      try {
        const data = message;

        // Handle STOMP CONNECTED frame
        if (data.startsWith('CONNECTED')) {
          console.log(`🔗 [${sessionId}] STOMP connected, subscribing to /topic/messages`);

          // STOMP SUBSCRIBE frame
          const subscribeFrame =
            'SUBSCRIBE\n' +
            'id:sub-0\n' +
            'destination:/topic/messages\n' +
            '\n' +
            '\x00';

          socket.send(subscribeFrame);
          return;
        }

        // Handle STOMP MESSAGE frame
        if (data.startsWith('MESSAGE')) {
          messageCount++;
          messageCounter.add(1);

          // Extract JSON payload from STOMP frame
          const bodyStart = data.indexOf('\n\n') + 2;
          const bodyEnd = data.indexOf('\x00', bodyStart);
          const body = data.substring(bodyStart, bodyEnd);

          try {
            const payload = JSON.parse(body);

            // Calculate latency if timestamp exists
            if (payload.timestamp) {
              const messageTime = new Date(payload.timestamp).getTime();
              const now = Date.now();
              const latency = now - messageTime;

              latencyTrend.add(latency);
              messageLatencies.push(latency);
              messageRate.add(true);

              if (messageCount % 10 === 0) {
                console.log(`📨 [${sessionId}] Received ${messageCount} messages, latency: ${latency}ms`);
              }
            }
          } catch (e) {
            console.log(`⚠️  [${sessionId}] Failed to parse message body: ${e}`);
            messageRate.add(false);
          }
        }
      } catch (e) {
        console.log(`❌ [${sessionId}] Error handling message: ${e}`);
        messageRate.add(false);
      }
    });

    socket.on('close', function() {
      console.log(`🔴 [${sessionId}] WebSocket connection closed`);
      console.log(`📊 [${sessionId}] Total messages received: ${messageCount}`);

      if (messageLatencies.length > 0) {
        const avgLatency = messageLatencies.reduce((a, b) => a + b, 0) / messageLatencies.length;
        console.log(`📈 [${sessionId}] Average latency: ${avgLatency.toFixed(2)}ms`);
      }
    });

    socket.on('error', function(e) {
      console.log(`❌ [${sessionId}] WebSocket error: ${e}`);
      connectionRate.add(false);
    });

    // Keep connection open for test duration
    socket.setTimeout(function() {
      console.log(`⏰ [${sessionId}] Timeout reached, closing connection`);
      socket.close();
    }, 60000); // 60 seconds
  });

  check(res, {
    'WebSocket connection successful': (r) => r && r.status === 101,
  });
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`\n✅ Test completed in ${duration.toFixed(2)} seconds`);
}
