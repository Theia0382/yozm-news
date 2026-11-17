package net.yozmnews.storyline.domain.linking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.yozmnews.evaluation.EventDescriptor;
import org.junit.jupiter.api.Test;

/**
 * 띠 안 후보를 물을 때 — 후보 스토리라인의 서술은 판정할 사건과 중심 코사인이 큰 구성 사건 다섯(자신 제외, 같으면 사건 ID 순, 셀 수 없는
 * 것은 뒤), 다시 물을 때는 어느 쪽 크기든 물었을 때의 두 배 이상.
 * <p>
 * 지키는 규칙: storyline-R36
 */
class LinkingAskTests {

	private static final Instant AT = Instant.parse("2026-11-05T00:00:00Z");

	private static final Subject SUBJECT = new Subject(new float[] { 1, 0 }, AT, AT, List.of());

	@Test
	void describesTheFiveClosestMembersWithoutTheJudgedEvent() {
		Map<Long, float[]> members = new TreeMap<>();
		members.put(1L, new float[] { 1, 0 });
		members.put(2L, new float[] { 0, 1 });
		members.put(3L, new float[] { 4, 3 });
		members.put(4L, new float[] { 3, 4 });
		members.put(5L, new float[] { 1, 0 });
		members.put(6L, new float[] { 1, 1 });
		members.put(7L, new float[] { 4, 3 });

		// 1 은 판정할 사건 — 코사인 5 · 1.0, 3 · 7 0.8, 6 0.707, 4 0.6, 2 0.0
		assertThat(LinkingAsk.describe(SUBJECT, 1, members)).containsExactly(5L, 3L, 7L, 6L, 4L);
	}

	@Test
	void describesNoMoreEventsThanAFlowCandidateTakes() {
		// 서술 사건마다 대표 기사 하나 — 흐름 소속 판정의 후보는 서술 기사를 이만큼까지만 받는다
		assertThat(LinkingAsk.DESCRIBED_EVENTS).isLessThanOrEqualTo(EventDescriptor.MAX_ARTICLES);
	}

	@Test
	void putsMembersWhoseCosineCannotBeCountedLast() {
		Map<Long, float[]> members = new TreeMap<>();
		members.put(2L, new float[] { 0, 0 });
		members.put(3L, new float[] { 0, 1 });

		assertThat(LinkingAsk.describe(SUBJECT, 1, members)).containsExactly(3L, 2L);
	}

	@Test
	void asksAgainWhenEitherSideDoubles() {
		AskedRecord record = new AskedRecord(new Asked(0.71, 9L), 2, 3);

		assertThat(LinkingAsk.due(record, 3, 5)).isFalse();
		assertThat(LinkingAsk.due(record, 4, 3)).isTrue();
		assertThat(LinkingAsk.due(record, 2, 6)).isTrue();
	}

}
