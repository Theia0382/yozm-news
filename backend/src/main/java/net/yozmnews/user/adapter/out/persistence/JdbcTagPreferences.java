package net.yozmnews.user.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.domain.preference.PreferenceCause;
import net.yozmnews.user.domain.preference.PreferenceChange;
import net.yozmnews.user.domain.preference.PreferenceMode;
import net.yozmnews.user.domain.preference.PreferenceSet;
import net.yozmnews.user.domain.preference.TagPreferenceRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link TagPreferenceRepository} 의 PostgreSQL 구현 — 현재 상태 {@code user_tag_preferences} 와 이력
 * {@code user_tag_preference_events}. docs/specs/user.md §4 · §6
 */
@Repository
class JdbcTagPreferences implements TagPreferenceRepository {

	private final JdbcClient jdbc;

	JdbcTagPreferences(JdbcClient jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	@Override
	public PreferenceSet load(UserId user) {
		Set<String> topics = new HashSet<>();
		Set<String> regions = new HashSet<>();
		Set<String> formats = new HashSet<>();
		this.jdbc.sql("select tag_kind, tag_key from user_tag_preferences where user_id = :user")
			.param("user", user.value())
			.query((rs, n) -> {
				String key = rs.getString("tag_key");
				switch (TagKind.valueOf(rs.getString("tag_kind"))) {
					case TOPIC -> topics.add(key);
					case REGION -> regions.add(key);
					case FORMAT -> formats.add(key);
				}
				return key;
			})
			.list();
		return new PreferenceSet(topics, regions, formats);
	}

	@Override
	public void apply(UserId user, List<PreferenceChange> changes, PreferenceCause cause, TagSetId version,
			Instant now) {
		write(user, changes, now);
		record(user, changes, cause, version, now);
	}

	@Override
	public void replace(UserId user, PreferenceSet next, Instant now) {
		write(user, load(user).changesTo(next), now);
	}

	/** 차이를 현재 상태 행으로 옮긴다 — 더함은 행 넣기, 해제는 행 지우기. 이력은 쓰지 않는다 */
	private void write(UserId user, List<PreferenceChange> changes, Instant now) {
		for (PreferenceChange change : changes) {
			switch (change.action()) {
				case ADD -> insertRow(user, change.kind(), change.key(), now);
				case REMOVE -> deleteRow(user, change.kind(), change.key());
			}
		}
	}

	@Override
	public void record(UserId user, List<PreferenceChange> changes, PreferenceCause cause, TagSetId version,
			Instant now) {
		Timestamp at = Timestamp.from(now);
		for (PreferenceChange change : changes) {
			this.jdbc.sql("""
					insert into user_tag_preference_events (user_id, cause, tag_set_id, tag_kind, tag_key, mode, action,
					  occurred_at)
					values (:user, :cause, :version, :kind, :key, :mode, :action, :at)
					""")
				.param("user", user.value())
				.param("cause", cause.name())
				.param("version", version.value())
				.param("kind", change.kind().name())
				.param("key", change.key())
				.param("mode", change.mode().name())
				.param("action", change.action().name())
				.param("at", at)
				.update();
		}
	}

	private void insertRow(UserId user, TagKind kind, String key, Instant now) {
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, :kind, :key, :mode, :at)
				""")
			.param("user", user.value())
			.param("kind", kind.name())
			.param("key", key)
			.param("mode", PreferenceMode.of(kind).name())
			.param("at", Timestamp.from(now))
			.update();
	}

	private void deleteRow(UserId user, TagKind kind, String key) {
		this.jdbc.sql("delete from user_tag_preferences where user_id = :user and tag_kind = :kind and tag_key = :key")
			.param("user", user.value())
			.param("kind", kind.name())
			.param("key", key)
			.update();
	}

}
