package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.AccountDirectory;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.TopicSuggestionDecline;
import net.yozmnews.user.TopicSuggestionDeclineList;
import net.yozmnews.user.UserTables;
import net.yozmnews.user.domain.suggestion.RecordedDecline;
import net.yozmnews.user.domain.suggestion.TopicSuggestionDeclineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 구독 제안 거절 조회 — 기록 당시 버전의 키를 계보 표로 활성 중분류로 푼다: 그대로 · 이름 변경 · 합침(늦은 거절 하나) · 계보 없이 지움 · 같은 키
 * 문자열이 버전마다 다른 활성 키로 풀림, 계정 없음, 계보 표의 기준 버전 불변식. 옛 버전은 행만 넣고(tag_set_id 에 FK 없음) 계보 표는 시험이 정한
 * 대체물이다.
 * <p>
 * 지키는 규칙: user-R65
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class TopicSuggestionDeclinesServiceTests {

	private static final Instant EARLY = Instant.parse("2026-09-01T00:00:00Z");

	private static final Instant LATE = Instant.parse("2026-10-01T00:00:00Z");

	@Autowired
	AccountDirectory accounts;

	@Autowired
	TopicSuggestionDeclineRepository declines;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	TagKeyLineage lineage;

	@Autowired
	JdbcClient jdbc;

	private UserId user;

	private TagSetId active;

	private TagSetId old;

	@BeforeEach
	void prepare() {
		clean();
		this.user = new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values ('declines@example.com', '사용자', 'USER', 'ACTIVE', now(), 0) returning id
				""").query(Long.class).single());
		this.active = this.tagSets.activeTagSet().id();
		this.old = new TagSetId(this.active.value() + 1_000);
	}

	@AfterEach
	void clean() {
		UserTables.deleteAll(this.jdbc);
	}

	@Test
	void anActiveVersionDeclineStaysAsItIs() {
		this.declines.upsert(this.user, new RecordedDecline(this.active, "economy.markets", 0.4, EARLY));

		TopicSuggestionDeclineList list = service(this.lineage).declinesOf(this.user);

		assertThat(list.tagSet()).isEqualTo(this.active);
		assertThat(list.declines()).containsExactly(new TopicSuggestionDecline("economy.markets", 0.4, EARLY));
	}

	@Test
	void oldVersionKeysFollowTheLineage() {
		// 옛 버전: a → economy.markets(이름 변경), gone → 없음(지움)
		this.declines.upsert(this.user, new RecordedDecline(this.old, "economy.a", 0.4, EARLY));
		this.declines.upsert(this.user, new RecordedDecline(this.old, "economy.gone", 0.5, EARLY));

		TopicSuggestionDeclineList list = service(oldMaps(Map.of("economy.a", Set.of("economy.markets"),
				"economy.gone", Set.of()))).declinesOf(this.user);

		assertThat(list.declines()).containsExactly(new TopicSuggestionDecline("economy.markets", 0.4, EARLY));
	}

	@Test
	void rowsReachingTheSameSubtopicKeepTheLaterDecline() {
		// 옛 버전의 두 키가 economy.markets 로 합쳐졌고, 활성 버전에도 그 키의 거절이 있다
		this.declines.upsert(this.user, new RecordedDecline(this.old, "economy.a", 0.9, EARLY));
		this.declines.upsert(this.user, new RecordedDecline(this.old, "economy.b", 0.3, LATE));
		this.declines.upsert(this.user, new RecordedDecline(this.active, "economy.markets", 0.6, EARLY));

		TopicSuggestionDeclineList list = service(oldMaps(Map.of("economy.a", Set.of("economy.markets"),
				"economy.b", Set.of("economy.markets")))).declinesOf(this.user);

		assertThat(list.declines()).containsExactly(new TopicSuggestionDecline("economy.markets", 0.3, LATE));
	}

	@Test
	void theSameKeyStringInTwoVersionsCanReachTwoSubtopics() {
		// 옛 버전의 economy.markets 는 활성 버전의 economy.industry 로 이어졌고, 활성 버전의 economy.markets 는 다른 주제다
		this.declines.upsert(this.user, new RecordedDecline(this.old, "economy.markets", 0.4, EARLY));
		this.declines.upsert(this.user, new RecordedDecline(this.active, "economy.markets", 0.5, LATE));

		TopicSuggestionDeclineList list = service(oldMaps(Map.of("economy.markets", Set.of("economy.industry"))))
			.declinesOf(this.user);

		assertThat(list.declines()).containsExactlyInAnyOrder(
				new TopicSuggestionDecline("economy.industry", 0.4, EARLY),
				new TopicSuggestionDecline("economy.markets", 0.5, LATE));
	}

	@Test
	void noAccountHasNoDeclines() {
		TopicSuggestionDeclineList list = service(this.lineage).declinesOf(new UserId(Long.MAX_VALUE));

		assertThat(list.tagSet()).isEqualTo(this.active);
		assertThat(list.declines()).isEmpty();
	}

	@Test
	void aLineageTableAgainstAnotherActiveVersionBreaksTheSnapshotInvariant() {
		this.declines.upsert(this.user, new RecordedDecline(this.active, "economy.markets", 0.4, EARLY));
		TagSetId elsewhere = new TagSetId(this.active.value() + 2_000);
		TagKeyLineage skewed = from -> {
			TagKeyMapping real = this.lineage.toActive(from);
			return new TagKeyMapping(real.from(), elsewhere, real.keys());
		};

		assertThatIllegalStateException().isThrownBy(() -> service(skewed).declinesOf(this.user));
	}

	private TopicSuggestionDeclinesService service(TagKeyLineage lineage) {
		return new TopicSuggestionDeclinesService(this.accounts, this.declines, this.tagSets, lineage);
	}

	/** 옛 버전은 주어진 주제 표, 활성 버전은 실제 표 */
	private TagKeyLineage oldMaps(Map<String, Set<String>> topics) {
		return from -> {
			if (from.equals(this.old)) {
				return new TagKeyMapping(this.old, this.active, Map.of(TagKind.TOPIC, topics));
			}
			return this.lineage.toActive(from);
		};
	}

}
