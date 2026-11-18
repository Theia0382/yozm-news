package net.yozmnews.digest.adapter.out.persistence;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.receipt.ReceiptRepository;
import net.yozmnews.platform.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link ReceiptRepository} 의 JdbcClient 구현 — docs/specs/digest.md §4 · digest-R13. 인용 · 섹션 · 다이제스트를 잇는 질의 하나다.
 */
@Repository
class JdbcReceiptRepository implements ReceiptRepository {

	private final JdbcClient jdbc;

	JdbcReceiptRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<ArticleId, LocalDate> firstShownDates(UserId user, Collection<ArticleId> articles) {
		if (articles.isEmpty()) {
			return Map.of();
		}
		Map<ArticleId, LocalDate> dates = new LinkedHashMap<>();
		this.jdbc.sql("""
				select i.article_id, min(d.digest_date) as received_on
				from digest_items i
				join digest_sections s on s.id = i.digest_section_id
				join digests d on d.id = s.digest_id
				where d.user_id = :user and d.status = 'READY' and i.article_id = any(:articles)
				  and d.id = %s
				group by i.article_id
				order by i.article_id
				""".formatted(ShownDigestSql.shownIdOf("d")))
			.param("user", user.value())
			.param("articles", articles.stream().map(ArticleId::value).distinct().toArray(Long[]::new))
			.query((ResultSet rs) -> {
				dates.put(new ArticleId(rs.getLong("article_id")), rs.getObject("received_on", LocalDate.class));
			});
		return Collections.unmodifiableMap(dates);
	}

}
