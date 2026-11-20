package net.yozmnews.digest.adapter.out.persistence;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.SectionKind;
import net.yozmnews.digest.domain.removal.DigestContentCleanup;
import net.yozmnews.digest.domain.removal.SectionCitations;
import net.yozmnews.event.EventId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link DigestContentCleanup} 의 JdbcClient 구현 — docs/specs/digest.md §4
 */
@Repository
class JdbcDigestContentCleanup implements DigestContentCleanup {

	private final JdbcClient jdbc;

	JdbcDigestContentCleanup(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Long> lockDigestsReferencing(Collection<ArticleId> removed) {
		if (removed.isEmpty()) {
			return List.of();
		}
		return this.jdbc.sql("""
				select id from digests
				where id in (
				    select digest_id from digest_candidates where article_id = any(:ids)
				    union
				    select s.digest_id from digest_sections s join digest_items i on i.digest_section_id = s.id
				    where i.article_id = any(:ids))
				order by id
				for update
				""").param("ids", ids(removed)).query(Long.class).list();
	}

	@Override
	public List<SectionCitations> sectionsOfDigestsCiting(Collection<ArticleId> removed) {
		if (removed.isEmpty()) {
			return List.of();
		}
		Map<Long, Section> sections = new LinkedHashMap<>();
		this.jdbc.sql("""
				select s.id, s.digest_id, s.kind, i.article_id
				from digest_sections s left join digest_items i on i.digest_section_id = s.id
				where s.digest_id in (
				    select cited.digest_id from digest_sections cited
				    join digest_items item on item.digest_section_id = cited.id
				    where item.article_id = any(:ids))
				order by s.digest_id, s.position, i.position
				""").param("ids", ids(removed)).query((ResultSet rs) -> {
					long id = rs.getLong("id");
					Section section = sections.get(id);
					if (section == null) {
						section = new Section(id, rs.getLong("digest_id"), SectionKind.valueOf(rs.getString("kind")));
						sections.put(id, section);
					}
					long article = rs.getLong("article_id");
					if (!rs.wasNull()) {
						section.cited().add(new ArticleId(article));
					}
				});
		List<SectionCitations> result = new ArrayList<>();
		for (Section section : sections.values()) {
			result.add(new SectionCitations(section.id(), section.digestId(), section.kind(), section.cited()));
		}
		return result;
	}

	@Override
	public Map<Long, List<EventId>> itemKeysOf(Collection<Long> sectionIds) {
		if (sectionIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<EventId>> keys = new LinkedHashMap<>();
		this.jdbc.sql("""
				select s.digest_id, e.event_id
				from digest_sections s join digest_section_events e on e.digest_section_id = s.id
				where s.id = any(:ids) and s.kind in ('TOP_STORY', 'HEADLINE') and e.position = 0
				order by s.digest_id, s.position
				""").param("ids", sectionIds.toArray(Long[]::new)).query((ResultSet rs) -> {
					keys.computeIfAbsent(rs.getLong("digest_id"), digest -> new ArrayList<>())
						.add(new EventId(rs.getLong("event_id")));
				});
		return keys;
	}

	@Override
	public void deleteItems(Collection<ArticleId> removed) {
		if (removed.isEmpty()) {
			return;
		}
		this.jdbc.sql("delete from digest_items where article_id = any(:ids)").param("ids", ids(removed)).update();
	}

	@Override
	public void deleteSections(Collection<Long> sectionIds) {
		if (sectionIds.isEmpty()) {
			return;
		}
		this.jdbc.sql("delete from digest_sections where id = any(:ids)")
			.param("ids", sectionIds.toArray(Long[]::new))
			.update();
	}

	private static Long[] ids(Collection<ArticleId> articles) {
		return articles.stream().map(ArticleId::value).toArray(Long[]::new);
	}

	/** 읽는 중인 섹션 하나 */
	private record Section(long id, long digestId, SectionKind kind, Set<ArticleId> cited) {

		Section(long id, long digestId, SectionKind kind) {
			this(id, digestId, kind, new LinkedHashSet<>());
		}

	}

}
