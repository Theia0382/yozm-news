package net.yozmnews.admin.adapter.in.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.event.BigEvent;
import net.yozmnews.event.BigEventAdministration;
import net.yozmnews.event.BigEventCriteria;
import net.yozmnews.event.BigEventCriteriaChange;
import net.yozmnews.event.BigEventCriteriaChangeResult;
import net.yozmnews.event.EventPeriod;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 큰 사건 기준 운영 API 대역 — 다음에 돌려줄 값을 시험이 정하고, 받은 호출을 순서대로 남긴다.
 */
class FakeBigEventAdministration implements BigEventAdministration {

	BigEventCriteria criteria = BigEventCriteria.DEFAULT;

	List<BigEventCriteriaChange> changes = List.of();

	List<BigEvent> preview = List.of();

	@Nullable BigEventCriteriaChangeResult change;

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.criteria = BigEventCriteria.DEFAULT;
		this.changes = List.of();
		this.preview = List.of();
		this.change = null;
		this.calls.clear();
	}

	@Override
	public BigEventCriteria criteria() {
		this.calls.add("criteria");
		return this.criteria;
	}

	@Override
	public BigEventCriteriaChangeResult change(int minSources, BigDecimal minImportance, ChangeActor actor) {
		this.calls.add("change " + minSources + " " + minImportance.toPlainString() + " " + actor);
		return FakeResults.given(this.change);
	}

	@Override
	public List<BigEventCriteriaChange> changes() {
		this.calls.add("changes");
		return this.changes;
	}

	@Override
	public List<BigEvent> preview(BigEventCriteria proposed, EventPeriod period) {
		this.calls.add("preview " + proposed.minSources() + " " + proposed.minImportance().toPlainString() + " "
				+ period.from() + " ~ " + period.to());
		return this.preview;
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeBigEventAdministration fakeBigEventAdministration() {
			return new FakeBigEventAdministration();
		}

	}

}
