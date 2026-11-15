package net.yozmnews.tag;

import java.util.Objects;

/**
 * 태그 체계의 활성 전환 — 활성화가 새 버전을 쓴 트랜잭션에서 낸다(tag → user) — docs/specs/tag.md §3 · tag-R16.
 * <p>
 * 사용자를 모른다 — 버전 ID 둘만 싣는다. 이벤트 발행 레지스트리에서 다시 처리될 수 있어, 소비자는 두 ID 를 믿지 않고 지금 활성 버전과 자기 기록을
 * 대조한다. 첫 기동 적재는 내지 않는다.
 * @param previous 활성화가 끈 기준 버전
 * @param activated 새로 켠 버전
 */
public record TagSetActivated(TagSetId previous, TagSetId activated) {

	public TagSetActivated {
		Objects.requireNonNull(previous, "previous");
		Objects.requireNonNull(activated, "activated");
	}

}
