package net.yozmnews.evaluation.adapter.out.persistence;

import java.util.Collection;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.FlowMembershipJudgementId;
import net.yozmnews.evaluation.domain.membership.FlowMembershipJudgementRepository;
import net.yozmnews.evaluation.domain.membership.MembershipRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link FlowMembershipJudgementRepository} 의 JdbcClient 구현 — docs/specs/evaluation.md §4.
 * <p>
 * 판정 원본은 넣기 한 번이고 갱신하지 않는다 — aggregate 가 없다. 넣기 · 지우기는 사건 소속 표와 함께 쓰는
 * {@link MembershipJudgementRows} 이고, 물은 후보의 ID 키는 {@code flowId} 다.
 */
@Repository
class JdbcFlowMembershipJudgementRepository implements FlowMembershipJudgementRepository {

	private static final String TABLE = "flow_membership_judgements";

	private final JdbcClient jdbc;

	JdbcFlowMembershipJudgementRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public FlowMembershipJudgementId insert(MembershipRecord record) {
		return new FlowMembershipJudgementId(MembershipJudgementRows.insert(this.jdbc, TABLE, "flowId", record));
	}

	@Override
	public void deleteByArticles(Collection<ArticleId> articleIds) {
		MembershipJudgementRows.deleteByArticles(this.jdbc, TABLE, articleIds);
	}

}
