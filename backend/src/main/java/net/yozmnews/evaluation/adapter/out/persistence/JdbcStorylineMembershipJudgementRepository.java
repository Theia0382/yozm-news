package net.yozmnews.evaluation.adapter.out.persistence;

import java.util.Collection;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.StorylineMembershipJudgementId;
import net.yozmnews.evaluation.domain.membership.MembershipRecord;
import net.yozmnews.evaluation.domain.membership.StorylineMembershipJudgementRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link StorylineMembershipJudgementRepository} 의 JdbcClient 구현 — docs/specs/evaluation.md §4.
 * <p>
 * 판정 원본은 넣기 한 번이고 갱신하지 않는다 — aggregate 가 없다. 넣기 · 지우기는 사건 소속 표와 함께 쓰는
 * {@link MembershipJudgementRows} 이고, 물은 후보의 ID 키는 {@code candidateId} 다.
 */
@Repository
class JdbcStorylineMembershipJudgementRepository implements StorylineMembershipJudgementRepository {

	private static final String TABLE = "storyline_membership_judgements";

	private final JdbcClient jdbc;

	JdbcStorylineMembershipJudgementRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public StorylineMembershipJudgementId insert(MembershipRecord record) {
		return new StorylineMembershipJudgementId(MembershipJudgementRows.insert(this.jdbc, TABLE, "candidateId", record));
	}

	@Override
	public void deleteByArticles(Collection<ArticleId> articleIds) {
		MembershipJudgementRows.deleteByArticles(this.jdbc, TABLE, articleIds);
	}

}
