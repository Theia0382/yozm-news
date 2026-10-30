package net.yozmnews.source.domain.fetch;

import org.jspecify.annotations.Nullable;

/**
 * 이전 수집이 남긴 조건부 요청 값 — docs/specs/source.md §4 · source-R22.
 *
 * @param etag 응답의 {@code ETag} — 없을 수 있다
 * @param lastModified 응답의 {@code Last-Modified} — 없을 수 있다
 */
public record FetchState(@Nullable String etag, @Nullable String lastModified) {

	/** 조건부 요청 값이 없는 상태 — 처음 수집하거나 서버가 주지 않았다 */
	public static final FetchState NONE = new FetchState(null, null);

	/** 저장 · 재전송할 수 있는 값의 최대 길이(문자) */
	public static final int MAX_VALIDATOR_LENGTH = 1_024;

	/**
	 * 값을 확인한다.
	 * @throws IllegalArgumentException 값이 {@link #isStorable} 이 아닐 때
	 */
	public FetchState {
		if (etag != null && !isStorable(etag)) {
			throw new IllegalArgumentException("ETag 를 저장할 수 없다");
		}
		if (lastModified != null && !isStorable(lastModified)) {
			throw new IllegalArgumentException("Last-Modified 를 저장할 수 없다");
		}
	}

	/** 200 응답의 두 헤더로 만든다 — 저장할 수 없는 값은 버린다 */
	public static FetchState fromResponse(@Nullable String etag, @Nullable String lastModified) {
		return new FetchState(storableOrNull(etag), storableOrNull(lastModified));
	}

	/** 304 응답이 준 값으로 바꾼다 — source-R22 */
	public FetchState updatedBy(@Nullable String etag, @Nullable String lastModified) {
		String newEtag = storableOrNull(etag);
		String newLastModified = storableOrNull(lastModified);
		return new FetchState((newEtag != null) ? newEtag : this.etag,
				(newLastModified != null) ? newLastModified : this.lastModified);
	}

	/** 조건부 헤더를 하나라도 보내는지 본다 */
	public boolean isConditional() {
		return this.etag != null || this.lastModified != null;
	}

	/** 헤더 값으로 다시 보낼 수 있는지 본다 — source-R22 */
	static boolean isStorable(String value) {
		if (value.isBlank() || value.length() > MAX_VALIDATOR_LENGTH) {
			return false;
		}
		return value.chars().allMatch(c -> c >= 0x20 && c <= 0x7E);
	}

	private static @Nullable String storableOrNull(@Nullable String value) {
		return (value != null && isStorable(value)) ? value : null;
	}

}
