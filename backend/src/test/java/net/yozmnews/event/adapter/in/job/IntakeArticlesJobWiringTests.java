package net.yozmnews.event.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import net.yozmnews.event.adapter.out.fake.FakeArticleEmbedder;
import net.yozmnews.event.application.AssignmentRound;
import net.yozmnews.event.application.EventIntakeRound;
import net.yozmnews.event.application.MergeRound;
import net.yozmnews.event.application.RejudgementRound;
import net.yozmnews.event.domain.feature.ArticleEmbedder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 사건 묶기 대기 반복 작업의 결선 — 작업 역할은 임베더 없이 뜨지 않는다. 회차 넷은 시험 대역(모의)이고, 작업 하나만 작업 역할로 올린다.
 * <p>
 * 지키는 규칙: event-R7 · ADR-0093
 */
class IntakeArticlesJobWiringTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withPropertyValues("spring.profiles.active=worker")
		.withBean(EventIntakeRound.class, () -> mock(EventIntakeRound.class))
		.withBean(AssignmentRound.class, () -> mock(AssignmentRound.class))
		.withBean(RejudgementRound.class, () -> mock(RejudgementRound.class))
		.withBean(MergeRound.class, () -> mock(MergeRound.class))
		.withUserConfiguration(IntakeArticlesJob.class);

	@Test
	void theWorkerRoleDoesNotStartWithoutAnEmbedder() {
		this.runner.run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining(ArticleEmbedder.class.getName());
		});
	}

	@Test
	void theWorkerRoleStartsWithAnEmbedder() {
		this.runner.withBean(ArticleEmbedder.class, FakeArticleEmbedder::new)
			.run(context -> assertThat(context).hasNotFailed().hasSingleBean(IntakeArticlesJob.class));
	}

}
