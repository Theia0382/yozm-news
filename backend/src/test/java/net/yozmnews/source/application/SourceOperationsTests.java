package net.yozmnews.source.application;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.registry.Activation;
import net.yozmnews.source.domain.registry.ActivationRefusal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 소스 · 피드의 켜기 · 끄기와 수집 주기 — 실제 PostgreSQL 에서 등록한 소스로 본다.
 * <p>
 * 지키는 규칙: source-R4 · source-R5 · source-R14
 */
@Import({ TestcontainersConfiguration.class, RegistryTestCatalog.class })
@SpringBootTest
class SourceOperationsTests {

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
	JdbcClient jdbc;

	RegistryRows rows;

	@BeforeEach
	void deleteRows() {
		this.rows = new RegistryRows(this.jdbc);
		this.rows.deleteAll();
	}

	@Test
	void enablesAPresetSourceAndItsFeedAndDisablesThemAgain() {
		long source = preset("multi");
		FeedId one = new FeedId(this.rows.feedId(source, "one"));

		assertThat(this.operations.enableSource(new SourceId(source))).isEqualTo(new Activation.Activated());
		assertThat(this.operations.enableFeed(one)).isEqualTo(new Activation.Activated());
		assertThat(sourceEnabled(source)).isTrue();
		assertThat(this.rows.feeds(source)).containsExactly("one:true", "two:false");

		this.operations.disableFeed(one);
		this.operations.disableSource(new SourceId(source));

		assertThat(sourceEnabled(source)).isFalse();
		assertThat(this.rows.feeds(source)).containsExactly("one:false", "two:false");
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
	void refusesToEnableAFeedDroppedFromThePreset() {
		long source = preset("multi");
		long gone = this.rows.insertFeed(source, "gone");

		assertThat(this.operations.enableFeed(new FeedId(gone)))
			.isEqualTo(new Activation.Refused(ActivationRefusal.FEED_NOT_IN_PROFILE));
		assertThat(this.rows.feeds(source)).contains("gone:false");
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

	private long preset(String key) {
		return ((Registration.Registered) this.registration.registerPreset(new PresetKey(key))).id().value();
	}

	private boolean sourceEnabled(long source) {
		return this.jdbc.sql("select enabled from sources where id = :id")
			.param("id", source)
			.query(Boolean.class)
			.single();
	}

	private @Nullable Integer feedInterval(FeedId feed) {
		return this.jdbc.sql("select poll_interval_minutes from feeds where id = :id")
			.param("id", feed.value())
			.query((rs, row) -> rs.getObject("poll_interval_minutes", Integer.class))
			.list()
			.getFirst();
	}

}
