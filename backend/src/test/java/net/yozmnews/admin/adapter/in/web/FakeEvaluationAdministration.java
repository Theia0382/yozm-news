package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.FakeResults.given;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.yozmnews.tag.EvaluationAdministration;
import net.yozmnews.tag.EvaluationOverview;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.EvaluationQueueCounts;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TopicCoverage;
import net.yozmnews.tag.TopicCoverageResult;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 평가 운영 API · 보도량 공개 조회 대역 — 다음에 돌려줄 결과를 시험이 정하고, 받은 호출을 순서대로 남긴다.
 */
class FakeEvaluationAdministration implements EvaluationAdministration, TopicCoverage {

	@Nullable EvaluationQueueCounts queue;

	Optional<EvaluationOverview> overview = Optional.empty();

	@Nullable TopicCoverageResult coverage;

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.queue = null;
		this.overview = Optional.empty();
		this.coverage = null;
		this.calls.clear();
	}

	@Override
	public EvaluationQueueCounts queueCounts() {
		this.calls.add("queueCounts");
		return given(this.queue);
	}

	@Override
	public Optional<EvaluationOverview> overview(TagSetId version, EvaluationPeriod period) {
		this.calls.add("overview " + version.value() + " " + period.from() + " " + period.to());
		return this.overview;
	}

	@Override
	public TopicCoverageResult count(EvaluationPeriod period, java.util.Set<String> topics) {
		this.calls.add("count " + period.from() + " " + period.to() + " " + new java.util.TreeSet<>(topics));
		return given(this.coverage);
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 — 두 공개 API 를 한 대역이 맡는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeEvaluationAdministration fakeEvaluationAdministration() {
			return new FakeEvaluationAdministration();
		}

	}

}
