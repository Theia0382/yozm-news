package net.yozmnews.source.domain.profile;

import java.util.Arrays;
import java.util.Optional;

/**
 * 기사 항목의 값 가운데 프로필이 읽을 요소를 정할 수 있는 것 — 필드 매핑(발행 시각), 요약문으로 쓸 요소, 메타데이터 매핑(기자 ·
 * 카테고리 · 썸네일).
 */
public enum ItemField {

	PUBLISHED_AT("publishedAt"),
	SUMMARY("summary"),
	AUTHOR("author"),
	CATEGORY("category"),
	THUMBNAIL("thumbnail");

	private final String key;

	ItemField(String key) {
		this.key = key;
	}

	/** 프로필 텍스트의 {@code items} 아래 키를 돌려준다 */
	public String key() {
		return this.key;
	}

	public static Optional<ItemField> ofKey(String key) {
		return Arrays.stream(values()).filter(field -> field.key.equals(key)).findFirst();
	}

}
