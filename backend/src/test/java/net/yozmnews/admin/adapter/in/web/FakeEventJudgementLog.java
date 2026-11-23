package net.yozmnews.admin.adapter.in.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.evaluation.EventJudgementCursor;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.evaluation.EventJudgementLog;
import net.yozmnews.evaluation.EventJudgementPage;
import net.yozmnews.evaluation.EventJudgementView;
import net.yozmnews.evaluation.JudgementPeriod;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 판정 원본 조회 대역 — 다음에 돌려줄 쪽과 ID 로 찾을 원본을 시험이 정하고, 받은 호출을 순서대로 남긴다.
 */
class FakeEventJudgementLog implements EventJudgementLog {

	@Nullable EventJudgementPage page;

	final Map<EventJudgementId, EventJudgementView> judgements = new LinkedHashMap<>();

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.page = null;
		this.judgements.clear();
		this.calls.clear();
	}

	@Override
	public EventJudgementPage list(JudgementPeriod period, @Nullable EventJudgementCursor after, int size) {
		this.calls.add("list " + period.from() + " ~ " + period.to() + " after "
				+ ((after == null) ? null : after.judgedAt() + "/" + after.id().value()) + " size " + size);
		return FakeResults.given(this.page);
	}

	@Override
	public Optional<EventJudgementView> find(EventJudgementId id) {
		this.calls.add("find " + id.value());
		return Optional.ofNullable(this.judgements.get(id));
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeEventJudgementLog fakeEventJudgementLog() {
			return new FakeEventJudgementLog();
		}

	}

}
