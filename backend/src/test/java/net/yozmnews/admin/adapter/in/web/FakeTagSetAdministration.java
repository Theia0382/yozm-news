package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.FakeResults.given;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.yozmnews.evaluation.EvaluationPeriod;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetActivationResult;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetReviewResult;
import net.yozmnews.evaluation.TagSetVersionView;
import net.yozmnews.evaluation.ThresholdChangeResult;
import net.yozmnews.evaluation.ThresholdChangeView;
import net.yozmnews.evaluation.ThresholdPreviewResult;
import net.yozmnews.evaluation.ThresholdStatusView;
import net.yozmnews.platform.ChangeActor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 태그 체계 운영 API 대역 — 다음에 돌려줄 결과를 시험이 정하고, 받은 호출을 순서대로 남긴다. 태그 체계 컨트롤러와
 * 기준 컨트롤러가 함께 쓴다. 이력 연산은 어느 컨트롤러도 부르지 않는다.
 */
class FakeTagSetAdministration implements TagSetAdministration {

	List<TagSetVersionView> versions = List.of();

	Optional<String> text = Optional.empty();

	@Nullable TagSetReviewResult review;

	@Nullable TagSetActivationResult activation;

	@Nullable ThresholdStatusView status;

	@Nullable ThresholdPreviewResult preview;

	@Nullable ThresholdChangeResult change;

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.versions = List.of();
		this.text = Optional.empty();
		this.review = null;
		this.activation = null;
		this.status = null;
		this.preview = null;
		this.change = null;
		this.calls.clear();
	}

	@Override
	public ThresholdChangeResult changeThresholds(MultiTagKind kind, BigDecimal tagThreshold, BigDecimal storeThreshold,
			ChangeActor actor) {
		this.calls.add("changeThresholds " + kind + " " + tagThreshold + " " + storeThreshold + " " + actor);
		return given(this.change);
	}

	@Override
	public List<ThresholdChangeView> thresholdChanges() {
		throw new UnsupportedOperationException("운영자 컨트롤러는 부르지 않는다 — 이력은 thresholdStatus 가 싣는다");
	}

	@Override
	public ThresholdStatusView thresholdStatus() {
		this.calls.add("thresholdStatus");
		return given(this.status);
	}

	@Override
	public ThresholdPreviewResult previewThresholds(MultiTagKind kind, BigDecimal tagThreshold,
			BigDecimal storeThreshold, EvaluationPeriod period) {
		this.calls.add("previewThresholds " + kind + " " + tagThreshold + " " + storeThreshold + " " + period.from()
				+ " " + period.to());
		return given(this.preview);
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
