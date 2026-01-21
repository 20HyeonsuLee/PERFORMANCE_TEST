# 에러 처리 규칙 (rules/error-handling.md)

1. **비즈니스 예외:** 도메인 로직 위반 시 `RuntimeException`을 상속받은 예외를 던진다.
   - 필요하다면 커스텀 예외 작성 가능
   - 되도록이면 기본 예외 사용
2. **Global Handler:** `@RestControllerAdvice`를 사용하여 모든 예외를 공통된 `ErrorResponse` 형식으로 반환한다.
3. **Validation:** 유효성 검사 실패 시 `MethodArgumentNotValidException`을 잡아 상세한 필드 에러를 반환한다.
