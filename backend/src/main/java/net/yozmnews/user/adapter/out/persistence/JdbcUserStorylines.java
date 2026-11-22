package net.yozmnews.user.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.userstoryline.UserStorylineRepository;
import net.yozmnews.user.domain.userstoryline.UserStorylineRow;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link UserStorylineRepository} 의 JdbcClient 구현. docs/specs/user.md §4 · §6
 */
@Repository
class JdbcUserStorylines implements UserStorylineRepository {

	private final JdbcClient jdbc;

	JdbcUserStorylines(JdbcClient jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	@Override
	public List<UserStorylineRow> load(UserId user) {
		Map<Long, Set<Long>> members = storylines("user_storyline_members", user);
		Map<Long, Set<Long>> seeds = storylines("user_storyline_tracked_seeds", user);
		return this.jdbc.sql("""
				select u.id, u.created_at, s.created_at as subscribed_at
				from user_storylines u
				left join user_storyline_subscriptions s on s.user_storyline_id = u.id
				where u.user_id = :user
				order by u.id
				""").param("user", user.value()).query((rs, n) -> {
			long id = rs.getLong("id");
			OffsetDateTime subscribedAt = rs.getObject("subscribed_at", OffsetDateTime.class);
			return new UserStorylineRow(id, rs.getObject("created_at", OffsetDateTime.class).toInstant(),
					(subscribedAt == null) ? null : subscribedAt.toInstant(), members.getOrDefault(id, Set.of()),
					seeds.getOrDefault(id, Set.of()));
		}).list();
	}

	/** 사용자 스토리라인 ID → 그 표의 스토리라인 ID. 표 이름은 이 클래스의 상수뿐이다 */
	private Map<Long, Set<Long>> storylines(String table, UserId user) {
		Map<Long, Set<Long>> found = new HashMap<>();
		this.jdbc.sql("""
				select t.user_storyline_id, t.storyline_id
				from %s t
				join user_storylines u on u.id = t.user_storyline_id
				where u.user_id = :user
				""".formatted(table))
			.param("user", user.value())
			.query((rs, n) -> Map.entry(rs.getLong("user_storyline_id"), rs.getLong("storyline_id")))
			.list()
			.forEach(row -> found.computeIfAbsent(row.getKey(), ignored -> new HashSet<>()).add(row.getValue()));
		return found;
	}

	@Override
	public long create(UserId user, Instant now) {
		return this.jdbc.sql("insert into user_storylines (user_id, created_at) values (:user, :at) returning id")
			.param("user", user.value())
			.param("at", utc(now))
			.query(Long.class)
			.single();
	}

	@Override
	public void delete(long id) {
		this.jdbc.sql("delete from user_storylines where id = :id").param("id", id).update();
	}

	@Override
	public void replaceMembers(long id, Set<Long> storylineIds) {
		replace("user_storyline_members", id, storylineIds);
	}

	@Override
	public void replaceSeeds(long id, Set<Long> storylineIds) {
		replace("user_storyline_tracked_seeds", id, storylineIds);
	}

	private void replace(String table, long id, Set<Long> storylineIds) {
		this.jdbc.sql("delete from %s where user_storyline_id = :id".formatted(table)).param("id", id).update();
		for (long storyline : storylineIds) {
			this.jdbc.sql("insert into %s (user_storyline_id, storyline_id) values (:id, :storyline)".formatted(table))
				.param("id", id)
				.param("storyline", storyline)
				.update();
		}
	}

	@Override
	public void subscribe(UserId user, long id, Instant at) {
		this.jdbc.sql("""
				insert into user_storyline_subscriptions (user_id, user_storyline_id, created_at)
				values (:user, :id, :at)
				on conflict (user_storyline_id) do update set created_at = excluded.created_at
				""").param("user", user.value()).param("id", id).param("at", utc(at)).update();
	}

	@Override
	public void unsubscribe(long id) {
		this.jdbc.sql("delete from user_storyline_subscriptions where user_storyline_id = :id")
			.param("id", id)
			.update();
	}

	@Override
	public List<UserId> usersHolding(Collection<Long> storylineIds, long afterUserId, int limit) {
		if (storylineIds.isEmpty()) {
			return List.of();
		}
		List<UserId> users = new ArrayList<>();
		this.jdbc.sql("""
				select distinct u.user_id
				from user_storylines u
				where u.user_id > :after
				  and (exists (select 1 from user_storyline_members m
				               where m.user_storyline_id = u.id and m.storyline_id = any(:storylines))
				    or exists (select 1 from user_storyline_tracked_seeds t
				               where t.user_storyline_id = u.id and t.storyline_id = any(:storylines)))
				order by u.user_id
				limit :limit
				""")
			.param("storylines", storylineIds.toArray(Long[]::new))
			.param("after", afterUserId)
			.param("limit", limit)
			.query((rs, n) -> new UserId(rs.getLong("user_id")))
			.list()
			.forEach(users::add);
		return users;
	}

	private static OffsetDateTime utc(Instant value) {
		return value.atOffset(ZoneOffset.UTC);
	}

}
