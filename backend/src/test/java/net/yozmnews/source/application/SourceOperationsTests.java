package net.yozmnews.source.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.ActivationRefusal;
import net.yozmnews.source.Criterion;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FetchFailureReason;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.gate.FeedActivation;
import net.yozmnews.source.domain.gate.FeedPreview;
import net.yozmnews.source.domain.gate.Shortfall;
import net.yozmnews.source.domain.profile.Preset;
import net.yozmnews.source.domain.profile.PresetCatalog;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.profile.PresetParse;
import net.yozmnews.source.domain.profile.ProfileParser;
import net.yozmnews.source.domain.registry.Activation;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 소스 · 피드의 켜기 · 끄기와 수집 주기 — 실제 PostgreSQL 에서 등록한 소스로 본다. 피드는 루프백의 WireMock 이라 수집 허용 대역에
 * {@code 127.0.0.1/32} 를 둔다. 합성 프리셋 multi(피드 one · two) · solo(피드 all)는 이 시험의 WireMock 주소를 가리키게 문맥을
 * 띄울 때 만든다. 시험은 트랜잭션으로 감싸지 않는다.
 * <p>
 * 지키는 규칙: source-R4 · source-R5 · source-R9 · source-R14 · source-R18
 */
@Import({ TestcontainersConfiguration.class, SourceOperationsTests.WireMockPresets.class })
@SpringBootTest
@TestPropertySource(properties = "yozmnews.collection.allowed-networks=127.0.0.1/32")
class SourceOperationsTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	private static final String UNREVIEWED_TEXT = """
			source:
			  name: 운영자일보
			  homepage: https://operator.example
			  language: ko
			feeds:
			  - key: main
			    url: https://operator.example/rss.xml
			    section: 전체
			""";

	@Autowired
	SourceRegistration registration;

	@Autowired
	SourceOperations operations;

	@Autowired
	FetchedArticleRecorder recorder;

	@Autowired
	JdbcClient jdbc;

	RegistryRows rows;

	@BeforeEach
	void deleteRows() {
		this.rows = new RegistryRows(this.jdbc);
		this.rows.deleteAll();
		this.recorder.clear();
	}

	@Test
	void enablesAPresetSourceAndItsFeedAndDisablesThemAgain() {
		long source = preset("multi");
		FeedId one = new FeedId(this.rows.feedId(source, "one"));
		server.stubFor(get("/multi/one.xml").willReturn(TestFeeds.rss("a1", "a2")));

		assertThat(this.operations.enableSource(new SourceId(source))).isEqualTo(new Activation.Activated());
		FeedPreview preview = activated(this.operations.enableFeed(one));
		assertThat(preview.items()).isEqualTo(2);
		assertThat(preview.datedArticles()).isEqualTo(2);
		assertThat(sourceEnabled(source)).isTrue();
		assertThat(this.rows.feeds(source)).containsExactly("one:true", "two:false");

		this.operations.disableFeed(one);
		this.operations.disableSource(new SourceId(source));

		assertThat(sourceEnabled(source)).isFalse();
		assertThat(this.rows.feeds(source)).containsExactly("one:false", "two:false");
	}

	@Test
	void refusesAnOperatorFeedWhosePreviewMissesTheDateCriterion() {
		long source = operatorSource("/operator/dc.xml");
		server.stubFor(get("/operator/dc.xml").willReturn(aResponse()
			.withHeader("Content-Type", "application/rss+xml; charset=utf-8")
			.withBody("""
				<?xml version="1.0" encoding="UTF-8"?>
				<rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel><title>합성</title>
				<link>https://news.example/</link><description>합성 피드</description>
				<item><title>제목</title><link>https://news.example/d1</link>
				<dc:date>2026-10-25T09:31:38+09:00</dc:date></item>
				</channel></rss>
				""")));

		FeedActivation activation = this.operations.enableFeed(new FeedId(this.rows.feedId(source, "main")));

		assertThat(activation).isInstanceOfSatisfying(FeedActivation.BelowCriteria.class,
				below -> assertThat(below.shortfalls())
					.containsExactly(new Shortfall(Criterion.DATED_ARTICLES, 0.0, 0.9)));
		assertThat(this.rows.feeds(source)).containsExactly("main:false");
	}

	@Test
	void refusesAFeedThatCannotBeFetched() {
		long source = operatorSource("/operator/gone.xml");
		server.stubFor(get("/operator/gone.xml").willReturn(aResponse().withStatus(404)));

		FeedActivation activation = this.operations.enableFeed(new FeedId(this.rows.feedId(source, "main")));

		assertThat(activation).isInstanceOfSatisfying(FeedActivation.Unfetched.class, unfetched -> {
			assertThat(unfetched.failure().reason()).isEqualTo(FetchFailureReason.NOT_FOUND);
			assertThat(unfetched.failure().httpStatus()).isEqualTo(404);
		});
		assertThat(this.rows.feeds(source)).containsExactly("main:false");
	}

	@Test
	void refusesToEnableASourceWithoutTheTermsReviewDate() {
		Registration registered = this.registration.registerOperatorProfile(UNREVIEWED_TEXT);
		long source = ((Registration.Registered) registered).id().value();

		assertThat(this.operations.enableSource(new SourceId(source)))
			.isEqualTo(new Activation.Refused(ActivationRefusal.TERMS_NOT_REVIEWED));
		assertThat(sourceEnabled(source)).isFalse();
	}

	@Test
	void refusesToEnableAFeedDroppedFromThePresetWithoutRequesting() {
		long source = preset("multi");
		long gone = this.rows.insertFeed(source, "gone");

		assertThat(this.operations.enableFeed(new FeedId(gone)))
			.isEqualTo(new FeedActivation.Refused(ActivationRefusal.FEED_NOT_IN_PROFILE));
		assertThat(this.rows.feeds(source)).contains("gone:false");
		assertThat(server.getAllServeEvents()).isEmpty();
	}

	@Test
	void previewWritesNothingButTheRequestReservation() {
		long source = preset("multi");
		long one = this.rows.feedId(source, "one");
		server.stubFor(get("/multi/one.xml").willReturn(TestFeeds.rss("a1")));
		Instant nextPollBefore = nextPollAt(one);

		activated(this.operations.enableFeed(new FeedId(one)));

		assertThat(this.recorder.forFeed(one)).isEmpty();
		Map<String, Object> state = this.jdbc
			.sql("select etag, last_modified, last_polled_at from feed_poll_states where feed_id = :feed")
			.param("feed", one)
			.query()
			.singleRow();
		assertThat(state).containsEntry("etag", null)
			.containsEntry("last_modified", null)
			.containsEntry("last_polled_at", null);
		assertThat(nextPollAt(one)).isEqualTo(nextPollBefore);
		// 예약은 요청 시작 + 10초 — 수신 시각(밀리초)과 견주려 밀리초로 자른다
		Instant received = receivedAt().getFirst();
		Instant next = nextFetchAt(source).truncatedTo(ChronoUnit.MILLIS);
		assertThat(Duration.between(received, next)).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(10));
	}

	@Test
	void enablesAnEnabledFeedAgainWithoutRequesting() {
		long source = preset("multi");
		FeedId one = new FeedId(this.rows.feedId(source, "one"));
		server.stubFor(get("/multi/one.xml").willReturn(TestFeeds.rss("a1")));
		activated(this.operations.enableFeed(one));
		server.resetRequests();

		assertThat(this.operations.enableFeed(one)).isEqualTo(new FeedActivation.Activated(null));
		assertThat(server.getAllServeEvents()).isEmpty();
	}

	@Test
	void spacesThePreviewsOfOneSource() {
		long source = preset("multi");
		server.stubFor(get("/multi/one.xml").willReturn(TestFeeds.rss("a1")));
		server.stubFor(get("/multi/two.xml").willReturn(TestFeeds.rss("b1")));

		activated(this.operations.enableFeed(new FeedId(this.rows.feedId(source, "one"))));
		activated(this.operations.enableFeed(new FeedId(this.rows.feedId(source, "two"))));

		List<Instant> received = receivedAt();
		assertThat(received).hasSize(2);
		// 예약 간격은 10초 — 수신 시각의 흔들림 100ms 를 둔다
		assertThat(Duration.between(received.get(0), received.get(1))).isGreaterThanOrEqualTo(Duration.ofMillis(9_900));
	}

	@Test
	void changesAFeedIntervalOnlyFromTheMinimumUp() {
		long source = preset("solo");
		FeedId feed = new FeedId(this.rows.feedId(source, "all"));

		assertThat(this.operations.changeFeedPollInterval(feed, 14)).isFalse();
		assertThat(feedInterval(feed)).isNull();
		assertThat(this.operations.changeFeedPollInterval(feed, 30)).isTrue();
		assertThat(feedInterval(feed)).isEqualTo(30);
		assertThat(this.operations.changeFeedPollInterval(feed, null)).isTrue();
		assertThat(feedInterval(feed)).isNull();
	}

	@Test
	void changesASourceIntervalOnlyFromTheMinimumUp() {
		long source = preset("solo");

		assertThat(this.operations.changeSourcePollInterval(new SourceId(source), 0)).isFalse();
		assertThat(this.operations.changeSourcePollInterval(new SourceId(source), 15)).isTrue();
		assertThat(this.jdbc.sql("select poll_interval_minutes from sources where id = :id")
			.param("id", source)
			.query(Integer.class)
			.single()).isEqualTo(15);
	}

	private static FeedPreview activated(FeedActivation activation) {
		assertThat(activation).isInstanceOf(FeedActivation.Activated.class);
		FeedPreview preview = ((FeedActivation.Activated) activation).preview();
		assertThat(preview).isNotNull();
		return preview;
	}

	private long preset(String key) {
		return ((Registration.Registered) this.registration.registerPreset(new PresetKey(key))).id().value();
	}

	/** 이용조건 검토일이 있는 운영자 프로필 소스 — 피드 main 하나가 WireMock 경로를 가리킨다 */
	private long operatorSource(String path) {
		Registration registered = this.registration.registerOperatorProfile("""
				source:
				  name: 운영자일보
				  homepage: https://operator.example
				  language: ko
				termsReviewedAt: 2026-10-01
				feeds:
				  - key: main
				    url: http://127.0.0.1:%d%s
				    section: 전체
				""".formatted(server.getPort(), path));
		return ((Registration.Registered) registered).id().value();
	}

	/** WireMock 이 받은 요청의 수신 시각 — 이른 순 */
	private static List<Instant> receivedAt() {
		return server.getAllServeEvents()
			.stream()
			.map(ServeEvent::getRequest)
			.map(request -> request.getLoggedDate().toInstant())
			.sorted()
			.toList();
	}

	private boolean sourceEnabled(long source) {
		return this.jdbc.sql("select enabled from sources where id = :id")
			.param("id", source)
			.query(Boolean.class)
			.single();
	}

	private Instant nextPollAt(long feed) {
		return this.jdbc.sql("select next_poll_at from feed_poll_states where feed_id = :feed")
			.param("feed", feed)
			.query(OffsetDateTime.class)
			.single()
			.toInstant();
	}

	private Instant nextFetchAt(long source) {
		return this.jdbc.sql("select next_fetch_at from source_fetch_slots where source_id = :source")
			.param("source", source)
			.query(OffsetDateTime.class)
			.single()
			.toInstant();
	}

	private @Nullable Integer feedInterval(FeedId feed) {
		return this.jdbc.sql("select poll_interval_minutes from feeds where id = :id")
			.param("id", feed.value())
			.query((rs, row) -> rs.getObject("poll_interval_minutes", Integer.class))
			.list()
			.getFirst();
	}

	/**
	 * 이 시험의 프리셋 카탈로그 — 합성 프리셋 multi · solo 의 피드 URL 이 이 시험의 WireMock 이다. 문맥을 띄울 때 만들므로 WireMock 이
	 * 그 전에 떠 있어야 한다(정적 확장은 모든 시험 전에 뜬다).
	 */
	@TestConfiguration(proxyBeanMethods = false)
	static class WireMockPresets {

		@Bean
		@Primary
		PresetCatalog wireMockPresetCatalog(ProfileParser profileParser) {
			String base = "http://127.0.0.1:" + server.getPort();
			return new PresetCatalog(List.of(preset("multi", """
					source:
					  name: 멀티일보
					  homepage: https://multi.example
					  language: ko
					termsReviewedAt: 2026-10-20
					feeds:
					  - key: one
					    url: %1$s/multi/one.xml
					    section: 하나
					  - key: two
					    url: %1$s/multi/two.xml
					    section: 둘
					""".formatted(base), profileParser), preset("solo", """
					source:
					  name: 솔로뉴스
					  homepage: https://solo.example
					  language: ko
					termsReviewedAt: 2026-10-21
					feeds:
					  - key: all
					    url: %s/solo/all.xml
					    section: 전체
					""".formatted(base), profileParser)));
		}

		private static Preset preset(String key, String text, ProfileParser parser) {
			return ((PresetParse.Parsed) Preset.parse(new PresetKey(key), text, parser)).preset();
		}

	}

}
