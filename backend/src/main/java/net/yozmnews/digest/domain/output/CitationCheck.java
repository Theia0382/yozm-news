package net.yozmnews.digest.domain.output;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.domain.candidate.AliasedItem;
import net.yozmnews.digest.domain.candidate.CandidateSet;

/**
 * 항목 대조 — 정본 · docs/specs/digest.md §5 · digest-R6
 * <p>
 * 출력은 기사를 인용하지 않고 칸마다 항목 별칭을 키로 쓴다 — 인용은 코드가 정한 항목의 표본 기사다. 항목 순서(항목 순위)대로, 주요 뉴스
 * 항목은 주요 뉴스 칸에서, 헤드라인 항목은 헤드라인 칸에서 그 별칭의 칸을 찾는다. 다른 칸의 키 · 모르는 키는 찾지 않는다.
 * 출력에 없는 항목은 저장하지 않는다.
 */
public final class CitationCheck {

	private CitationCheck() {
	}

	/** 읽은 출력의 칸을 항목과 대조한다 */
	public static CitationResult check(DigestDraft draft, CandidateSet candidates) {
		Objects.requireNonNull(draft, "draft");
		Objects.requireNonNull(candidates, "candidates");
		List<CitedStory> stories = new ArrayList<>();
		List<CitedHeadline> headlines = new ArrayList<>();
		int missing = 0;
		for (AliasedItem item : candidates.items()) {
			boolean written = switch (item.kind()) {
				case TOP_STORY -> {
					StoryDraft story = draft.topStories().get(item.alias());
					if (story != null) {
						stories.add(new CitedStory(item, story.title(), story.summary()));
					}
					yield story != null;
				}
				case HEADLINE -> {
					HeadlineDraft headline = draft.headlines().get(item.alias());
					if (headline != null) {
						headlines.add(new CitedHeadline(item, headline.title(), headline.note()));
					}
					yield headline != null;
				}
			};
			if (!written) {
				missing++;
			}
		}
		CitationDrops drops = new CitationDrops(0, missing);
		if (stories.isEmpty() && headlines.isEmpty()) {
			return new CitationResult.NoCitation(drops);
		}
		return new CitationResult.Cited(new CitedDigest(draft.overview(), stories, headlines), drops);
	}

}
