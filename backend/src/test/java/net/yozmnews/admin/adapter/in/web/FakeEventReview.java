package net.yozmnews.admin.adapter.in.web;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventReview;
import net.yozmnews.event.JudgementUse;
import net.yozmnews.event.MergedEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 운영자 사건 조회 대역 — 시험이 넣은 값을 돌려주고, 받은 호출을 순서대로 남긴다. 쓰임은 넣어 둔 원본만 낸다(가리키는 기록이
 * 없는 원본은 빠진다).
 */
class FakeEventReview implements EventReview {

	AssignmentCriteria criteria = new AssignmentCriteria(Duration.ofHours(72), 0.8, 0.55, 0.4, 0.5);

	final Map<EventId, List<MergedEvent>> merged = new LinkedHashMap<>();

	final Map<EventJudgementId, JudgementUse> uses = new LinkedHashMap<>();

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.criteria = new AssignmentCriteria(Duration.ofHours(72), 0.8, 0.55, 0.4, 0.5);
		this.merged.clear();
		this.uses.clear();
		this.calls.clear();
	}

	@Override
	public AssignmentCriteria assignmentCriteria() {
		this.calls.add("assignmentCriteria");
		return this.criteria;
	}

	@Override
	public List<MergedEvent> mergedInto(EventId survivor) {
		this.calls.add("mergedInto " + survivor.value());
		return this.merged.getOrDefault(survivor, List.of());
	}

	@Override
	public Map<EventJudgementId, JudgementUse> judgementUses(Collection<EventJudgementId> judgements) {
		this.calls.add("judgementUses " + judgements.stream().map(EventJudgementId::value).toList());
		Map<EventJudgementId, JudgementUse> found = new LinkedHashMap<>();
		judgements.forEach(id -> {
			JudgementUse use = this.uses.get(id);
			if (use != null) {
				found.put(id, use);
			}
		});
		return found;
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeEventReview fakeEventReview() {
			return new FakeEventReview();
		}

	}

}
