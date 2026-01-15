import ws from 'k6/ws';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import http from 'k6/http';

const messageCounter = new Counter('messages_received');
const latencyTrend = new Trend('message_latency');

export const options = {
  vus: 10,
  duration: '30s',
};

const BASE_URL = __ENV.BASE_URL || 'localhost:8080';

export function setup() {
  const config = JSON.stringify({
    tps: 5,
    duration: 60
  });

  const res = http.post(`http://${BASE_URL}/broadcast/start`, config, {
    headers: { 'Content-Type': 'application/json' },
  });

  console.log(`Broadcast started: ${res.status}`);
}

export default function() {
  const url = `ws://${BASE_URL}/ws`;

  const res = ws.connect(url, {}, function(socket) {
    socket.on('open', () => {
      // STOMP CONNECT
      socket.send('CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\x00');
    });

    socket.on('message', (data) => {
      if (data.startsWith('CONNECTED')) {
        // STOMP SUBSCRIBE
        socket.send('SUBSCRIBE\nid:sub-0\ndestination:/topic/messages\n\n\x00');
      } else if (data.startsWith('MESSAGE')) {
        messageCounter.add(1);

        // Extract latency
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

    socket.setTimeout(() => socket.close(), 30000);
  });

  check(res, { 'status is 101': (r) => r && r.status === 101 });
}
