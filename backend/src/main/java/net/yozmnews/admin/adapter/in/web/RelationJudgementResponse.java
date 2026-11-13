package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.evaluation.RelationJudgementView;
import net.yozmnews.evaluation.RelationReading;
import org.jspecify.annotations.Nullable;

/**
 * 관계 판정 원본 — 판별자 {@code reading} 하나의 평평한 값 · docs/specs/admin.md §5 admin-R1 · admin-R7.
 * <p>
 * {@link Reading#READ} 면 답 다섯과 두 한 사안 확률이 있고 {@code message} 가 null 이다. 그 밖이면 확률이 모두 null 이고 {@code message}
 * 가 읽지 못한 까닭의 문구다. 확률의 방향은 원본의 first · second 흐름({@code firstId} · {@code secondId}) 기준이다.
 *
 * @param firstId 그 판정 요청의 first 스토리라인 — 판정 뒤 합쳐진 스토리라인일 수 있다
 * @param secondId 그 판정 요청의 second 스토리라인
 * @param none 해당 없음의 확률
 * @param secondPartOfFirst second 가 first 의 부분일 확률
 * @param firstPartOfSecond first 가 second 의 부분일 확률
 * @param firstLedToSecond first 가 second 를 낳았을 확률
 * @param secondLedToFirst second 가 first 를 낳았을 확률
 * @param firstOneMatter first 가 한 사안의 전개일 확률
 * @param secondOneMatter second 가 한 사안의 전개일 확률
 */
record RelationJudgementResponse(long id, long firstId, long secondId, String modelVersion, String wordingVersion,
		int inputTokens, Instant judgedAt, Reading reading, @Nullable Double none, @Nullable Double secondPartOfFirst,
		@Nullable Double firstPartOfSecond, @Nullable Double firstLedToSecond, @Nullable Double secondLedToFirst,
		@Nullable Double firstOneMatter, @Nullable Double secondOneMatter, @Nullable String message) {

	/** 원본 응답을 다시 읽은 결과 */
	enum Reading {

		READ, NO_EVALUATOR, OTHER_EVALUATOR, UNREADABLE

	}

	static RelationJudgementResponse of(RelationJudgementView view) {
		return switch (view.reading()) {
			case RelationReading.Read read -> new RelationJudgementResponse(view.id().value(), view.firstFlowId(),
					view.secondFlowId(), view.modelVersion(), view.wordingVersion(), view.inputTokens(), view.judgedAt(),
					Reading.READ, read.probabilities().get(RelationAnswer.NONE),
					read.probabilities().get(RelationAnswer.SECOND_PART_OF_FIRST),
					read.probabilities().get(RelationAnswer.FIRST_PART_OF_SECOND),
					read.probabilities().get(RelationAnswer.FIRST_LED_TO_SECOND),
					read.probabilities().get(RelationAnswer.SECOND_LED_TO_FIRST), read.firstOneMatter(),
					read.secondOneMatter(), null);
			case RelationReading.Unread unread -> new RelationJudgementResponse(view.id().value(), view.firstFlowId(),
					view.secondFlowId(), view.modelVersion(), view.wordingVersion(), view.inputTokens(), view.judgedAt(),
					switch (unread.reason()) {
						case NO_EVALUATOR -> Reading.NO_EVALUATOR;
						case OTHER_EVALUATOR -> Reading.OTHER_EVALUATOR;
						case UNREADABLE -> Reading.UNREADABLE;
					}, null, null, null, null, null, null, null, unread.message());
		};
	}

}
