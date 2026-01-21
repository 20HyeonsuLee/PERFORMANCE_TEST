# Java/Spring 코딩 스타일 가이드 (rules/style.md)

## 1. 객체지향 생활체조 원칙 (Java 기준)
1. **한 메서드에 한 단계의 들여쓰기(Indent)만 허용:** 복잡한 로직은 프라이빗 메서드로 추출하라.
2. **else 예약어 사용 금지:** `Optional`과 `Early Return`을 적극 활용하라.
3. **모든 원시 값과 문자열을 포장:** `String email` 대신 `record Email(String value) {}`를 사용하여 도메인 의미를 부여하라.
4. **한 줄에 점(Dot) 하나만 사용:** `order.getCustomer().getAddress()` 대신 객체에 메시지를 보내라. (단, Stream API나 빌더 패턴은 예외)
5. **줄여 쓰지 않는다:** `req` -> `request`, `svc` -> `service` 등 명확한 이름을 사용하라.
6. **엔티티 크기 최소화:** 클래스는 50줄 이내를 지향하며, 책임이 커지면 분리하라.
7. **3개 이상의 인스턴스 변수 금지:** 클래스가 너무 많은 상태를 가지면 객체로 묶어라.
8. **일급 컬렉션 사용:** 컬렉션은 그 자체로 객체화하여 로직을 응집시켜라 (예: `List<Order>`를 관리하는 `Orders` 클래스).
9. **Getter/Setter/Property 지양:** 데이터를 꺼내지 말고 객체가 스스로 판단하여 행동하게 하라(Tell, Don't Ask).
    - *참고: DTO나 JPA Entity의 프레임워크 요구사항에 한해 최소한으로 허용한다.*
10. **Stream API를 최대한 활용한다.**

## 2. Java 명명 및 구현 규칙
- **Class:** PascalCase (예: `OrderService`)
- **Method:** camelCase (예: `calculateTotalAmount`)
  - **기본 구조:** `동사 + 목적어` 형식을 사용한다. (예: `calculateTotalPrice()`)
  - **Boolean 반환:** `is`, `has`, `can`, `should`로 시작한다. (예: `isValid()`, `hasToken()`)
  - **구체적인 동사 사용:** `handle`, `process`, `manage` 등 모호한 단어 대신 구체적인 동사를 선택한다.
  - `processData()` (X) -> `parseJsonLog()` (O)
- **일관성:** 데이터를 가져올 때는 `get`, `fetch`, `retrieve` 중 하나를 선정하여 프로젝트 전체에서 통일한다.
- **Constant:** UPPER_SNAKE_CASE (예: `MAX_RETRY_COUNT`)
- **Optional 활용:** `null`을 직접 반환하지 말고 `Optional<T>`를 사용하여 의도를 명확히 하라.
- **Record 사용:** 불변 데이터 객체(VO, DTO)는 Java 16+의 `record`를 우선적으로 사용하라.
- **final 사용** 모든 데이터(매개변수, 지역변수, 멤버변수)는 불변을 최우선으로 고려한다.

## 3. 에이전트 행동 지침
- 코드를 작성하기 전 위 규칙 위반 여부를 스스로 체크한다.
- 규칙 위반이 불가피한 경우 그 이유를 주석이나 대화로 설명한다.
