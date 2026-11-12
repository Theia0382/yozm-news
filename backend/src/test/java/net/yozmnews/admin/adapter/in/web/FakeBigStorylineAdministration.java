package net.yozmnews.admin.adapter.in.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.event.EventPeriod;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.BigStorylineAdministration;
import net.yozmnews.storyline.BigStorylineCriteria;
import net.yozmnews.storyline.BigStorylineCriteriaChange;
import net.yozmnews.storyline.BigStorylineCriteriaChangeResult;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 큰 판정 기준 운영 API 대역 — 다음에 돌려줄 값을 시험이 정하고, 받은 호출을 순서대로 남긴다.
 */
class FakeBigStorylineAdministration implements BigStorylineAdministration {

	BigStorylineCriteria criteria = BigStorylineCriteria.DEFAULT;

	List<BigStorylineCriteriaChange> changes = List.of();

	List<BigStoryline> preview = List.of();

	@Nullable BigStorylineCriteriaChangeResult change;

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.criteria = BigStorylineCriteria.DEFAULT;
		this.changes = List.of();
		this.preview = List.of();
		this.change = null;
		this.calls.clear();
	}

	@Override
	public BigStorylineCriteria criteria() {
		this.calls.add("criteria");
		return this.criteria;
	}

	@Override
	public BigStorylineCriteriaChangeResult change(int minSources, BigDecimal minImportance, ChangeActor actor) {
		this.calls.add("change " + minSources + " " + minImportance.toPlainString() + " " + actor);
		return FakeResults.given(this.change);
	}

	@Override
	public List<BigStorylineCriteriaChange> changes() {
		this.calls.add("changes");
		return this.changes;
	}

	@Override
	public List<BigStoryline> preview(BigStorylineCriteria proposed, EventPeriod period) {
		this.calls.add("preview " + proposed.minSources() + " " + proposed.minImportance().toPlainString() + " "
				+ period.from() + " ~ " + period.to());
		return this.preview;
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeBigStorylineAdministration fakeBigStorylineAdministration() {
			return new FakeBigStorylineAdministration();
		}

	}

}
