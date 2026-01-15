# k6 부하 테스트 스크립트

SRP(Single Responsibility Principle)를 준수하는 WebSocket & SSE 부하 테스트

## 📁 디렉토리 구조

```
script/
├── sse/                         # SSE 테스트
│   ├── common/                  # 공통 모듈 (SRP 적용)
│   │   ├── config.js           # 설정 및 환경 변수
│   │   ├── client.js           # SSE 연결 로직
│   │   ├── parser.js           # SSE 이벤트 파싱
│   │   ├── metrics.js          # 커스텀 메트릭
│   │   └── api.js              # API 호출 (브로드캐스트 등)
│   ├── simple-test.js          # 간단한 테스트 (10 VUs, 30s)
│   ├── load-test.js            # 부하 테스트 (0→100 점진적)
│   ├── stress-test.js          # 스트레스 테스트 (0→400)
│   └── comparison-test.js      # 프로토콜 비교 테스트
│
├── websocket/                   # WebSocket 테스트
│   ├── common/                  # 공통 모듈 (SRP 적용)
│   │   ├── config.js           # 설정 및 환경 변수
│   │   ├── client.js           # WebSocket 연결 로직
│   │   ├── stomp.js            # STOMP 프로토콜 처리
│   │   ├── metrics.js          # 커스텀 메트릭
│   │   └── api.js              # API 호출
│   ├── simple-test.js          # 간단한 테스트
│   ├── load-test.js            # 부하 테스트
│   └── stress-test.js          # 스트레스 테스트
│
└── README.md                    # 이 파일
```

## 🎯 SRP (Single Responsibility Principle) 적용

각 모듈은 명확한 단일 책임만 가집니다:

- **config.js**: 환경 변수 관리, URL 생성
- **client.js**: 연결 처리, 이벤트 수신
- **parser.js / stomp.js**: 프로토콜 파싱
- **metrics.js**: 메트릭 기록
- **api.js**: HTTP API 호출
- **테스트 스크립트**: 시나리오 정의

## 🚀 실행 방법

### SSE 테스트

```bash
# 간단한 테스트
k6 run script/sse/simple-test.js

# 부하 테스트
k6 run script/sse/load-test.js

# 스트레스 테스트
k6 run script/sse/stress-test.js

# 비교 테스트
k6 run script/sse/comparison-test.js
```

### WebSocket 테스트

```bash
# 간단한 테스트
k6 run script/websocket/simple-test.js

# 부하 테스트
k6 run script/websocket/load-test.js

# 스트레스 테스트
k6 run script/websocket/stress-test.js
```

### 환경 변수 설정

```bash
# 서버 URL 변경
k6 run -e BASE_URL=your-server:8080 script/sse/load-test.js

# 결과를 JSON으로 저장
k6 run --out json=results.json script/sse/load-test.js
```

## ✨ 자동 브로드캐스트 시작

**더 이상 수동으로 POST 요청을 보낼 필요가 없습니다!**

모든 테스트 스크립트는 `setup()` 함수에서 자동으로 브로드캐스트를 시작합니다.

### Before (수동 실행)
```bash
# 1. 먼저 브로드캐스트 시작
curl -X POST http://localhost:8080/sse/broadcast/start \
  -H "Content-Type: application/json" \
  -d '{"tps": 10, "duration": 60}'

# 2. 그 다음 테스트 실행
k6 run script/sse/load-test.js
```

### After (자동 실행)
```bash
# 테스트만 실행하면 자동으로 브로드캐스트 시작
k6 run script/sse/load-test.js
```

## 📊 커스텀 메트릭

### SSE 메트릭
- `sse_messages_received` (Counter): 수신한 이벤트 총 개수
- `sse_message_latency` (Trend): 메시지 지연시간 (ms)
- `sse_connection_success` (Rate): 연결 성공률
- `sse_message_success` (Rate): 메시지 처리 성공률

### WebSocket 메트릭
- `messages_received` (Counter): 수신한 메시지 총 개수
- `message_latency` (Trend): 메시지 지연시간 (ms)
- `connection_success` (Rate): 연결 성공률
- `message_success` (Rate): 메시지 처리 성공률

## 🔧 공통 모듈 사용 예시

### SSE 클라이언트

```javascript
import { connectSimple, connectVerbose } from './common/client.js';

// 간단한 연결 (메트릭만)
const result = connectSimple('session-id');

// 상세 로깅 연결
const result = connectVerbose('session-id');

console.log(`Messages: ${result.messageCount}`);
console.log(`Success: ${result.success}`);
```

### WebSocket 클라이언트

```javascript
import { connectSimple, connectVerbose } from './common/client.js';

// 간단한 연결
const result = connectSimple('session-id', '/topic/messages');

// 커스텀 설정
import { connectAndReceive } from './common/client.js';

const result = connectAndReceive('session-id', '/topic/messages', {
  logEvents: true,
  maxMessages: 100,  // 100개 메시지 수신 후 종료
  duration: 30000,   // 30초
});
```

### API 호출

```javascript
import { healthCheck, startBroadcast } from './common/api.js';

// 헬스체크
if (!healthCheck()) {
  throw new Error('Server is not healthy');
}

// 브로드캐스트 시작
startBroadcast({ tps: 10, duration: 60 });
```

## 🧪 새로운 테스트 시나리오 추가하기

### 1. 새 테스트 파일 생성

```bash
touch script/sse/my-test.js
```

### 2. 공통 모듈 임포트

```javascript
import { sleep } from 'k6';
import { healthCheck, startBroadcast } from './common/api.js';
import { connectSimple } from './common/client.js';
```

### 3. 테스트 옵션 정의

```javascript
export const options = {
  vus: 20,
  duration: '1m',
  thresholds: {
    'sse_message_latency': ['p(95)<100'],
  },
};
```

### 4. Setup/Main/Teardown 구현

```javascript
export function setup() {
  healthCheck();
  startBroadcast({ tps: 5, duration: 120 });
  sleep(2);
  return { startTime: Date.now() };
}

export default function(data) {
  const result = connectSimple(`my-${__VU}-${__ITER}`);
  sleep(1);
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`✅ Test completed in ${duration.toFixed(2)}s`);
}
```

## 🔍 트러블슈팅

### 브로드캐스트가 시작되지 않음

```bash
# 서버 상태 확인
curl http://localhost:8080/actuator/health

# 수동으로 브로드캐스트 시작 테스트
curl -X POST http://localhost:8080/sse/broadcast/start \
  -H "Content-Type: application/json" \
  -d '{"tps": 5, "duration": 60}'
```

### 메시지를 수신하지 못함

- `setup()` 함수에서 `sleep(2)`로 브로드캐스트 시작 대기
- 브로드캐스트 duration이 테스트 시간보다 길어야 함

### 연결 실패

- 서버 URL 확인: `-e BASE_URL=localhost:8080`
- 방화벽/보안그룹 설정 확인
- WebSocket의 경우 `ws://` 프로토콜 확인

## 📚 참고 자료

- [k6 공식 문서](https://k6.io/docs/)
- [k6 WebSocket API](https://k6.io/docs/javascript-api/k6-ws/)
- [SSE 가이드](./SSE_README.md)
- [메트릭 가이드](../docs/메트릭_가이드.md)
