# SSE 모듈 아키텍처

SRP(Single Responsibility Principle)를 준수하는 SSE 모듈 구조

## 클래스 다이어그램

```
┌─────────────────┐
│  SseController  │  HTTP 엔드포인트 처리
└────────┬────────┘
         │
         ├──────────────┐
         │              │
         ▼              ▼
┌──────────────────┐  ┌─────────────────────┐
│ SseEmitterManager│  │ SseBroadcastService │
│                  │  │                     │
│ - Emitter 생명주기│  │ - 브로드캐스트 로직  │
│ - 연결 관리      │  │ - TPS/Delay 제어    │
└────────┬─────────┘  └──────────┬──────────┘
         │                       │
         │    ┌──────────────────┘
         │    │
         ▼    ▼
    ┌────────────────────┐
    │ SseMetricsCollector│
    │                    │
    │ - 메트릭 수집       │
    │ - Micrometer 연동  │
    └────────────────────┘
```

## 클래스 책임 분리

### 1. SseController
**책임**: HTTP 엔드포인트 처리

```java
@RestController
public class SseController {
    // ✅ HTTP 요청/응답만 처리
    // ✅ 비즈니스 로직은 다른 클래스에 위임
}
```

**엔드포인트**:
- `GET /sse/response` - SSE 연결
- `POST /api/broadcast` - 즉시 브로드캐스트
- `POST /sse/broadcast/start` - 비동기 브로드캐스트 시작
- `DELETE /api/remove-all` - 모든 연결 제거

**의존성**:
- `SseEmitterManager` - Emitter 생성 위임
- `SseBroadcastService` - 브로드캐스트 위임
- `SseMetricsCollector` - 메트릭 조회 위임

---

### 2. SseEmitterManager
**책임**: SseEmitter 생명주기 관리

```java
@Component
public class SseEmitterManager {
    // ✅ Emitter 생성, 등록, 제거
    // ✅ 연결 콜백 설정 (onCompletion, onTimeout, onError)
    // ✅ 초기 연결 메시지 전송
}
```

**주요 메서드**:
- `createEmitter()` - 새 SSE 연결 생성
- `getAllEmitters()` - 모든 활성 emitter 조회
- `getActiveConnectionCount()` - 현재 연결 수
- `removeAll()` - 모든 연결 제거

**관리 대상**:
- `Map<String, SseEmitter> emitters` - 활성 연결 저장소
- Emitter ID 생성
- 연결 생명주기 콜백

**메트릭 연동**:
- 연결 성공 시 `metricsCollector.incrementConnectionTotal()`
- 연결 오류 시 `metricsCollector.incrementConnectionError()`

---

### 3. SseBroadcastService
**책임**: 메시지 브로드캐스트 로직

```java
@Service
public class SseBroadcastService {
    // ✅ TPS/Delay 기반 브로드캐스트
    // ✅ 메시지 전송 로직
    // ✅ 브로드캐스트 스케줄링
}
```

**주요 메서드**:
- `broadcastAsync(BroadcastConfig)` - 비동기 브로드캐스트 시작
- `broadcastOnce()` - 즉시 한 번 브로드캐스트
- `broadcastByTps()` - TPS 기반 브로드캐스트
- `broadcastByDelay()` - Delay 기반 브로드캐스트

**브로드캐스트 전략**:

#### TPS (초당 메시지 수) 기반
```java
intervalMillis = 1000 / tps
while (현재시간 < 종료시간) {
    broadcastOnce()
    sleep(intervalMillis - 처리시간)
}
```

#### Delay (지연시간) 기반
```java
while (현재시간 < 종료시간) {
    broadcastOnce()
    sleep(delayMillis)
}
```

**메트릭 연동**:
- 브로드캐스트 시작 시 `metricsCollector.resetMessageCount()`
- 메시지 전송 성공 시 `metricsCollector.recordMessageSent()`
- 메시지 전송 실패 시 `metricsCollector.recordMessageFailed()`
- 브로드캐스트 소요 시간 `metricsCollector.recordBroadcast()`

---

### 4. SseMetricsCollector
**책임**: 메트릭 수집 및 관리

```java
@Component
public class SseMetricsCollector {
    // ✅ Micrometer 메트릭 등록
    // ✅ 메트릭 기록
    // ✅ 메트릭 조회
}
```

**수집 메트릭**:

#### 메시지 메트릭
- `sse.messages.sent` (Counter) - 전송 성공 메시지 수
- `sse.messages.failed` (Counter) - 전송 실패 메시지 수
- `sse.broadcast.duration` (Timer) - 브로드캐스트 소요 시간

#### 연결 메트릭
- `sse.connections.total` (Counter) - 누적 연결 수
- `sse.connections.errors` (Counter) - 연결 오류 수
- `sse.connections.active` (Gauge) - 현재 활성 연결 수

**주요 메서드**:
- `recordMessageSent()` - 메시지 전송 성공 기록
- `recordMessageFailed()` - 메시지 전송 실패 기록
- `recordBroadcast(Runnable)` - 브로드캐스트 시간 측정
- `incrementConnectionTotal()` - 연결 총 수 증가
- `incrementConnectionError()` - 연결 오류 증가
- `registerActiveConnectionsGauge()` - Gauge 등록 (순환 참조 방지)

---

## 데이터 흐름

### 1. SSE 연결 흐름

```
Client → SseController.stream()
           ↓
       SseEmitterManager.createEmitter()
           ↓
       1. ID 생성 (client-timestamp)
       2. SseEmitter 생성 (30분 timeout)
       3. Emitter 등록 (Map에 추가)
       4. 콜백 설정 (onCompletion, onTimeout, onError)
       5. 초기 메시지 전송 ("connected")
       6. metricsCollector.incrementConnectionTotal()
           ↓
       Client ← SseEmitter 반환
```

### 2. 브로드캐스트 시작 흐름

```
Client → POST /sse/broadcast/start (BroadcastConfig)
           ↓
       SseController.startBroadcast()
           ↓
       SseBroadcastService.broadcastAsync() [@Async]
           ↓
       1. metricsCollector.resetMessageCount()
       2. 종료시간 계산
       3. TPS or Delay 기반 루프 시작
           ↓
       while (현재시간 < 종료시간) {
           broadcastOnce()
               ↓
           LoadTestResponse 생성
               ↓
           metricsCollector.recordBroadcast(() -> {
               emitterManager.getAllEmitters()
                   .forEach(emitter -> sendMessage())
           })
               ↓
           각 emitter에게 메시지 전송
               성공 → metricsCollector.recordMessageSent()
               실패 → metricsCollector.recordMessageFailed()
       }
```

### 3. 메시지 전송 흐름

```
sendMessage(emitter, message)
    ↓
try {
    emitter.send(SseEmitter.event().data(message))
    metricsCollector.recordMessageSent()
} catch (IOException) {
    metricsCollector.recordMessageFailed()
}
```

---

## SRP 준수 분석

### Before (리팩터링 전)

**SseController**:
- ❌ HTTP 엔드포인트 처리
- ❌ Emitter 생명주기 관리
- ❌ 메트릭 수집
- **책임 과다**: 3개의 책임

**SseService**:
- ❌ 브로드캐스트 로직
- ❌ 메시지 전송
- ❌ 메트릭 수집
- **책임 과다**: 3개의 책임

### After (리팩터링 후)

**SseController**:
- ✅ HTTP 엔드포인트 처리만
- **단일 책임**: API 레이어

**SseEmitterManager**:
- ✅ Emitter 생명주기 관리만
- **단일 책임**: 연결 관리

**SseBroadcastService**:
- ✅ 브로드캐스트 로직만
- **단일 책임**: 메시지 전송

**SseMetricsCollector**:
- ✅ 메트릭 수집만
- **단일 책임**: 모니터링

---

## 순환 참조 해결

### 문제
`SseEmitterManager` ← → `SseMetricsCollector` 순환 참조

### 해결 방법
1. `SseMetricsCollector` 생성자에서 `SseEmitterManager` 제거
2. `registerActiveConnectionsGauge()` 메서드 분리
3. `SseController.init()` (`@PostConstruct`)에서 Gauge 등록

```java
@PostConstruct
public void init() {
    metricsCollector.registerActiveConnectionsGauge(emitterManager);
}
```

---

## 확장 가능성

### 1. 새로운 브로드캐스트 전략 추가
`SseBroadcastService`에 메서드만 추가:
```java
public void broadcastBySchedule(CronExpression cron) {
    // Cron 기반 브로드캐스트
}
```

### 2. 새로운 메트릭 추가
`SseMetricsCollector`에 메트릭만 추가:
```java
private final Counter reconnections = Counter.builder("sse.reconnections")
    .register(meterRegistry);
```

### 3. Emitter 저장소 변경
`SseEmitterManager`만 수정 (Redis, Database 등):
```java
private final EmitterRepository emitterRepository; // ConcurrentHashMap → Redis
```

### 4. 새로운 API 추가
`SseController`에 엔드포인트만 추가:
```java
@GetMapping("/sse/stats")
public SseStats getStats() {
    return new SseStats(
        emitterManager.getActiveConnectionCount(),
        metricsCollector.getMessageCount()
    );
}
```

---

## 테스트 전략

### Unit Test

#### SseEmitterManagerTest
```java
- createEmitter() → Emitter 생성 확인
- setupCallbacks() → onCompletion, onTimeout, onError 동작 확인
- removeAll() → 모든 emitter 제거 확인
```

#### SseBroadcastServiceTest
```java
- broadcastByTps() → TPS 준수 확인
- broadcastByDelay() → Delay 준수 확인
- broadcastOnce() → 메시지 생성 및 전송 확인
```

#### SseMetricsCollectorTest
```java
- recordMessageSent() → Counter 증가 확인
- recordBroadcast() → Timer 기록 확인
- getMessageCount() → 카운트 조회 확인
```

### Integration Test

#### SseControllerTest (with MockMvc)
```java
- GET /sse/response → SSE 연결 성공
- POST /sse/broadcast/start → 브로드캐스트 시작
- DELETE /api/remove-all → 모든 연결 제거
```

### Performance Test

#### k6 Load Test
```bash
k6 run script/sse-load-test.js
```
- 클라이언트 관점 지연시간 측정
- 메시지 수신율 측정
- 연결 안정성 테스트

---

## 모니터링

### Prometheus 쿼리

#### 초당 메시지 전송율 (TPS)
```promql
rate(sse_messages_sent_total[1m])
```

#### 메시지 실패율
```promql
rate(sse_messages_failed_total[1m]) / rate(sse_messages_sent_total[1m]) * 100
```

#### 활성 연결 수
```promql
sse_connections_active
```

#### 브로드캐스트 P95 지연시간
```promql
histogram_quantile(0.95, rate(sse_broadcast_duration_seconds_bucket[5m]))
```

### Grafana 대시보드

패널 구성:
1. **활성 연결 수** (Gauge) - `sse_connections_active`
2. **TPS** (Graph) - `rate(sse_messages_sent_total[1m])`
3. **메시지 실패율** (Graph) - 실패율 계산식
4. **브로드캐스트 지연시간** (Graph) - P50, P95, P99
5. **누적 전송 메시지** (Counter) - `sse_messages_sent_total`

---

## 성능 최적화

### 1. 메모리 사용량
- `ConcurrentHashMap` 사용으로 최소 오버헤드
- Emitter 자동 제거 (onCompletion, onTimeout, onError)

### 2. CPU 사용량
- 비동기 브로드캐스트 (`@Async`)
- Sleep 기반 TPS 제어로 CPU 절약

### 3. 네트워크 효율
- HTTP/1.1 Keep-Alive
- JSON 직렬화 캐싱 (`LoadTestResponse` immutable)

### 4. 확장성
- Stateless 설계 (Emitter만 메모리에 보관)
- 수평 확장 가능 (Redis 저장소 사용 시)

---

## 참고 자료

- [Spring SSE 공식 문서](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html#mvc-ann-async-sse)
- [Micrometer 공식 문서](https://micrometer.io/docs)
- [SOLID 원칙](https://en.wikipedia.org/wiki/SOLID)
- [메트릭 가이드](./메트릭_가이드.md)
