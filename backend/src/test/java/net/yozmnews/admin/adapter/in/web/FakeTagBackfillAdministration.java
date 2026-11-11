package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.FakeResults.given;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.tag.TagBackfillAdministration;
import net.yozmnews.tag.TagBackfillRequestResult;
import net.yozmnews.tag.TagBackfillReviewResult;
import net.yozmnews.tag.TagBackfillRunView;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 빠진 태그 채움 운영 API 대역 — 다음에 돌려줄 결과를 시험이 정하고, 받은 호출을 순서대로 남긴다.
 */
class FakeTagBackfillAdministration implements TagBackfillAdministration {

	@Nullable TagBackfillReviewResult review;

	@Nullable TagBackfillRequestResult request;

	List<TagBackfillRunView> runs = List.of();

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.review = null;
		this.request = null;
		this.runs = List.of();
		this.calls.clear();
	}

	@Override
	public TagBackfillReviewResult review(int days) {
		this.calls.add("review " + days);
		return given(this.review);
	}

	@Override
	public TagBackfillRequestResult request(int days) {
		this.calls.add("request " + days);
		return given(this.request);
	}

	@Override
	public List<TagBackfillRunView> runs() {
		this.calls.add("runs");
		return this.runs;
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeTagBackfillAdministration fakeTagBackfillAdministration() {
			return new FakeTagBackfillAdministration();
		}

	}

}
