package net.yozmnews.digest.domain.output;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.AliasedItem;
import net.yozmnews.digest.domain.candidate.CandidateSet;

/**
 * 항목 대조 — 정본 · docs/specs/digest.md §5 · digest-R6
 * <p>
 * 출력은 기사를 인용하지 않고 칸마다 항목 별칭을 키로 쓴다 — 인용은 코드가 정한 항목의 표본 기사다. 항목 순서(항목 순위)대로, 주요 뉴스
 * 항목은 주요 뉴스 칸에서, 헤드라인 항목은 헤드라인 칸에서 그 별칭의 칸을 찾는다. 다른 칸의 키 · 모르는 키는 찾지 않는다. 출력에 칸이 없는
 * 항목은 버리지 않고 헤드라인으로 남긴다 — 제목은 그 항목의 첫 인용 기사 제목이고 코멘트는 없다. 주요 뉴스 항목도 헤드라인이 된다.
 */
public final class CitationCheck {

	private CitationCheck() {
	}

	/**
	 * 읽은 출력의 칸을 항목과 대조한다
	 * @param candidates 대조할 항목 — 지운 기사를 거른 집합
	 * @param titles 항목들의 기사 제목 — {@code candidates} 의 기사를 모두 담는다
	 * @throws NullPointerException 대체할 항목의 첫 인용 기사 제목이 {@code titles} 에 없을 때
	 */
	public static CitationResult check(DigestDraft draft, CandidateSet candidates, Map<ArticleId, String> titles) {
		Objects.requireNonNull(draft, "draft");
		Objects.requireNonNull(candidates, "candidates");
		Objects.requireNonNull(titles, "titles");
		List<CitedStory> stories = new ArrayList<>();
		List<CitedHeadline> headlines = new ArrayList<>();
		int substituted = 0;
		for (AliasedItem item : candidates.items()) {
			if (!draft.covers(item)) {
				ArticleId first = item.articles().getFirst().articleId();
				headlines.add(new CitedHeadline(item, Objects.requireNonNull(titles.get(first), "첫 인용 기사의 제목"), null));
				substituted++;
				continue;
			}
			// 종류가 늘면 컴파일 오류가 나게 식으로 가른다
			boolean topStory = switch (item.kind()) {
				case TOP_STORY -> true;
				case HEADLINE -> false;
			};
			if (topStory) {
				StoryDraft story = Objects.requireNonNull(draft.topStories().get(item.alias()));
				stories.add(new CitedStory(item, story.title(), story.summary()));
			}
			else {
				HeadlineDraft headline = Objects.requireNonNull(draft.headlines().get(item.alias()));
				headlines.add(new CitedHeadline(item, headline.title(), headline.note()));
			}
		}
		CitationDrops drops = new CitationDrops(substituted);
		if (stories.isEmpty() && headlines.isEmpty()) {
			return new CitationResult.NoCitation(drops);
		}
		return new CitationResult.Cited(new CitedDigest(draft.overview(), stories, headlines), drops);
	}

}
