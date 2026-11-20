package net.yozmnews.digest.adapter.out.persistence;

import java.util.List;

import net.yozmnews.digest.domain.content.ItemRepository;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link ItemRepository} 의 JdbcClient 구현 — docs/specs/digest.md §4 · digest-R14. 섹션 사건 · 섹션 · 다이제스트를 잇는 질의 하나다 —
 * 그 사건이 든 항목 섹션이 정확히 하나일 때만 그 섹션의 사건 행을 낸다.
 */
@Repository
class JdbcItemRepository implements ItemRepository {

	private final JdbcClient jdbc;

	JdbcItemRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<EventId> itemOf(UserId user, DigestId digest, EventId event) {
		return this.jdbc.sql("""
				with holding as (
				    select distinct e.digest_section_id
				    from digest_section_events e
				    join digest_sections s on s.id = e.digest_section_id
				    join digests d on d.id = s.digest_id
				    where d.id = :digest and d.user_id = :user and e.event_id = :event
				      and s.kind in ('TOP_STORY', 'HEADLINE'))
				select e.event_id
				from digest_section_events e
				where e.digest_section_id in (select digest_section_id from holding)
				  and (select count(*) from holding) = 1
				order by e.position
				""")
			.param("digest", digest.value())
			.param("user", user.value())
			.param("event", event.value())
			.query((rs, row) -> new EventId(rs.getLong("event_id")))
			.list();
	}

}
