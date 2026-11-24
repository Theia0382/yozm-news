package net.yozmnews.user;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineRows;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 도우미 — 사용자 스토리라인 시험의 행(사용자 · 스토리라인 · 관계 · 사건과 소속 · 반응 이력)과 저장 상태 읽기. 스토리라인과 관계는 SQL 로
 * 직접 넣는다 — 이벤트가 나가지 않는다.
 */
public final class UserStorylineFixtures {

	private UserStorylineFixtures() {
	}

	/** 사용자 · 스토리라인 · 사건 · 반응 이력을 비운다 */
	public static void clear(JdbcClient jdbc) {
		UserTables.deleteAll(jdbc);
		jdbc.sql("delete from feedback_events").update();
		StorylineRows.clear(jdbc);
	}

	public static UserId user(JdbcClient jdbc, String email) {
		return new UserId(jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, :email, 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single());
	}

	/** 스토리라인 하나 — 첫 · 마지막 기사 시각이 {@code at} */
	public static long storyline(JdbcClient jdbc, Instant at) {
		return jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				                        representative_event_id, created_at, updated_at)
				values ('model-a', cast('[1,0]' as halfvec), 1, :at, :at, 1, :at, :at)
				returning id
				""").param("at", utc(at)).query(Long.class).single();
	}

	public static void merge(JdbcClient jdbc, long merged, long survivor) {
		jdbc.sql("update storylines set merged_into = :into, merged_at = now() where id = :id")
			.param("into", survivor)
			.param("id", merged)
			.update();
	}

	/** 이어짐 — earlier 가 later 를 낳았다 */
	public static void follows(JdbcClient jdbc, long earlier, long later) {
		relate(jdbc, "FOLLOWS", earlier, later);
	}

	/** 포함 — parent ⊃ child */
	public static void contains(JdbcClient jdbc, long parent, long child) {
		relate(jdbc, "CONTAINS", parent, child);
	}

	public static void unrelate(JdbcClient jdbc, long a, long b) {
		jdbc.sql("""
				delete from storyline_relations
				where (source_id = :a and target_id = :b) or (source_id = :b and target_id = :a)
				""").param("a", a).param("b", b).update();
	}

	private static void relate(JdbcClient jdbc, String kind, long source, long target) {
		List<Long> criteria = jdbc.sql("select id from storyline_relation_criteria order by id limit 1")
			.query(Long.class)
			.list();
		long criteriaId = criteria.isEmpty() ? jdbc.sql("""
				insert into storyline_relation_criteria (probability, one_matter, min_events, candidate_score,
				                                         window_seconds, merge_probability, created_at)
				values (0.61, 0.21, 3, 0.87, 86400, 1.0, now()) returning id
				""").query(Long.class).single() : criteria.getFirst();
		jdbc.sql("""
				insert into storyline_relations (kind, source_id, target_id, probability, judgement_id, criteria_id, linked_at)
				values (:kind, :source, :target, 0.7, 1, :criteria, now())
				""").param("kind", kind).param("source", source).param("target", target).param("criteria", criteriaId).update();
	}

	/** 그 스토리라인에 직접 든 사건 하나 — 사건 ID 를 낸다 */
	public static long eventIn(JdbcClient jdbc, long storyline) {
		long event = StorylineRows.event(jdbc, "OPEN", "model-a", "[1,0]", 1, "2026-11-01T00:00:00Z",
				"2026-11-01T00:00:00Z", Map.of());
		jdbc.sql("""
				insert into storyline_members (storyline_id, event_id, linked_by, linked_at)
				values (:storyline, :event, 'MERGE', now())
				""").param("storyline", storyline).param("event", event).update();
		return event;
	}

	/** 반응 이력 한 행 — 값은 LIKE · DISLIKE · NONE */
	public static void reaction(JdbcClient jdbc, UserId user, long event, String value, Instant at) {
		jdbc.sql("""
				insert into feedback_events (user_id, event_id, value, digest_id, occurred_at)
				values (:user, :event, :value, 1, :at)
				""").param("user", user.value()).param("event", event).param("value", value).param("at", utc(at)).update();
	}

	/** 추적 중인 사용자 스토리라인 하나를 행으로 직접 둔다 — 추적 대상과 추적 씨앗이 그 스토리라인 하나, 이력은 더함 · 사용자 */
	public static long tracked(JdbcClient jdbc, UserId user, long storyline, Instant at) {
		long id = jdbc.sql("insert into user_storylines (user_id, created_at) values (:user, :at) returning id")
			.param("user", user.value())
			.param("at", utc(at))
			.query(Long.class)
			.single();
		jdbc.sql("insert into user_storyline_members (user_storyline_id, storyline_id) values (:id, :storyline)")
			.param("id", id)
			.param("storyline", storyline)
			.update();
		jdbc.sql("insert into user_storyline_tracked_seeds (user_storyline_id, storyline_id) values (:id, :storyline)")
			.param("id", id)
			.param("storyline", storyline)
			.update();
		jdbc.sql("""
				insert into user_storyline_subscriptions (user_id, user_storyline_id, created_at)
				values (:user, :id, :at)
				""").param("user", user.value()).param("id", id).param("at", utc(at)).update();
		jdbc.sql("""
				insert into user_storyline_subscription_events (user_id, storyline_id, action, cause, occurred_at)
				values (:user, :storyline, 'ADD', 'USER', :at)
				""").param("user", user.value()).param("storyline", storyline).param("at", utc(at)).update();
		return id;
	}

	/** 이력 행 — [스토리라인 ID, 동작, 원인], 행 순 */
	public static List<List<Object>> history(JdbcClient jdbc, UserId user) {
		List<List<Object>> rows = new ArrayList<>();
		jdbc.sql("""
				select storyline_id, action, cause from user_storyline_subscription_events
				where user_id = :user order by id
				""").param("user", user.value()).query((rs, n) -> {
			rows.add(List.of(rs.getLong("storyline_id"), rs.getString("action"), rs.getString("cause")));
			return n;
		}).list();
		return rows;
	}

	/** 저장된 사용자 스토리라인 — ID 순 */
	public static List<Saved> saved(JdbcClient jdbc, UserId user) {
		return jdbc.sql("""
				select u.id, s.created_at as subscribed_at,
				       (select array_agg(m.storyline_id order by m.storyline_id) from user_storyline_members m
				         where m.user_storyline_id = u.id) as members,
				       (select array_agg(t.storyline_id order by t.storyline_id) from user_storyline_tracked_seeds t
				         where t.user_storyline_id = u.id) as seeds
				from user_storylines u
				left join user_storyline_subscriptions s on s.user_storyline_id = u.id
				where u.user_id = :user
				order by u.id
				""").param("user", user.value()).query((rs, n) -> {
			OffsetDateTime subscribedAt = rs.getObject("subscribed_at", OffsetDateTime.class);
			return new Saved(rs.getLong("id"), (subscribedAt == null) ? null : subscribedAt.toInstant(),
					ids(rs.getArray("members")), ids(rs.getArray("seeds")));
		}).list();
	}

	private static Set<Long> ids(java.sql.Array array) throws java.sql.SQLException {
		Set<Long> ids = new TreeSet<>();
		if (array != null) {
			for (Object value : (Object[]) array.getArray()) {
				ids.add(((Number) value).longValue());
			}
		}
		return ids;
	}

	private static OffsetDateTime utc(Instant value) {
		return value.atOffset(ZoneOffset.UTC);
	}

	/**
	 * 저장된 사용자 스토리라인 하나.
	 *
	 * @param subscribedAt 추적 시각 — 추적 중이 아니면 null
	 * @param members 추적 대상으로 저장한 스토리라인 ID
	 * @param seeds 추적 씨앗으로 저장한 스토리라인 ID
	 */
	public record Saved(long id, Instant subscribedAt, Set<Long> members, Set<Long> seeds) {

		public boolean tracked() {
			return this.subscribedAt != null;
		}

	}

}
