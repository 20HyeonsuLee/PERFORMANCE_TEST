# WebSocket-SSE-Polling 모듈

WebSocket 기반 실시간 통신 성능 테스트 모듈

## 기능

- WebSocket을 통한 실시간 메시지 브로드캐스팅
- 설정 가능한 TPS (Transactions Per Second)
- Toxiproxy를 활용한 네트워크 시뮬레이션
- 메시지 지연 시간 측정 및 성능 분석

## 빠른 시작

### 로컬 실행

```bash
# 빌드
./gradlew :websocket-sse-polling:build

# 실행
./gradlew :websocket-sse-polling:bootRun
```

### Docker 실행

```bash
# 프로젝트 루트에서
docker build -f websocket-sse-polling/Dockerfile -t websocket-sse-polling .
docker run -p 8080:8080 websocket-sse-polling
```

### Docker Compose 실행

```bash
cd websocket-sse-polling
cp .env.example .env
# .env 파일 수정
docker-compose up -d
```

## API 엔드포인트

### 브로드캐스트 시작

```bash
POST /broadcast/start
Content-Type: application/json

{
  "delay": 100,      # 메시지 간 딜레이 (ms) - tps와 배타적
  "tps": 10,         # 초당 메시지 수 - delay와 배타적
  "duration": 60     # 브로드캐스트 지속 시간 (초)
}
```

### WebSocket 연결

```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    stompClient.subscribe('/topic/messages', function(message) {
        console.log(JSON.parse(message.body));
    });
});
```

### Health Check

```bash
GET /actuator/health
```

## 성능 테스트

```bash
# 테스트 실행
./gradlew :websocket-sse-polling:test

# 특정 테스트 실행
./gradlew :websocket-sse-polling:test --tests "WebSocketPerformanceTest"
```

## 환경 변수

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 활성 프로파일 | `default` |
| `SERVER_PORT` | 서버 포트 | `8080` |
| `LOG_LEVEL` | 로그 레벨 | `INFO` |

## 배포

GitHub Actions를 통한 자동 배포가 설정되어 있습니다.

- **자동 배포**: `main` 브랜치에 push 시
- **수동 배포**: GitHub Actions UI에서 실행

자세한 내용은 [배포 프로세스](./docs/배포_프로세스.md) 문서를 참고하세요.

## 문서

- [배포 프로세스](./docs/배포_프로세스.md) - 배포 전략 및 워크플로우
- [배포 시작하기](./docs/배포_시작하기.md) - 빠른 배포 가이드
- [성능 테스트 체크리스트](./docs/성능테스트_체크리스트.md) - 성능 테스트 가이드

## 기술 스택

- Java 21
- Spring Boot 3.5.7
- Spring WebSocket
- Docker
- Testcontainers
- Toxiproxy

## 라이선스

MIT
