package net.yozmnews.admin.adapter.in.web;

import java.util.function.LongFunction;

import net.yozmnews.event.EventId;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.EvaluationId;
import net.yozmnews.tag.TagSetId;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 경로의 ID — 1 미만은 있을 수 없는 소스 · 피드 · 태그 체계 버전 · 평가 · 사건이라 404 다(식별자 생성자의 예외가 500 으로 새지 않게). docs/specs/admin.md §4 · admin-R2.
 */
final class AdminIds {

	private static final String TAG_SET = "태그 체계 버전";

	private static final String EVALUATION = "평가";

	private static final String EVENT = "사건";

	private static final String STORYLINE = "스토리라인";

	private AdminIds() {
	}

	static SourceId source(long id) {
		return positive(id, "소스", SourceId::new);
	}

	static TagSetId tagSet(long id) {
		return positive(id, TAG_SET, TagSetId::new);
	}

	static EvaluationId evaluation(long id) {
		return positive(id, EVALUATION, EvaluationId::new);
	}

	static EventId event(long id) {
		return positive(id, EVENT, EventId::new);
	}

	static FeedId feed(long id) {
		return positive(id, "피드", FeedId::new);
	}

	/** 초대 ID — 없는 초대의 404 설명은 공개 예외와 같은 "없는 초대: {ID}" 다 */
	static long invitation(long id) {
		return positive(id, "초대", value -> value);
	}

	/** 없는 태그 체계 버전의 404 — 1 미만 ID 와 없는 버전의 원문이 함께 쓴다 */
	static ResponseStatusException unknownTagSet(long id) {
		return notFound(TAG_SET, id);
	}

	/** 없는 평가의 404 — 1 미만 ID 와 없는 평가의 상세가 함께 쓴다 */
	static ResponseStatusException unknownEvaluation(long id) {
		return notFound(EVALUATION, id);
	}

	/** 없는 사건의 404 — 1 미만 ID 와 없는 사건의 상세가 함께 쓴다 */
	static ResponseStatusException unknownEvent(long id) {
		return notFound(EVENT, id);
	}

	static StorylineId storyline(long id) {
		return positive(id, STORYLINE, StorylineId::new);
	}

	static ResponseStatusException unknownStoryline(long id) {
		return notFound(STORYLINE, id);
	}

	private static <T> T positive(long id, String target, LongFunction<T> create) {
		if (id < 1) {
			throw notFound(target, id);
		}
		return create.apply(id);
	}

	private static ResponseStatusException notFound(String target, long id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 " + target + ": " + id);
	}

}
