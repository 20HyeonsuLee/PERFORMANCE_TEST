# Spring Boot DDD 아키텍처 규칙 (rules/architecture.md)

## 1. 표준 패키지 구조
- 모든 소스 코드는 `src/main/java/[base-package]/[domain]/` 아래에 위치한다.

```text
modules/[domain-name]/
├── domain/                # 핵심 도메인 모델 (POJO)
│   ├── model/             # Aggregates, Entities, Value Objects (Record 활용)
│   ├── repository/        # Repository 인터페이스 (Spring Data 인터페이스)
│   └── service/           # 도메인 서비스 (도메인 로직이 여러 객체에 걸칠 때)
├── application/           # 유스케이스 및 흐름 제어
│   ├── service/           # @Service (트랜잭션 관리 및 흐름 제어)
│   ├── dto/               # Request/Response DTO (Record 활용)
├── infrastructure/        # 외부 기술 구현체
│   ├── persistence/       # JPA Entity, Querydsl 구현체
│   └── external/          # 외부 API 클라이언트 (Feign, RestClient 등)
└── interfaces/            # 진입점
    └── controller/        # @RestController
```

## 2. 공통 모듈 (Shared)
- `src/main/java/[base-package]/shared/` 폴더를 생성한다.
- 모든 도메인에서 공통으로 사용하는 **BaseEntity**, **공통 예외 클래스**는 이곳에 위치시킨다.
- Shared 패키지는 다른 어떤 모듈에도 의존해서는 안 된다.

## 3. 계층 간 규칙
- Entity = Domain Model: 별도의 변환(Mapping) 비용을 줄이기 위해 JPA 엔티티를 도메인 모델로 직접 사용한다.
- 의존성 방향: Interfaces -> Application -> Domain <- Infrastructure.
- Domain의 순수성: domain 패키지는 Spring 프레임워크(특히 JPA Annotation 제외)에 최대한 의존하지 않는 순수 Java 코드로 유지한다.
- Rich Domain Model: 비즈니스 로직은 서비스 계층이 아닌 엔티티 내부에 작성한다. (Service는 상태 변경 명령만 내림)
- 트랜잭션 범위: @Transactional은 application.service 계층에서만 사용한다.
- 데이터 변환: 컨트롤러는 DTO를 사용하며, 도메인 엔티티를 직접 반환하지 않는다.

## 4. Spring Boot 특화 규칙
- Bean 주입: 필드 주입(@Autowired) 대신 생성자 주입을 사용하라 (Lombok @RequiredArgsConstructor)
- Validation: jakarta.validation(@Valid, @NotNull 등)을 DTO 레벨에서 적극 활용하라.
- Exception: @RestControllerAdvice를 통해 공통 처리하라.

## 5. 모듈 간 통신
- **직접 참조 금지:** 다른 모듈의 서비스나 레포지토리를 직접 주입받지 않는다.
- **이벤트 기반 통신:** 모듈 간 상태 변경 전파는 `ApplicationEventPublisher`를 통한 도메인 이벤트 발행 및 `@EventListener` 처리를 원칙으로 한다.
- **TransactionalEventListener:** 부가 작업(로그, 알림 등)은 `@TransactionalEventListener`를 사용하여 메인 트랜잭션과 분리한다.
