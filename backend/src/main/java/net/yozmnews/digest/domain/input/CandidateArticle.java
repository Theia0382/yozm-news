package net.yozmnews.digest.domain.input;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.domain.candidate.DigestCandidate;
import org.jspecify.annotations.Nullable;

/**
 * 사건의 표본 기사 하나와 프롬프트의 기사 줄 재료 — 토큰 어림에 쓴 제목 · 요약문과 같은 읽기의 값이다 — docs/specs/digest.md §5 · digest-R22
 *
 * @param candidate 기사와 선정 이유 — 이유는 그 사건의 선정 이유
 * @param title 제목
 * @param summary 저장된 RSS 요약문 — 없으면 null
 * @param sources 출처 이름 — 엔트리 순서, 비어 있을 수 있다
 * @param publishedAt 발행 시각
 * @param topics 붙은 주제(활성 중분류)의 이름 — 체계 순서
 * @param regions 붙은 지역의 이름 — 체계 순서
 */
public record CandidateArticle(DigestCandidate candidate, String title, @Nullable String summary,
		List<String> sources, Instant publishedAt, List<String> topics, List<String> regions) {

	public CandidateArticle {
		Objects.requireNonNull(candidate, "candidate");
		Objects.requireNonNull(title, "title");
		sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
		Objects.requireNonNull(publishedAt, "publishedAt");
		topics = List.copyOf(Objects.requireNonNull(topics, "topics"));
		regions = List.copyOf(Objects.requireNonNull(regions, "regions"));
	}

}
