package net.yozmnews.digest.domain.item;

import net.yozmnews.digest.domain.prompt.PromptRules;
import net.yozmnews.user.SummaryLength;

/**
 * 항목 가르기의 상수 · 기준 — 정본 · docs/specs/digest.md §5 · digest-R47
 */
public final class ItemRules {

	/**
	 * 큰 슬롯 항목 밖에서 주요 뉴스로 두는 항목 수 — 측정 전 목표. 주요 뉴스는 큰 슬롯 항목(사용자의 큰 슬롯 수 이하, 그 상한은 {@code user}
	 * 의 큰 슬롯 수 상한 {@code DigestVolume.MAX_BIG_SLOTS} 10) 더하기 이 수까지다 — 요약문 길이가 기준보다 짧은 항목이 빠지면 그보다
	 * 적다. 주요 뉴스 칸은 항목 별칭을 키로 한 필수 객체라 그 위쪽이 공급자가 받는 키 수 안이어야 한다 — 이 값을 올리면 그 한도를 함께 본다(시험이
	 * 묶는다)
	 */
	public static final int TOP_STORIES = 5;

	private ItemRules() {
	}

	/**
	 * 주요 뉴스가 되는 항목의 요약문 길이(RSS 요약문의 코드 포인트 합 — {@link EventSummaryLengths})의 최소 — 사용자의 요약 길이 단계에서
	 * 주요 뉴스 요약 길이 기준의 하한({@link PromptRules#summary})이다. 요약문이 그 하한보다 짧으면 요약을 쓰려고 기사에 없는 말로 늘려
	 * 쓰게 되므로 그런 항목은 헤드라인으로 둔다
	 */
	public static int minSummaryLength(SummaryLength length) {
		return PromptRules.summary(length).min();
	}

}
