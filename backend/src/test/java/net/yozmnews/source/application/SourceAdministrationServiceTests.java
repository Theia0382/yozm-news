package net.yozmnews.source.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.ActivationRefusal;
import net.yozmnews.source.ArticleSampleView;
import net.yozmnews.source.Criterion;
import net.yozmnews.source.CriterionShortfall;
import net.yozmnews.source.FailurePermanence;
import net.yozmnews.source.FeedEnableResult;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedView;
import net.yozmnews.source.FetchFailureReason;
import net.yozmnews.source.FetchFailureView;
import net.yozmnews.source.PresetView;
import net.yozmnews.source.PreviewReport;
import net.yozmnews.source.ProfilePreviewResult;
import net.yozmnews.source.ProfileProblemView;
import net.yozmnews.source.ProfileRevisionResult;
import net.yozmnews.source.ProfileState;
import net.yozmnews.source.SourceAdministration;
import net.yozmnews.source.SourceEnableResult;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.SourceRegistrationResult;
import net.yozmnews.source.SourceView;
import net.yozmnews.source.SummaryLengthsView;
import net.yozmnews.source.UnknownFeedException;
import net.yozmnews.source.UnknownSourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 공개 운영 API — 목록 · 등록 · 수정 · 미리보기 · 켜기의 결과를 공개 타입으로 옮기는지, 실제 PostgreSQL 과 합성 프리셋 multi(피드 one ·
 * two) · solo(피드 all), 루프백 WireMock 피드로 본다.
 * <p>
 * 지키는 규칙: source-R1 · source-R2 · source-R4 · source-R8 · source-R9 · source-R14 · source-R50 · source-R51 · source-R52
 */
@Import({ TestcontainersConfiguration.class, RegistryTestCatalog.class })
@SpringBootTest
@TestPropertySource(properties = "yozmnews.collection.allowed-networks=127.0.0.1/32")
class SourceAdministrationServiceTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	@Autowired
	SourceAdministration administration;

	@Autowired
	JdbcClient jdbc;

	RegistryRows rows;

	@BeforeEach
	void deleteRows() {
		this.rows = new RegistryRows(this.jdbc);
		this.rows.deleteAll();
	}

	@Test
	void minimumPollIntervalIsFifteenMinutes() {
		assertThat(SourceAdministration.MINIMUM_POLL_INTERVAL_MINUTES).isEqualTo(15);
	}

	@Test
	void presetsListTheCatalogInKeyOrderWithTheirSource() {
		SourceId multi = registered(this.administration.registerPreset("multi"));

		assertThat(this.administration.presets()).containsExactly(
				new PresetView("multi", "멀티일보", URI.create("https://multi.example"), LocalDate.parse("2026-10-20"), 2,
						multi),
				new PresetView("solo", "솔로뉴스", URI.create("https://solo.example"), LocalDate.parse("2026-10-21"), 1,
						null));
	}

	@Test
	void registeringAPresetReportsMissingMalformedAndRepeatedKeys() {
		assertThat(this.administration.registerPreset("gone"))
			.isEqualTo(new SourceRegistrationResult.PresetNotFound("gone"));
		assertThat(this.administration.registerPreset("Bad Key"))
			.isEqualTo(new SourceRegistrationResult.PresetNotFound("Bad Key"));
		registered(this.administration.registerPreset("solo"));
		assertThat(this.administration.registerPreset("solo"))
			.isEqualTo(new SourceRegistrationResult.PresetAlreadyRegistered("solo"));
	}

	@Test
	void sourcesShowTheOperatingSettingsAndTheEffectiveInterval() {
		long source = registered(this.administration.registerPreset("multi")).value();
		FeedId one = new FeedId(this.rows.feedId(source, "one"));
		FeedId two = new FeedId(this.rows.feedId(source, "two"));
		this.administration.changeSourcePollInterval(new SourceId(source), 30);
		this.administration.changeFeedPollInterval(two, 45);

		assertThat(this.administration.sources()).containsExactly(new SourceView(new SourceId(source), "multi", false,
				30, ProfileState.AVAILABLE, "멀티일보", URI.create("https://multi.example"), LocalDate.parse("2026-10-20"),
				List.of(new FeedView(one, "one", false, null, true, URI.create("https://multi.example/rss/one.xml"), "하나",
						30),
						new FeedView(two, "two", false, 45, true, URI.create("https://multi.example/rss/two.xml"), "둘",
								45))));
	}

	@Test
	void aSourceThatLostItsPresetOrHasAnInvalidTextShowsWhy() {
		long vanished = this.jdbc
			.sql("insert into sources (preset_key, enabled, version, created_at) values ('vanished', true, 0, now()) "
					+ "returning id")
			.query(Long.class)
			.single();
		long feed = this.rows.insertFeed(vanished, "lost");
		long invalid = this.jdbc
			.sql("insert into sources (profile, enabled, version, created_at) values ('source:\n  name: 이름만\n', false, 0, "
					+ "now()) returning id")
			.query(Long.class)
			.single();

		assertThat(this.administration.sources()).containsExactly(
				new SourceView(new SourceId(vanished), "vanished", true, null, ProfileState.PRESET_MISSING, null, null,
						null, List.of(new FeedView(new FeedId(feed), "lost", false, null, false, null, null, null))),
				new SourceView(new SourceId(invalid), null, false, null, ProfileState.INVALID, null, null, null,
						List.of()));
	}

	@Test
	void operatorProfileTextIsReturnedOnlyForOperatorSources() {
		String text = operatorText("/admin/none.xml", true);
		SourceId operator = registered(this.administration.registerOperatorProfile(text));
		SourceId preset = registered(this.administration.registerPreset("solo"));

		assertThat(this.administration.operatorProfileText(operator)).contains(text);
		assertThat(this.administration.operatorProfileText(preset)).isEqualTo(Optional.empty());
		assertThatExceptionOfType(UnknownSourceException.class)
			.isThrownBy(() -> this.administration.operatorProfileText(new SourceId(999_999)));
	}

	@Test
	void anInvalidOperatorTextReportsEachProblemWithItsPlace() {
		String text = operatorText("/admin/none.xml", true) + "script: x\n";

		assertThat(this.administration.registerOperatorProfile(text)).isInstanceOfSatisfying(
				SourceRegistrationResult.InvalidProfile.class,
				invalid -> assertThat(invalid.problems()).singleElement()
					.isEqualTo(new ProfileProblemView("script", "UNKNOWN_KEY", "알 수 없는 항목 'script'", 10, 1)));
		assertThat(this.rows.count("sources")).isZero();
	}

	@Test
	void previewReportsThePassedMetricsAndSamples() {
		server.stubFor(get("/admin/pass.xml").willReturn(TestFeeds.rss("p1", "p2")));

		ProfilePreviewResult result = this.administration
			.previewOperatorProfile(operatorText("/admin/pass.xml", true), "main");

		// 읽은 문자 437 은 TestFeeds.rss("p1", "p2") 본문의 문자 수다(한글 포함, 따로 센 값)
		assertThat(result).isEqualTo(new ProfilePreviewResult.Passed(new PreviewReport(2, 2, List.of(), 2, false,
				new SummaryLengthsView(0, 2, null, null, null), 0, 437, Instant.parse("2026-10-28T00:00:00Z"),
				null,
				List.of(new ArticleSampleView("제목 p1", Instant.parse("2026-10-28T00:00:00Z"), false),
						new ArticleSampleView("제목 p2", Instant.parse("2026-10-28T00:00:00Z"), false)))));
	}

	@Test
	void previewReportsAFetchFailureAndUnknownFeeds() {
		server.stubFor(get("/admin/gone.xml").willReturn(aResponse().withStatus(404)));
		String text = operatorText("/admin/gone.xml", true);

		assertThat(this.administration.previewOperatorProfile(text, "main"))
			.isEqualTo(new ProfilePreviewResult.Unfetched(new FetchFailureView(FetchFailureReason.NOT_FOUND,
					FailurePermanence.PERMANENT, "피드가 없다는 응답 (HTTP 404)", 404, null)));
		assertThat(this.administration.previewOperatorProfile(text, "other"))
			.isEqualTo(new ProfilePreviewResult.FeedNotInProfile("other"));
		assertThat(this.administration.previewOperatorProfile(text, "Main!"))
			.isEqualTo(new ProfilePreviewResult.FeedNotInProfile("Main!"));
		assertThat(this.administration.previewOperatorProfile("source: x\n", "main"))
			.isInstanceOf(ProfilePreviewResult.InvalidProfile.class);
	}

	@Test
	void enablingReportsRefusalsShortfallsAndTheEnabledPreview() {
		SourceId unreviewed = registered(this.administration.registerOperatorProfile(operatorText("/admin/dc.xml", false)));
		server.stubFor(get("/admin/dc.xml").willReturn(aResponse()
			.withHeader("Content-Type", "application/rss+xml; charset=utf-8")
			.withBody("""
				<?xml version="1.0" encoding="UTF-8"?>
				<rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel><title>합성</title>
				<link>https://news.example/</link><description>합성 피드</description>
				<item><title>제목</title><link>https://news.example/d1</link>
				<dc:date>2026-10-25T09:31:38+09:00</dc:date></item>
				</channel></rss>
				""")));
		FeedId dc = new FeedId(this.rows.feedId(unreviewed.value(), "main"));

		assertThat(this.administration.enableSource(unreviewed))
			.isEqualTo(new SourceEnableResult.Refused(ActivationRefusal.TERMS_NOT_REVIEWED));
		assertThat(this.administration.enableFeed(dc)).isInstanceOfSatisfying(FeedEnableResult.BelowCriteria.class,
				below -> assertThat(below.shortfalls())
					.containsExactly(new CriterionShortfall(Criterion.DATED_ARTICLES, 0.0, 0.9)));

		server.stubFor(get("/admin/ok.xml").willReturn(TestFeeds.rss("o1")));
		SourceId reviewed = registered(this.administration.registerOperatorProfile(operatorText("/admin/ok.xml", true)));
		FeedId ok = new FeedId(this.rows.feedId(reviewed.value(), "main"));
		assertThat(this.administration.enableSource(reviewed)).isEqualTo(new SourceEnableResult.Enabled());
		assertThat(this.administration.enableFeed(ok)).isInstanceOfSatisfying(FeedEnableResult.Enabled.class,
				enabled -> assertThat(enabled.preview()).isNotNull()
					.satisfies(preview -> assertThat(preview.articles()).isEqualTo(1)));
		assertThat(this.administration.enableFeed(ok)).isEqualTo(new FeedEnableResult.Enabled(null));
	}

	@Test
	void revisionReportsTheFeedsItTurnedOffAndAdded() {
		String text = operatorText("/admin/rev.xml", true);
		SourceId source = registered(this.administration.registerOperatorProfile(text));
		long main = this.rows.feedId(source.value(), "main");
		this.jdbc.sql("update feeds set enabled = true where id = :id").param("id", main).update();

		ProfileRevisionResult result = this.administration.reviseOperatorProfile(source,
				text.replace("rev.xml", "rev-v2.xml") + """
						  - key: added
						    url: https://operator.example/added.xml
						    section: 새것
						""");

		assertThat(result).isEqualTo(new ProfileRevisionResult.Revised(List.of("main"), false, List.of("added")));
		assertThat(this.administration.reviseOperatorProfile(registered(this.administration.registerPreset("solo")),
				text))
			.isEqualTo(new ProfileRevisionResult.NotOperatorProfile());
	}

	@Test
	void unknownIdsPassThroughAsUnknown() {
		assertThatExceptionOfType(UnknownFeedException.class)
			.isThrownBy(() -> this.administration.enableFeed(new FeedId(999_999)));
		assertThatExceptionOfType(UnknownSourceException.class)
			.isThrownBy(() -> this.administration.reviseOperatorProfile(new SourceId(999_999), "x"));
	}

	/** 피드 main 하나가 이 시험의 WireMock 경로를 가리키는 9줄짜리 운영자 원문 — 뒤에 더한 줄은 10번째 줄이다 */
	private static String operatorText(String path, boolean reviewed) {
		return """
				source:
				  name: 운영자일보
				  homepage: https://operator.example
				  language: ko
				%sfeeds:
				  - key: main
				    url: http://127.0.0.1:%d%s
				    section: 전체
				""".formatted(reviewed ? "termsReviewedAt: 2026-10-01\n" : "# 검토일 없음\n", server.getPort(), path);
	}

	private static SourceId registered(SourceRegistrationResult result) {
		assertThat(result).isInstanceOf(SourceRegistrationResult.Registered.class);
		return ((SourceRegistrationResult.Registered) result).id();
	}

}
