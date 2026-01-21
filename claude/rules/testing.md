# Java 테스트 규칙 (rules/testing.md)

## 1. 테스트 원칙: Real Object First
- **상태 기반 검증:** 행위 검증(verify)보다 실제 객체의 상태 변환을 확인하는 상태 기반 검증을 우선한다.
- **테스트 더블 지양:** 인터페이스나 외부 API를 제외하고, 도메인 모델과 내부 서비스는 실제 객체를 직접 생성하여 테스트한다.
- **AssertJ 활용:** 가독성 높은 검증을 위해 `assertThat` 등 AssertJ 문법을 사용한다.
- **given-when-then** 테스트의 흐름 파악이 쉽도록 주석을 이용하여 given, when, then을 구분한다.

## 2. 계층별 테스트 전략
1. **Domain 단위 테스트 (POJO):** - 의존성 없이 실제 엔티티와 VO를 생성하여 테스트한다.
    - 비즈니스 로직의 모든 분기점을 순수 Java 코드로 검증한다.
2. **Application 서비스 테스트 (State-based):**
    - `@MockitoExtension` 사용을 최소화한다.
    - 도메인 객체는 실제 객체를 사용하며, DB 접근이 필요한 경우에만 가벼운 In-memory DB 또는 Fake Repository를 검토한다.
3. **통합/슬라이스 테스트:**
    - `@DataJpaTest` 등을 활용할 때도 실제 DB(H2 등)와 연동하여 쿼리 작동 여부를 확인한다.
    - 외부 API 연동부만 `MockRestServiceServer` 등으로 대체한다.

## 3. 테스트 데이터 관리
- **Test Fixture:** `src/test/java/[base-package]/fixture/` 폴더에 도메인별 테스트 데이터 생성기(Factory)를 둔다.
- **유효 객체 보장:** Fixture는 항상 도메인 규칙에 어긋나지 않는 '유효한 상태의 실제 객체'를 반환해야 한다.
