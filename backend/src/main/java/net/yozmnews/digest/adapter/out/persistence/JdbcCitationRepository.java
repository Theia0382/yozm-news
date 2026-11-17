package net.yozmnews.digest.adapter.out.persistence;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.content.CitationRepository;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.platform.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link CitationRepository} 의 JdbcClient 구현 — docs/specs/digest.md §4 · digest-R14. 인용 · 섹션 · 다이제스트를 잇는 질의 하나다.
 */
@Repository
class JdbcCitationRepository implements CitationRepository {

	private final JdbcClient jdbc;

	JdbcCitationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean cited(UserId user, DigestId digest, ArticleId article) {
		return this.jdbc.sql("""
				select exists (
				    select 1
				    from digest_items i
				    join digest_sections s on s.id = i.digest_section_id
				    join digests d on d.id = s.digest_id
				    where d.id = :digest and d.user_id = :user and i.article_id = :article)
				""")
			.param("digest", digest.value())
			.param("user", user.value())
			.param("article", article.value())
			.query(Boolean.class)
			.single();
	}

}
