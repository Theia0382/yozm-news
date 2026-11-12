package net.yozmnews.evaluation.domain.evaluator;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.CodePoints;
import org.jspecify.annotations.Nullable;

/**
 * 관계 판정에 싣는 서술 사건 하나 — 시각과 대표 기사의 제목 · 요약문 · docs/specs/evaluation.md §4 · evaluation-R48.
 * <p>
 * 제목의 길이 상한은 사건 소속 판정의 서술 제목과 같다({@link CandidateEvent#MAX_TITLE_CODE_POINTS} — 정본).
 *
 * @param at 사건의 시각
 * @param title 대표 기사의 제목 — 상한 안으로 자른 값
 * @param summary 대표 기사의 요약문 — 상한 안으로 자른 값. 싣지 않으면 null
 */
public record HappeningText(Instant at, String title, @Nullable String summary) {

	/** 요약문의 길이 상한(코드 포인트) — 정본. 측정 전 목표 */
	public static final int MAX_SUMMARY_CODE_POINTS = 160;

	/**
	 * @throws IllegalArgumentException 제목 · 요약문이 공백뿐이거나 상한을 넘을 때 — 상한을 모르는 값은 {@link #limited} 로 만든다
	 */
	public HappeningText {
		Objects.requireNonNull(at, "at");
		Objects.requireNonNull(title, "title");
		if (title.isBlank()) {
			throw new IllegalArgumentException("서술 제목이 공백뿐이다");
		}
		if (title.codePointCount(0, title.length()) > CandidateEvent.MAX_TITLE_CODE_POINTS) {
			throw new IllegalArgumentException("서술 제목은 " + CandidateEvent.MAX_TITLE_CODE_POINTS + " 코드 포인트까지다");
		}
		if (summary != null) {
			if (summary.isBlank()) {
				throw new IllegalArgumentException("요약문이 공백뿐이다 — 없으면 null 이다");
			}
			if (summary.codePointCount(0, summary.length()) > MAX_SUMMARY_CODE_POINTS) {
				throw new IllegalArgumentException("요약문은 " + MAX_SUMMARY_CODE_POINTS + " 코드 포인트까지다");
			}
		}
	}

	/**
	 * 상한을 적용해 만든다 — 정본. 제목 · 요약문을 앞에서부터 상한까지 자르고(서로게이트 쌍을 가르지 않는다) 끝 공백을 지운다. 자른 뒤
	 * 공백뿐인 요약문은 없는 요약문이다.
	 * @param summary 실을 요약문 — 싣지 않으면 null
	 * @return 자른 제목이 공백뿐이면 빈 값
	 */
	public static Optional<HappeningText> limited(Instant at, String title, @Nullable String summary) {
		String cutTitle = CodePoints.prefix(Objects.requireNonNull(title, "title"), CandidateEvent.MAX_TITLE_CODE_POINTS)
			.stripTrailing();
		if (cutTitle.isBlank()) {
			return Optional.empty();
		}
		String cutSummary = (summary == null) ? null
				: EvaluationInput.presentSummary(CodePoints.prefix(summary, MAX_SUMMARY_CODE_POINTS).stripTrailing());
		return Optional.of(new HappeningText(at, cutTitle, cutSummary));
	}

}
