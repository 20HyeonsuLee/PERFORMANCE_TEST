# SSE (Server-Sent Events) 부하 테스트 스크립트

k6를 사용한 SSE 부하 테스트

## SSE란?

Server-Sent Events는 서버에서 클라이언트로 단방향 실시간 데이터 스트리밍을 제공하는 HTTP 기반 프로토콜입니다.

**특징:**
- HTTP 기반 (WebSocket보다 간단)
- 서버 → 클라이언트 단방향
- 자동 재연결 지원
- text/event-stream 형식

**vs WebSocket:**
- SSE: 단방향, HTTP 기반, 더 간단
- WebSocket: 양방향, 별도 프로토콜, 더 복잡

## 테스트 스크립트

### 1. simple-sse-test.js
간단한 기본 테스트 (10 VUs, 30초)

### 2. sse-load-test.js
고급 부하 테스트 (점진적 증가, 메트릭 수집)

### 3. sse-stress-test.js
스트레스 테스트 (점진적으로 400명까지)

### 4. sse-comparison-test.js
SSE vs Long Polling 비교 테스트

## 실행 방법

### 기본 실행

```bash
# 간단한 테스트
k6 run simple-sse-test.js

# 고급 테스트
k6 run sse-load-test.js

# 스트레스 테스트
k6 run sse-stress-test.js

# 비교 테스트
k6 run sse-comparison-test.js
```

### 커스텀 설정

```bash
# VUs와 duration 지정
k6 run --vus 50 --duration 2m simple-sse-test.js

# 서버 URL 변경
k6 run -e BASE_URL=your-server:8080 sse-load-test.js

# 결과를 JSON으로 저장
k6 run --out json=sse-results.json sse-load-test.js
```

### Docker 사용

```bash
docker run --rm -i \
  --network=host \
  -v $(pwd):/scripts \
  grafana/k6 run /scripts/sse-load-test.js
```

## SSE 엔드포인트

### 연결
```bash
GET /sse/response
Accept: text/event-stream
```

### 브로드캐스트 시작
```bash
POST /sse/broadcast/start
Content-Type: application/json

{
  "tps": 10,
  "duration": 60
}
```

### 단일 브로드캐스트
```bash
POST /api/broadcast
```

### 모든 연결 종료
```bash
DELETE /api/remove-all
```

## 메트릭 설명

### 커스텀 메트릭
- `sse_messages_received`: 수신한 SSE 이벤트 총 개수
- `sse_message_latency`: 메시지 지연 시간
- `sse_connection_success`: 연결 성공률
- `sse_message_success`: 메시지 처리 성공률

### 기본 메트릭
- `http_reqs`: HTTP 요청 수
- `http_req_duration`: HTTP 요청 시간
- `http_req_waiting`: 서버 응답 대기 시간

## SSE 이벤트 형식

```
event: connected
data: Connected

event: message
data: {"timestamp":"2024-01-14T12:00:00","messages":[...]}

event: heartbeat
data: ping
```

## Threshold (임계값)

현재 설정된 임계값:
- 연결 성공률 > 95%
- 메시지 수신 성공률 > 99%
- 메시지 지연 시간 P95 < 100ms
- 메시지 지연 시간 P99 < 200ms
- HTTP 요청 시간 P95 < 1초

## 결과 분석

### 콘솔 출력

```
✅ [sse-session-1-123] SSE connection established
📨 [sse-session-1-123] Received 45 SSE events
📈 [sse-session-1-123] Event 1: latency=52ms
📈 [sse-session-1-123] Event 45: latency=48ms
🔴 [sse-session-1-123] Connection closed after 30000ms, received 45 events
```

### 요약 통계

```
     ✓ sse_connection_success........: 98.50%
     ✓ sse_message_success...........: 99.80%

     sse_message_latency.............: avg=48ms  min=12ms med=45ms max=150ms p(90)=75ms p(95)=95ms
     sse_messages_received...........: 15432
     http_req_duration...............: avg=25s   min=10s  med=24s  max=60s   p(95)=55s
```

## 브로드캐스트 시작

```bash
# 초당 10개 메시지, 60초 동안
curl -X POST http://localhost:8080/sse/broadcast/start \
  -H "Content-Type: application/json" \
  -d '{"tps": 10, "duration": 60}'
```

## 수동 SSE 연결 테스트

### curl 사용
```bash
curl -N -H "Accept: text/event-stream" http://localhost:8080/sse/response
```

### httpie 사용
```bash
http --stream GET http://localhost:8080/sse/response Accept:text/event-stream
```

### 브라우저 JavaScript
```javascript
const eventSource = new EventSource('http://localhost:8080/sse/response');

eventSource.addEventListener('connected', (e) => {
  console.log('Connected:', e.data);
});

eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('Received:', data);
};

eventSource.onerror = (error) => {
  console.error('Error:', error);
};
```

## 문제 해결

### 연결이 즉시 끊김
SSE는 timeout 설정이 중요합니다:
```javascript
const params = {
  timeout: '60s',  // 충분히 긴 timeout 설정
  headers: {
    'Accept': 'text/event-stream',
  },
};
```

### 메시지를 받지 못함
브로드캐스트가 시작되었는지 확인:
```bash
curl -X POST http://localhost:8080/sse/broadcast/start \
  -H "Content-Type: application/json" \
  -d '{"tps": 5, "duration": 60}'
```

### 파싱 에러
SSE 이벤트 형식 확인:
```bash
curl -N http://localhost:8080/sse/response | head -20
```

## SSE vs WebSocket 비교

### SSE 장점
- HTTP 기반으로 프록시/방화벽 통과 쉬움
- 자동 재연결 지원
- 구현이 더 간단
- 텍스트 데이터에 최적화

### WebSocket 장점
- 양방향 통신
- 바이너리 데이터 지원
- 더 낮은 오버헤드
- 더 낮은 지연시간

### 사용 사례
**SSE 추천:**
- 실시간 알림
- 주식 시세
- 뉴스 피드
- 서버 모니터링

**WebSocket 추천:**
- 채팅 애플리케이션
- 멀티플레이어 게임
- 협업 도구
- 양방향 통신 필요 시

## 고급 사용법

### 연결 재시도 테스트

```javascript
export default function() {
  for (let retry = 0; retry < 3; retry++) {
    const res = http.get(`${BASE_URL}/sse/response`, params);
    if (res.status === 200) {
      break;
    }
    console.log(`Retry ${retry + 1}/3`);
    sleep(1);
  }
}
```

### 연결 시간 분석

```javascript
const timings = new Trend('sse_connection_time');

export default function() {
  const start = Date.now();
  const res = http.get(`${BASE_URL}/sse/response`, params);
  const connectionTime = Date.now() - start;

  timings.add(connectionTime);
}
```

## 참고 자료

- [SSE 스펙](https://html.spec.whatwg.org/multipage/server-sent-events.html)
- [k6 공식 문서](https://k6.io/docs/)
- [MDN SSE 가이드](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)
