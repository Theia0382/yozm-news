package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자 목록의 커서 매개변수 — 시각(ISO-8601)과 ID 를 함께 받는다 · docs/specs/admin.md §5 admin-R3. 평가 목록 · 사건 목록 · 판정 기록이 함께
 * 쓴다.
 */
record AdminCursor(Instant at, long id) {

	/**
	 * 질의의 두 값을 읽는다 — 둘 다 없으면 첫 쪽.
	 * @param atParam 시각 매개변수 이름 — 400 설명에 쓴다
	 * @param target ID 가 가리키는 것 — 400 설명에 쓴다
	 * @return 둘 다 없으면 null
	 * @throws ResponseStatusException 400 — 하나만 오거나, 시각 형식이 틀렸거나, ID 가 1 미만일 때
	 */
	static @Nullable AdminCursor of(@Nullable String at, @Nullable Long id, String atParam, String target) {
		if ((at == null) != (id == null)) {
			throw badRequest(atParam + " 과 afterId 는 함께 준다");
		}
		if (at == null) {
			return null;
		}
		Instant parsed;
		try {
			parsed = Instant.parse(at);
		}
		catch (DateTimeParseException ex) {
			throw badRequest("시각은 ISO-8601 형식이다: " + at);
		}
		long value = Objects.requireNonNull(id);
		if (value < 1) {
			throw badRequest(target + " ID 는 1 이상이다: " + value);
		}
		return new AdminCursor(parsed, value);
	}

	private static ResponseStatusException badRequest(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}

}
