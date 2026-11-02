package net.yozmnews.source.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.FeedLabels;
import net.yozmnews.source.SourceAdministration;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.SourceRegistrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 공개 조회 — 피드의 출처 이름 · 섹션을 실제 PostgreSQL 과 합성 프리셋 multi(멀티일보 — 피드 one 하나 · two 둘)로 본다. 운영 목록과
 * 같은 해석이다: 이름은 프로필 → 기록된 출처 이름, 섹션은 프로필에 그 피드가 있을 때만.
 * <p>
 * 지키는 규칙: source-R57
 */
@Import({ TestcontainersConfiguration.class, RegistryTestCatalog.class })
@SpringBootTest
class FeedLabelsTests {

	@Autowired
	FeedLabels labels;

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
	void aPresetSourceGivesTheProfileNameAndEachFeedsSectionEvenWhenHidden() {
		long multi = registered(this.administration.registerPreset("multi")).value();
		FeedId one = new FeedId(this.rows.feedId(multi, "one"));
		FeedId two = new FeedId(this.rows.feedId(multi, "two"));
		this.rows.hide("feeds", two.value());
		this.rows.hide("sources", multi);
		// 기록된 출처 이름을 프로필과 다르게 둔다 — 프로필을 얻으면 기록이 아니라 프로필의 이름이다
		this.jdbc.sql("update source_attributions set name = '예전멀티일보' where source_id = :source")
			.param("source", multi)
			.update();

		assertThat(this.labels.labels(List.of(one, two)))
			.isEqualTo(Map.of(one, new FeedLabel("멀티일보", "하나"), two, new FeedLabel("멀티일보", "둘")));
	}

	@Test
	void aSourceThatLostItsPresetGivesTheRecordedNameAndNoSection() {
		long vanished = this.jdbc
			.sql("insert into sources (preset_key, enabled, version, created_at) values ('vanished', true, 0, now()) "
					+ "returning id")
			.query(Long.class)
			.single();
		FeedId lost = new FeedId(this.rows.insertFeed(vanished, "lost"));
		this.rows.insertPollState(lost.value());
		this.jdbc.sql("insert into source_attributions (source_id, name, recorded_at) values (:source, '사라진일보', now())")
			.param("source", vanished)
			.update();

		assertThat(this.labels.labels(List.of(lost))).isEqualTo(Map.of(lost, new FeedLabel("사라진일보", null)));
	}

	@Test
	void aSourceWithoutAProfileOrARecordGivesNeither() {
		long invalid = this.jdbc
			.sql("insert into sources (profile, enabled, version, created_at) values ('source:\n  name: 이름만\n', false, 0, "
					+ "now()) returning id")
			.query(Long.class)
			.single();
		FeedId feed = new FeedId(this.rows.insertFeed(invalid, "any"));
		this.rows.insertPollState(feed.value());

		assertThat(this.labels.labels(List.of(feed))).isEqualTo(Map.of(feed, new FeedLabel(null, null)));
	}

	@Test
	void aSourceWhoseProfileFailsValidationGivesTheRecordedNameAndNoSection() {
		long invalid = this.jdbc
			.sql("insert into sources (profile, enabled, version, created_at) values ('source:\n  name: 이름만\n', false, 0, "
					+ "now()) returning id")
			.query(Long.class)
			.single();
		FeedId feed = new FeedId(this.rows.insertFeed(invalid, "any"));
		this.rows.insertPollState(feed.value());
		this.jdbc.sql("insert into source_attributions (source_id, name, recorded_at) values (:source, '원문일보', now())")
			.param("source", invalid)
			.update();

		assertThat(this.labels.labels(List.of(feed))).isEqualTo(Map.of(feed, new FeedLabel("원문일보", null)));
	}

	@Test
	void aDeletedFeedIsLeftOutAndAnEmptyRequestGivesNothing() {
		long multi = registered(this.administration.registerPreset("multi")).value();
		FeedId one = new FeedId(this.rows.feedId(multi, "one"));
		FeedId deleted = new FeedId(one.value() + 1_000);

		assertThat(this.labels.labels(List.of(one, deleted))).containsOnlyKeys(one);
		assertThat(this.labels.labels(List.of())).isEmpty();
	}

	private static SourceId registered(SourceRegistrationResult result) {
		assertThat(result).isInstanceOf(SourceRegistrationResult.Registered.class);
		return ((SourceRegistrationResult.Registered) result).id();
	}

}
