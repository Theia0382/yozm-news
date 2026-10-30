package net.yozmnews.source.domain.feed;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * 받은 피드 문서 — 정규화의 입력.
 * <p>
 * 본문은 받은 바이트 그대로이고, 문자 인코딩은 정규화가 판정한다.
 */
public final class FeedDocument {

	private final byte[] body;

	private final @Nullable String transportCharset;

	private final Instant fetchedAt;

	/**
	 * 받은 문서를 만든다.
	 * @param body 응답 본문 — 복사해 둔다
	 * @param transportCharset 전송 계층이 알린 문자 인코딩(HTTP {@code Content-Type} 의 charset 매개변수) — 없을 수 있다
	 * @param fetchedAt 받은 시각 — 발행 시각을 해석하지 못한 기사의 추정값이 된다
	 */
	public FeedDocument(byte[] body, @Nullable String transportCharset, Instant fetchedAt) {
		this.body = Objects.requireNonNull(body, "body").clone();
		this.transportCharset = transportCharset;
		this.fetchedAt = Objects.requireNonNull(fetchedAt, "fetchedAt");
	}

	/** 본문의 복사본을 돌려준다 */
	byte[] body() {
		return this.body.clone();
	}

	Optional<String> transportCharset() {
		return Optional.ofNullable(this.transportCharset);
	}

	public Instant fetchedAt() {
		return this.fetchedAt;
	}

}
