package net.yozmnews.source.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.Optional;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedRemovalRefusal;
import net.yozmnews.source.FeedRemovalResult;
import net.yozmnews.source.FeedView;
import net.yozmnews.source.PresetView;
import net.yozmnews.source.SourceAdministration;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.SourceRegistrationResult;
import net.yozmnews.source.SourceView;
import net.yozmnews.source.UnknownFeedException;
import net.yozmnews.source.UnknownSourceException;
import net.yozmnews.source.domain.registry.Feed;
import net.yozmnews.source.domain.registry.FeedRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 소스 · 피드의 숨김 — 공개 운영 API 로 숨기고 다시 보이며, 운영 목록 · 운영 연산 · 저장된 행을 잰다.
 * <p>
 * 지키는 규칙: 숨김(docs/specs/source.md §5) · source-R52
 */
@Import({ TestcontainersConfiguration.class, RegistryTestCatalog.class })
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class SourceRemovalTests {

	/** 운영자 원문 — 피드 main */
	private static final String OPERATOR_TEXT = """
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
	SourceAdministration administration;

	@Autowired
	FeedRepository feeds;

	@Autowired
	JdbcClient jdbc;

	RegistryRows rows;

	@BeforeEach
	void deleteRows() {
		this.rows = new RegistryRows(this.jdbc);
		this.rows.deleteAll();
	}

	@Test
	void hidingASourceTurnsItAndItsFeedsOffAndMovesItToTheHiddenList(CapturedOutput output) {
		SourceId hidden = registered(this.administration.registerPreset("multi"));
		SourceId kept = registered(this.administration.registerPreset("solo"));
		this.jdbc.sql("update sources set enabled = true where id = :id").param("id", hidden.value()).update();
		this.jdbc.sql("update feeds set enabled = true where source_id = :id").param("id", hidden.value()).update();

		this.administration.hideSource(hidden);

		assertThat(this.administration.sources()).extracting(SourceView::id).containsExactly(kept);
		assertThat(this.administration.hiddenSources()).singleElement().satisfies(view -> {
			assertThat(view.id()).isEqualTo(hidden);
			assertThat(view.enabled()).isFalse();
			assertThat(view.feeds()).extracting(FeedView::enabled).containsExactly(false, false);
		});
		assertThat(this.rows.feeds(hidden.value())).containsExactly("one:false", "two:false");
		assertThat(this.rows.count("feed_poll_states")).isEqualTo(3);
		assertThat(hiddenAt("sources", hidden.value())).isNotNull();
		assertThat(output).contains("운영자가 소스를 숨겼다 — 소스 " + hidden.value() + " 프리셋 multi");
	}

	@Test
	void unhidingASourceBringsItBackTurnedOff() {
		SourceId source = registered(this.administration.registerPreset("multi"));
		this.administration.hideSource(source);

		this.administration.unhideSource(source);

		assertThat(this.administration.hiddenSources()).isEmpty();
		assertThat(this.administration.sources()).singleElement().satisfies(view -> {
			assertThat(view.id()).isEqualTo(source);
			assertThat(view.enabled()).isFalse();
		});
		assertThat(hiddenAt("sources", source.value())).isNull();
	}

	@Test
	void hiddenSourcesAreListedInTheOrderTheyWereHidden() {
		SourceId first = registered(this.administration.registerPreset("solo"));
		SourceId second = registered(this.administration.registerPreset("multi"));
		this.administration.hideSource(second);
		this.administration.hideSource(first);
		// 숨긴 시각을 직접 정해 같은 시각이 되는 경우를 없앤다 — 먼저 숨긴 second 가 앞이다
		this.jdbc.sql("update sources set hidden_at = '2026-11-01T00:00:00Z' where id = :id")
			.param("id", second.value())
			.update();
		this.jdbc.sql("update sources set hidden_at = '2026-11-02T00:00:00Z' where id = :id")
			.param("id", first.value())
			.update();

		assertThat(this.administration.hiddenSources()).extracting(SourceView::id).containsExactly(second, first);
	}

	@Test
	void aHiddenPresetIsNotRegisteredAgainAndThePresetListSaysItIsHidden() {
		SourceId hidden = registered(this.administration.registerPreset("multi"));
		this.administration.hideSource(hidden);

		assertThat(this.administration.registerPreset("multi"))
			.isEqualTo(new SourceRegistrationResult.PresetAlreadyRegistered("multi"));
		assertThat(this.administration.presets()).extracting(PresetView::key, PresetView::sourceId,
				PresetView::sourceHidden)
			.containsExactly(tuple("multi", hidden, true), tuple("solo", null, false));
	}

	@Test
	void operationsTreatAHiddenSourceAndItsFeedsAsUnknown() {
		SourceId source = registered(this.administration.registerOperatorProfile(OPERATOR_TEXT));
		FeedId feed = new FeedId(this.rows.feedId(source.value(), "main"));
		this.administration.hideSource(source);

		assertThatExceptionOfType(UnknownSourceException.class)
			.isThrownBy(() -> this.administration.enableSource(source));
		assertThatExceptionOfType(UnknownSourceException.class)
			.isThrownBy(() -> this.administration.disableSource(source));
		assertThatExceptionOfType(UnknownSourceException.class)
			.isThrownBy(() -> this.administration.changeSourcePollInterval(source, 30));
		assertThatExceptionOfType(UnknownSourceException.class)
			.isThrownBy(() -> this.administration.operatorProfileText(source));
		assertThatExceptionOfType(UnknownSourceException.class)
			.isThrownBy(() -> this.administration.reviseOperatorProfile(source, OPERATOR_TEXT));
		assertThatExceptionOfType(UnknownFeedException.class).isThrownBy(() -> this.administration.enableFeed(feed));
		assertThatExceptionOfType(UnknownFeedException.class).isThrownBy(() -> this.administration.disableFeed(feed));
		assertThatExceptionOfType(UnknownFeedException.class)
			.isThrownBy(() -> this.administration.changeFeedPollInterval(feed, 30));
		assertThatExceptionOfType(UnknownFeedException.class).isThrownBy(() -> this.administration.hideFeed(feed));
		assertThatExceptionOfType(UnknownFeedException.class).isThrownBy(() -> this.administration.unhideFeed(feed));
	}

	@Test
	void hidingAFeedMissingFromTheProfileMovesItToTheHiddenFeeds(CapturedOutput output) {
		SourceId source = registered(this.administration.registerPreset("multi"));
		long typo = this.rows.insertFeed(source.value(), "typo");
		this.rows.insertPollState(typo);
		this.jdbc.sql("update feeds set enabled = true where id = :id").param("id", typo).update();

		assertThat(this.administration.hideFeed(new FeedId(typo))).isEqualTo(new FeedRemovalResult.Removed());

		assertThat(this.administration.sources()).singleElement().satisfies(view -> {
			assertThat(view.feeds()).extracting(FeedView::feedKey).containsExactly("one", "two");
			assertThat(view.hiddenFeeds()).singleElement().satisfies(feed -> {
				assertThat(feed.feedKey()).isEqualTo("typo");
				assertThat(feed.enabled()).isFalse();
			});
		});
		assertThat(hiddenAt("feeds", typo)).isNotNull();
		assertThat(output).contains("운영자가 피드를 숨겼다 — 소스 " + source.value() + " 프리셋 multi 피드 " + typo);
		assertThatExceptionOfType(UnknownFeedException.class)
			.isThrownBy(() -> this.administration.enableFeed(new FeedId(typo)));

		this.administration.unhideFeed(new FeedId(typo));

		assertThat(this.administration.sources().getFirst().feeds()).extracting(FeedView::feedKey)
			.containsExactly("one", "two", "typo");
		assertThat(hiddenAt("feeds", typo)).isNull();
	}

	@Test
	void aFeedOfTheProfileOrOfASourceWithoutAProfileIsNotHidden() {
		SourceId source = registered(this.administration.registerPreset("multi"));
		FeedId one = new FeedId(this.rows.feedId(source.value(), "one"));
		long vanished = this.jdbc
			.sql("insert into sources (preset_key, enabled, version, created_at) values ('vanished', false, 0, now()) "
					+ "returning id")
			.query(Long.class)
			.single();
		long lost = this.rows.insertFeed(vanished, "lost");
		this.rows.insertPollState(lost);

		assertThat(this.administration.hideFeed(one))
			.isEqualTo(new FeedRemovalResult.Refused(FeedRemovalRefusal.IN_PROFILE));
		assertThat(this.administration.hideFeed(new FeedId(lost)))
			.isEqualTo(new FeedRemovalResult.Refused(FeedRemovalRefusal.PROFILE_UNAVAILABLE));
		assertThat(hiddenAt("feeds", one.value())).isNull();
		assertThat(hiddenAt("feeds", lost)).isNull();
	}

	@Test
	void aFeedReadBeforeTheSourceWasHiddenCannotBeSavedAfterIt() {
		SourceId source = registered(this.administration.registerPreset("multi"));
		Feed stale = this.feeds.findById(this.rows.feedId(source.value(), "one")).orElseThrow();

		this.administration.hideSource(source);

		assertThatExceptionOfType(OptimisticLockingFailureException.class).isThrownBy(() -> this.feeds.save(stale));
	}

	@Test
	void unknownIdsAreReportedAsUnknown() {
		assertThatExceptionOfType(UnknownSourceException.class)
			.isThrownBy(() -> this.administration.hideSource(new SourceId(999_999)));
		assertThatExceptionOfType(UnknownSourceException.class)
			.isThrownBy(() -> this.administration.unhideSource(new SourceId(999_999)));
		assertThatExceptionOfType(UnknownFeedException.class)
			.isThrownBy(() -> this.administration.hideFeed(new FeedId(999_999)));
		assertThatExceptionOfType(UnknownFeedException.class)
			.isThrownBy(() -> this.administration.unhideFeed(new FeedId(999_999)));
	}

	private @Nullable Object hiddenAt(String table, long id) {
		return this.jdbc.sql("select hidden_at from " + table + " where id = :id")
			.param("id", id)
			.query((rs, rowNum) -> Optional.ofNullable(rs.getObject("hidden_at")))
			.single()
			.orElse(null);
	}

	private static SourceId registered(SourceRegistrationResult result) {
		assertThat(result).isInstanceOf(SourceRegistrationResult.Registered.class);
		return ((SourceRegistrationResult.Registered) result).id();
	}

}
