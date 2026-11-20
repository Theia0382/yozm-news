package net.yozmnews.digest.domain.output;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.digest.domain.candidate.AliasedItem;

/**
 * 읽은 출력 — 항목 대조 전 · docs/specs/digest.md §4
 * @param topStories 읽은 주요 뉴스 칸 — 항목 별칭 → 주요 뉴스, 서술의 키 순서. 출력에 없던 항목은 없다
 * @param headlines 읽은 헤드라인 칸 — 항목 별칭 → 헤드라인, 서술의 키 순서. 출력에 없던 항목은 없다
 */
public record DigestDraft(String overview, Map<String, StoryDraft> topStories, Map<String, HeadlineDraft> headlines) {

	public DigestDraft {
		Objects.requireNonNull(overview, "overview");
		topStories = Collections.unmodifiableMap(new LinkedHashMap<>(topStories));
		headlines = Collections.unmodifiableMap(new LinkedHashMap<>(headlines));
	}

	/** 그 항목의 칸이 있는가 — 주요 뉴스 항목은 주요 뉴스 칸에서, 헤드라인 항목은 헤드라인 칸에서 별칭을 찾는다 */
	public boolean covers(AliasedItem item) {
		Objects.requireNonNull(item, "item");
		return switch (item.kind()) {
			case TOP_STORY -> this.topStories.containsKey(item.alias());
			case HEADLINE -> this.headlines.containsKey(item.alias());
		};
	}

}
