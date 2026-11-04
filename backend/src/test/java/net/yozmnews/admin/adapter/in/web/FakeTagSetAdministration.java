package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.FakeResults.given;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetActivationResult;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetReviewResult;
import net.yozmnews.evaluation.TagSetVersionView;
import net.yozmnews.evaluation.ThresholdChangeResult;
import net.yozmnews.evaluation.ThresholdChangeView;
import net.yozmnews.evaluation.ThresholdStatusView;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 태그 체계 운영 API 대역 — 다음에 돌려줄 결과를 시험이 정하고, 받은 호출을 순서대로 남긴다. 태그 기준 · 저장 기준
 * 연산은 이 컨트롤러가 부르지 않는다.
 */
class FakeTagSetAdministration implements TagSetAdministration {

	List<TagSetVersionView> versions = List.of();

	Optional<String> text = Optional.empty();

	@Nullable TagSetReviewResult review;

	@Nullable TagSetActivationResult activation;

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.versions = List.of();
		this.text = Optional.empty();
		this.review = null;
		this.activation = null;
		this.calls.clear();
	}

	@Override
	public ThresholdChangeResult changeThresholds(MultiTagKind kind, BigDecimal tagThreshold, BigDecimal storeThreshold,
			ChangeActor actor) {
		throw new UnsupportedOperationException("태그 체계 컨트롤러는 부르지 않는다");
	}

	@Override
	public List<ThresholdChangeView> thresholdChanges() {
		throw new UnsupportedOperationException("태그 체계 컨트롤러는 부르지 않는다");
	}

	@Override
	public ThresholdStatusView thresholdStatus() {
		throw new UnsupportedOperationException("태그 체계 컨트롤러는 부르지 않는다");
	}

	@Override
	public List<TagSetVersionView> versions() {
		this.calls.add("versions");
		return this.versions;
	}

	@Override
	public Optional<String> versionText(TagSetId id) {
		this.calls.add("versionText " + id.value());
		return this.text;
	}

	@Override
	public TagSetReviewResult review(String text, int recentDays) {
		this.calls.add("review " + recentDays + " " + text);
		return given(this.review);
	}

	@Override
	public TagSetActivationResult activate(String text, int recentDays, ChangeActor actor) {
		this.calls.add("activate " + recentDays + " " + actor + " " + text);
		return given(this.activation);
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeTagSetAdministration fakeTagSetAdministration() {
			return new FakeTagSetAdministration();
		}

	}

}
