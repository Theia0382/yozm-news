package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventDescriptor;
import net.yozmnews.evaluation.MembershipJudgement;
import net.yozmnews.evaluation.MembershipQuery;
import net.yozmnews.evaluation.adapter.out.fake.FakeArticleEvaluator;
import net.yozmnews.evaluation.domain.membership.EventJudgementRepository;
import net.yozmnews.evaluation.domain.membership.MembershipRecord;
import net.yozmnews.source.SourceId;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사건 소속 판정 공개 API — 평가기가 없는 프로세스에서 부르면 기사를 읽기 전에 예외로 드러난다.
 * <p>
 * 지키는 규칙: evaluation-R37
 */
class EventMembershipServiceTests {

	private static final MembershipQuery QUERY = new MembershipQuery(new ArticleId(1),
			List.of(new EventDescriptor(12, List.of(new ArticleId(3)))));

	private final Reads articles = new Reads();

	@Test
	void failsBeforeReadingAnythingWithoutAnEvaluator() {
		EventMembershipService service = new EventMembershipService(() -> null, this.articles, new NoJudgements(),
				new TransactionTemplate(), () -> Instant.EPOCH);

		assertThatIllegalStateException().isThrownBy(() -> service.judge(QUERY))
			.withMessage("평가기가 없는 프로세스에서 사건 소속 판정을 불렀다 — 판정은 작업 역할의 작업에서 부른다");
		assertThat(this.articles.calls).isEmpty();
	}

	@Test
	void readsTheArticlesWithAnEvaluator() {
		EventMembershipService service = new EventMembershipService(FakeArticleEvaluator::new, this.articles,
				new NoJudgements(), new TransactionTemplate(), () -> Instant.EPOCH);

		assertThat(service.judge(QUERY)).isEqualTo(new MembershipJudgement.ArticleMissing());
		assertThat(this.articles.calls).containsExactly(List.of(new ArticleId(1), new ArticleId(3)));
	}

	/** 기사가 하나도 없는 조회 — 받은 ID 목록을 모은다 */
	private static final class Reads implements ArticleDirectory {

		private final List<List<ArticleId>> calls = new ArrayList<>();

		@Override
		public Map<ArticleId, ArticleContent> findAll(Collection<ArticleId> ids) {
			this.calls.add(List.copyOf(ids));
			return Map.of();
		}

		@Override
		public List<ArticleId> firstSeenSince(Instant since) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<ArticleId, Set<SourceId>> sources(Collection<ArticleId> ids) {
			throw new UnsupportedOperationException();
		}

	}

	/** 쓰기가 일어나면 크게 실패하는 저장소 */
	private static final class NoJudgements implements EventJudgementRepository {

		@Override
		public void insert(MembershipRecord record) {
			throw new UnsupportedOperationException("쓰지 않는다");
		}

		@Override
		public void deleteByArticles(Collection<ArticleId> articleIds) {
			throw new UnsupportedOperationException("지우지 않는다");
		}

	}

}
