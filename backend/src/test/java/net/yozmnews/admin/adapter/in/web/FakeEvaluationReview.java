package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.FakeResults.given;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.yozmnews.tag.EvaluationCursor;
import net.yozmnews.tag.EvaluationDetailView;
import net.yozmnews.tag.EvaluationFilter;
import net.yozmnews.tag.EvaluationId;
import net.yozmnews.tag.EvaluationListResult;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.EvaluationReview;
import net.yozmnews.tag.EvaluationSampleResult;
import net.yozmnews.tag.TagSetId;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 평가 검수 운영 API 대역 — 다음에 돌려줄 결과를 시험이 정하고, 받은 호출을 순서대로 남긴다.
 */
class FakeEvaluationReview implements EvaluationReview {

	@Nullable EvaluationListResult list;

	@Nullable EvaluationSampleResult sample;

	Optional<EvaluationDetailView> detail = Optional.empty();

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.list = null;
		this.sample = null;
		this.detail = Optional.empty();
		this.calls.clear();
	}

	@Override
	public EvaluationListResult list(TagSetId version, EvaluationPeriod period, EvaluationFilter filter,
			@Nullable EvaluationCursor after, int limit) {
		this.calls.add("list " + version.value() + " " + period.from() + " " + period.to() + " " + filter + " " + after
				+ " " + limit);
		return given(this.list);
	}

	@Override
	public EvaluationSampleResult sample(TagSetId version, EvaluationPeriod period, long seed, int size) {
		this.calls.add("sample " + version.value() + " " + period.from() + " " + period.to() + " " + seed + " " + size);
		return given(this.sample);
	}

	@Override
	public Optional<EvaluationDetailView> detail(EvaluationId id) {
		this.calls.add("detail " + id.value());
		return this.detail;
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeEvaluationReview fakeEvaluationReview() {
			return new FakeEvaluationReview();
		}

	}

}
