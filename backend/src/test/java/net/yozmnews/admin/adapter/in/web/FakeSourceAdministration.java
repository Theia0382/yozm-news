package net.yozmnews.admin.adapter.in.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.yozmnews.source.FeedEnableResult;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.PresetView;
import net.yozmnews.source.ProfilePreviewResult;
import net.yozmnews.source.ProfileRevisionResult;
import net.yozmnews.source.SourceAdministration;
import net.yozmnews.source.SourceEnableResult;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.SourceRegistrationResult;
import net.yozmnews.source.SourceView;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 공개 운영 API 대역 — 다음에 돌려줄 결과 · 던질 예외를 시험이 정하고, 받은 호출을 순서대로 남긴다.
 */
class FakeSourceAdministration implements SourceAdministration {

	List<PresetView> presets = List.of();

	List<SourceView> sources = List.of();

	Optional<String> profileText = Optional.empty();

	@Nullable SourceRegistrationResult registration;

	@Nullable ProfileRevisionResult revision;

	@Nullable ProfilePreviewResult preview;

	@Nullable SourceEnableResult sourceEnable;

	@Nullable FeedEnableResult feedEnable;

	boolean intervalAccepted = true;

	/** 정하면 모든 호출이 이 예외를 던진다 */
	@Nullable RuntimeException failure;

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.presets = List.of();
		this.sources = List.of();
		this.profileText = Optional.empty();
		this.registration = null;
		this.revision = null;
		this.preview = null;
		this.sourceEnable = null;
		this.feedEnable = null;
		this.intervalAccepted = true;
		this.failure = null;
		this.calls.clear();
	}

	@Override
	public List<PresetView> presets() {
		called("presets");
		return this.presets;
	}

	@Override
	public List<SourceView> sources() {
		called("sources");
		return this.sources;
	}

	@Override
	public Optional<String> operatorProfileText(SourceId id) {
		called("operatorProfileText " + id.value());
		return this.profileText;
	}

	@Override
	public SourceRegistrationResult registerPreset(String presetKey) {
		called("registerPreset " + presetKey);
		return given(this.registration);
	}

	@Override
	public SourceRegistrationResult registerOperatorProfile(String text) {
		called("registerOperatorProfile " + text);
		return given(this.registration);
	}

	@Override
	public ProfileRevisionResult reviseOperatorProfile(SourceId id, String text) {
		called("reviseOperatorProfile " + id.value() + " " + text);
		return given(this.revision);
	}

	@Override
	public ProfilePreviewResult previewOperatorProfile(String text, String feedKey) {
		called("previewOperatorProfile " + feedKey + " " + text);
		return given(this.preview);
	}

	@Override
	public SourceEnableResult enableSource(SourceId id) {
		called("enableSource " + id.value());
		return given(this.sourceEnable);
	}

	@Override
	public void disableSource(SourceId id) {
		called("disableSource " + id.value());
	}

	@Override
	public FeedEnableResult enableFeed(FeedId id) {
		called("enableFeed " + id.value());
		return given(this.feedEnable);
	}

	@Override
	public void disableFeed(FeedId id) {
		called("disableFeed " + id.value());
	}

	@Override
	public boolean changeSourcePollInterval(SourceId id, @Nullable Integer minutes) {
		called("changeSourcePollInterval " + id.value() + " " + minutes);
		return this.intervalAccepted;
	}

	@Override
	public boolean changeFeedPollInterval(FeedId id, @Nullable Integer minutes) {
		called("changeFeedPollInterval " + id.value() + " " + minutes);
		return this.intervalAccepted;
	}

	private void called(String call) {
		this.calls.add(call);
		if (this.failure != null) {
			throw this.failure;
		}
	}

	private static <T> T given(@Nullable T result) {
		if (result == null) {
			throw new IllegalStateException("시험이 결과를 정하지 않았다");
		}
		return result;
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeSourceAdministration fakeSourceAdministration() {
			return new FakeSourceAdministration();
		}

	}

}
