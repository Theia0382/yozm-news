package net.yozmnews.digest.domain.input;

import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.domain.item.ItemKind;
import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;

/**
 * 생성의 입력에서 항목 하나 — 코드가 묶고 가른 사건들 · docs/specs/digest.md §5 · digest-R22
 *
 * @param kind 주요 뉴스 · 헤드라인 — LLM 이 쓸 칸
 * @param events 항목의 사건 — 비지 않음, 사건 점수 순
 * @param storyline 항목을 묶은 포함 나무의 꼭대기 — 혼자 한 항목이면 null. 프롬프트는 읽지 않고 섹션에 저장한다
 */
public record InputItem(ItemKind kind, List<InputEvent> events, @Nullable StorylineId storyline) {

	/**
	 * @throws IllegalArgumentException 사건이 없을 때
	 */
	public InputItem {
		Objects.requireNonNull(kind, "kind");
		events = List.copyOf(Objects.requireNonNull(events, "events"));
		if (events.isEmpty()) {
			throw new IllegalArgumentException("사건이 없는 항목이다");
		}
	}

}
