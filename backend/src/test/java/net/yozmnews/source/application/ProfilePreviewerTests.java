package net.yozmnews.source.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.domain.gate.FeedPreview;
import net.yozmnews.source.domain.gate.PreviewOutcome;
import net.yozmnews.source.domain.gate.ProfilePreview;
import net.yozmnews.source.domain.profile.FeedKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 등록 전 운영자 프로필 원문의 미리보기 — 실제 PostgreSQL 과 루프백의 WireMock 피드로 본다(수집 허용 대역 {@code 127.0.0.1/32}). DB 에
 * 아무 행도 남지 않는지는 소스 · 피드 · 요청 시작 시각 예약 테이블의 행 수로 본다.
 * <p>
 * 지키는 규칙: source-R9
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "yozmnews.collection.allowed-networks=127.0.0.1/32")
class ProfilePreviewerTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	@Autowired
	ProfilePreviewer previewer;

	@Autowired
	JdbcClient jdbc;

	RegistryRows rows;

	@BeforeEach
	void deleteRows() {
		this.rows = new RegistryRows(this.jdbc);
		this.rows.deleteAll();
	}

	@Test
	void previewsAFeedOfAnUnregisteredProfileWithoutTouchingTheDatabase() {
		server.stubFor(get("/draft.xml").willReturn(TestFeeds.rss("a1", "a2", "a3")));

		ProfilePreview preview = this.previewer.preview(text("/draft.xml", ""), new FeedKey("main"));

		assertThat(preview).isInstanceOfSatisfying(ProfilePreview.Previewed.class,
				previewed -> assertThat(previewed.outcome()).isInstanceOfSatisfying(PreviewOutcome.Passed.class,
						passed -> assertThat(passed.preview().items()).isEqualTo(3)));
		assertThat(this.rows.count("sources")).isZero();
		assertThat(this.rows.count("feeds")).isZero();
		assertThat(this.rows.count("source_fetch_slots")).isZero();
	}

	@Test
	void passesAFeedWithoutDatesWhenTheProfileReadsNoPublishedAt() {
		server.stubFor(get("/nodate.xml").willReturn(aResponse()
			.withHeader("Content-Type", "application/rss+xml; charset=utf-8")
			.withBody("""
					<?xml version="1.0" encoding="UTF-8"?>
					<rss version="2.0"><channel><title>합성</title><link>https://news.example/</link>
					<description>합성 피드</description>
					<item><title>발행 시각 없음</title><link>https://news.example/n1</link></item>
					</channel></rss>
					""")));

		ProfilePreview declared = this.previewer.preview(text("/nodate.xml", """
				items:
				  publishedAt: []
				"""), new FeedKey("main"));

		assertThat(declared).isInstanceOfSatisfying(ProfilePreview.Previewed.class,
				previewed -> assertThat(previewed.outcome()).isInstanceOfSatisfying(PreviewOutcome.Passed.class,
						passed -> assertThat(passed.preview()).extracting(FeedPreview::datedArticles,
								FeedPreview::publishedAtUnread)
							.containsExactly(0, true)));
		// 대조 — 선언하지 않은 같은 원문은 날짜 기준에 못 미친다
		assertThat(this.previewer.preview(text("/nodate.xml", ""), new FeedKey("main")))
			.isInstanceOfSatisfying(ProfilePreview.Previewed.class, previewed -> assertThat(previewed.outcome())
				.isInstanceOf(PreviewOutcome.BelowCriteria.class));
	}

	@Test
	void doesNotRequestForAnInvalidProfile() {
		ProfilePreview preview = this.previewer.preview("source:\n  name: 이름만\n", new FeedKey("main"));

		assertThat(preview).isInstanceOfSatisfying(ProfilePreview.Invalid.class,
				invalid -> assertThat(invalid.problems()).isNotEmpty());
		assertThat(server.getAllServeEvents()).isEmpty();
	}

	@Test
	void doesNotRequestForAFeedKeyMissingFromTheProfile() {
		ProfilePreview preview = this.previewer.preview(text("/draft.xml", ""), new FeedKey("other"));

		assertThat(preview).isEqualTo(new ProfilePreview.FeedNotInProfile(new FeedKey("other")));
		assertThat(server.getAllServeEvents()).isEmpty();
	}

	/** 피드 main 하나가 WireMock 경로를 가리키는 운영자 프로필 원문 — 검토일은 없다(미리보기는 보지 않는다) */
	private static String text(String path, String extra) {
		return """
				source:
				  name: 초안일보
				  homepage: https://draft.example
				  language: ko
				feeds:
				  - key: main
				    url: http://127.0.0.1:%d%s
				    section: 전체
				""".formatted(server.getPort(), path) + extra;
	}

}
