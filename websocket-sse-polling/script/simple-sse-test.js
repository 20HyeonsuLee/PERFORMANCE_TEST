import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// Custom metrics
const messageCounter = new Counter('sse_messages_received');
const latencyTrend = new Trend('sse_message_latency');

// Simple test configuration
export const options = {
  vus: 10,           // 10 virtual users
  duration: '30s',   // 30 seconds
};

const BASE_URL = __ENV.BASE_URL || 'localhost:8080';

export function setup() {
  // Start broadcasting
  const config = JSON.stringify({
    tps: 5,      // 5 messages per second
    duration: 60  // 60 seconds
  });

  const res = http.post(`http://${BASE_URL}/sse/broadcast/start`, config, {
    headers: { 'Content-Type': 'application/json' },
  });

  console.log(`Broadcast started: ${res.status}`);
}

export default function() {
  const params = {
    timeout: '30s',
    headers: {
      'Accept': 'text/event-stream',
    },
    responseType: 'text',
  };

  const res = http.get(`http://${BASE_URL}/sse/response`, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has events': (r) => r.body && r.body.includes('data:'),
  });

  // Count and parse events
  if (res.body) {
    const events = res.body.split('\n\n').filter(e => e.includes('data:'));
    messageCounter.add(events.length);

    // Parse first event for latency
    if (events.length > 0) {
      try {
        const dataLine = events[0].split('\n').find(l => l.startsWith('data:'));
        if (dataLine) {
          const data = dataLine.substring(5).trim();
          const payload = JSON.parse(data);
          if (payload.timestamp) {
            const latency = Date.now() - new Date(payload.timestamp).getTime();
            latencyTrend.add(latency);
          }
        }
      } catch (e) {
        // Ignore parse errors
      }
    }
  }

  sleep(1);
}
