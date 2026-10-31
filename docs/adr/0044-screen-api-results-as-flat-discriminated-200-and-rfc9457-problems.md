# ADR-0044: 화면 API 는 판정 결과를 판별자 하나의 평평한 200 응답으로, 요청의 문제만 RFC 9457 형식의 자체 오류 레코드로 앱 전체에서 낸다

- 상태: 승인
- 날짜: 2026-10-31
- 근거: [이슈 #25](https://github.com/Theia0382/yozm-news/issues/25) — spring-webmvc 7.0.10 의 `ResponseEntityExceptionHandler` · springdoc 3.2.0 의 명세 출력을 시험으로 확인. 구현 `5193083`(오류 응답 형식) · `231ab94`(운영자 API 응답)
- 관련: [ADR-0020](0020-screen-api-contract-from-committed-openapi-spec.md)(화면용 API 계약 — 오류 타입의 직렬화를 이 ADR 이 정한다), [ADR-0043](0043-admin-functions-guarded-by-env-bearer-token-until-account-roles.md)(운영자 거부의 문제 유형)

## 맥락

- 운영자 API 가 화면용 API 에 처음으로 오류 응답과 여러 갈래의 판정 결과(켜짐 · 거절 · 기준 미달 · 받지 못함 등)를 들인다.
- ADR-0020 은 객체 속성을 모두 필수로 내고 응답은 null 키를 쓴다고 정했고, `ProblemDetail` 처럼 빈 속성을 빼는 타입을 처음 들이는 작업이 처리 방식을 정한다고 남겼다. Spring `ProblemDetail` 의 Jackson 직렬화는 빈 속성을 빼고(`NON_EMPTY`) 확장 속성을 최상위에 펼친다.
- `ResponseEntityExceptionHandler` 는 프레임워크 예외(형식 오류 · 없는 경로 · `ResponseStatusException` 등)를 `ProblemDetail` 로 만들고 `createResponseEntity` 로 응답을 만든다. 같은 예외에 처리기를 하나 더 두면 기동이 멈춘다(모호한 처리기).
- springdoc 은 `@JsonSubTypes` 의 다형 타입을 `allOf` 합성으로 내고, 오류 `@ApiResponse` 를 선언한 작업에서는 반환 타입의 200 을 추론하지 않는다.

## 결정

- **판정 결과는 200 응답이다.** 유스케이스가 정상으로 내놓는 결과(켜기 거절 · 기준 미달 · 받지 못함 · 원문 검증 실패 · 이미 등록된 프리셋 등)는 판별자 `result`(문자열 열거) 하나와 결과마다 쓰이는 속성을 모두 필수 · null 가능으로 둔 평평한 레코드로 낸다. 판별자와 속성의 조합은 응답 레코드의 정적 팩토리 하나에서만 만든다.
- **HTTP 오류는 요청의 문제다.** 자격(401 · 403), 없는 대상(404), 본문 형식 · 값의 범위(400), 동시 수정(409), 처리하지 못한 예외(500).
- **오류 본문은 RFC 9457 의 필드 이름을 따르는 자체 레코드 `ApiProblem(type, title, status, detail, instance)` 이다.** 미디어 타입은 `application/problem+json`, 모든 속성이 값을 갖는다 — `instance` 는 요청 경로, `detail` 은 늘 채운다. 따로 정하지 않은 문제의 `type` 은 `about:blank` 이고 `title` 은 상태 코드의 표준 문구다. 응답은 `Cache-Control: no-store` 다.
- **앱 전체에 적용한다.** 컨트롤러 오류는 `platform` 의 조언이 `ResponseEntityExceptionHandler` 를 이어 `createResponseEntity` 에서 본문을 바꾸고, 보안 거부는 보안 진입점이 같은 레코드를 쓴다. 500 은 예외 문구를 싣지 않는다. 다른 모듈의 오류 대응(예: 모듈 공개 예외 → 404)은 그 모듈의 조언이 `platform` 공개 타입으로 응답을 돌려준다.
- **명세에는 오류 응답을 `ApiProblem` 스키마로 선언하고, 그런 작업의 200 은 반환 타입 스키마로 명시한다.**

## 결과

- 화면은 상태 코드로 "요청이 틀렸다"와 "판정이 이렇다"를 가른다. 판정 결과는 생성 타입의 판별자 열거로 망라 분기한다.
- 평평한 응답의 생성 타입은 판별자와 속성의 조합을 강제하지 않는다 — 조합은 서버의 정적 팩토리와 응답 리터럴 시험이 지킨다.
- 없는 경로도 `application/problem+json` 404 다(배포 스모크가 이 형식을 확인한다).
- `ProblemDetail` 의 확장 속성 기능은 쓰지 않는다. 문제를 기계가 가르려면 `type` URI 를 정해 쓴다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 판정 거절을 4xx 오류로 낸다 | 유스케이스가 정상으로 돌려주는 결과와 요청의 문제가 같은 경로로 섞이고, 결과의 값(미리보기 · 기준별 값)을 오류 본문의 확장으로 실어야 한다 |
| sealed 응답 + `@JsonTypeInfo` 의 `oneOf` 판별 공용체 | springdoc 출력이 `allOf` 합성이라 `oneOf` 를 손으로 맞춰야 하고, 명세가 실제 본문과 어긋날 위험이 크다 |
| Spring `ProblemDetail` 을 그대로 직렬화 | 빈 속성이 빠지고 확장 속성이 펼쳐져 명세 스키마(모든 속성 필수)와 본문이 어긋난다 |
| 자체 `{code, message}` 본문 | 표준이 아니고, 프레임워크 예외를 모두 직접 옮겨야 한다 |
| 조언에서 같은 예외의 처리기를 하나 더 둔다 | 상위 처리기와 겹쳐 기동이 멈춘다 |
