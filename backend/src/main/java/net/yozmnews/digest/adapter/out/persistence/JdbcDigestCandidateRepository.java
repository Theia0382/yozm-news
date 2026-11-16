package net.yozmnews.digest.adapter.out.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.AliasedCandidate;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.candidate.DigestCandidateRepository;
import net.yozmnews.digest.domain.digest.DigestId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link DigestCandidateRepository} 의 JdbcClient 구현 — docs/specs/digest.md §4
 * <p>
 * 후보 행은 넣고 지우기만 하고 고치지 않는다 — aggregate 가 없다. 후보 행을 쓰거나 지우는 쪽은 모두 먼저 그 다이제스트 행을 잠근다(생성 ·
 * 지운 기사 따르기 · 보존 정리, 여러 다이제스트면 ID 순) — 잠금이 다이제스트 행 → 후보 행 한 순서라 서로 교착하지 않는다.
 */
@Repository
class JdbcDigestCandidateRepository implements DigestCandidateRepository {

	private final JdbcClient jdbc;

	JdbcDigestCandidateRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void replace(DigestId digest, CandidateSet candidates) {
		Objects.requireNonNull(digest, "digest");
		Objects.requireNonNull(candidates, "candidates");
		this.jdbc.sql("delete from digest_candidates where digest_id = :digest").param("digest", digest.value()).update();
		for (AliasedCandidate entry : candidates.entries()) {
			this.jdbc.sql("""
					insert into digest_candidates (digest_id, alias, article_id) values (:digest, :alias, :article)
					""")
				.param("digest", digest.value())
				.param("alias", entry.alias())
				.param("article", entry.candidate().articleId().value())
				.update();
		}
	}

	@Override
	public Set<ArticleId> lockArticles(DigestId digest) {
		Objects.requireNonNull(digest, "digest");
		Set<ArticleId> articles = new LinkedHashSet<>();
		this.jdbc.sql("select article_id from digest_candidates where digest_id = :digest order by alias for share")
			.param("digest", digest.value())
			.query(Long.class)
			.list()
			.forEach(id -> articles.add(new ArticleId(id)));
		return articles;
	}

	@Override
	public void delete(DigestId digest, Collection<ArticleId> articles) {
		Objects.requireNonNull(digest, "digest");
		if (articles.isEmpty()) {
			return;
		}
		Long[] ids = articles.stream().map(ArticleId::value).toArray(Long[]::new);
		this.jdbc.sql("delete from digest_candidates where digest_id = :digest and article_id = any(:ids)")
			.param("digest", digest.value())
			.param("ids", ids)
			.update();
	}

	@Override
	public void deleteByArticles(Collection<ArticleId> articles) {
		if (articles.isEmpty()) {
			return;
		}
		Long[] ids = articles.stream().map(ArticleId::value).toArray(Long[]::new);
		this.jdbc.sql("delete from digest_candidates where article_id = any(:ids)").param("ids", ids).update();
	}

	@Override
	public int deleteOfDigestsCreatedBefore(Instant cutoff) {
		Objects.requireNonNull(cutoff, "cutoff");
		Long[] digests = this.jdbc.sql("""
				select d.id from digests d
				where d.created_at < :cutoff and exists (select 1 from digest_candidates c where c.digest_id = d.id)
				order by d.id
				for update
				""").param("cutoff", cutoff.atOffset(ZoneOffset.UTC)).query(Long.class).list().toArray(Long[]::new);
		if (digests.length == 0) {
			return 0;
		}
		return this.jdbc.sql("delete from digest_candidates where digest_id = any(:digests)")
			.param("digests", digests)
			.update();
	}

}
