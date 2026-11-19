package net.yozmnews.digest.domain.output;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.digest.domain.candidate.AliasedItem;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.item.ItemKind;

/**
 * 항목 대조 — 정본 · docs/specs/digest.md §5 · digest-R6
 * <p>
 * 출력은 기사를 인용하지 않고 항목을 별칭으로 가리킨다 — 인용은 코드가 정한 항목의 표본 기사다. 주요 뉴스를 앞에서부터, 그다음 헤드라인을
 * 앞에서부터 대조해 모르는 항목 별칭 · 다른 칸의 항목(주요 뉴스 항목을 헤드라인에, 헤드라인 항목을 주요 뉴스에) · 앞에서 이미 쓴 항목은
 * 버린다. 출력에 없는 항목은 저장하지 않는다. 남은 주요 뉴스 · 헤드라인은 각각 항목 순서(항목 순위)로 둔다 — 출력의 순서는 따르지 않는다.
 */
public final class CitationCheck {

	private CitationCheck() {
	}

	/** 읽은 출력의 항목 별칭을 항목과 대조한다 */
	public static CitationResult check(DigestDraft draft, CandidateSet candidates) {
		Objects.requireNonNull(draft, "draft");
		Objects.requireNonNull(candidates, "candidates");
		int dropped = 0;
		Set<String> used = new HashSet<>();
		Map<String, CitedStory> stories = new HashMap<>();
		for (StoryDraft story : draft.topStories()) {
			Optional<AliasedItem> item = matching(candidates, story.ref(), ItemKind.TOP_STORY, used);
			if (item.isEmpty()) {
				dropped++;
				continue;
			}
			stories.put(item.get().alias(), new CitedStory(item.get(), story.title(), story.summary()));
		}
		Map<String, CitedHeadline> headlines = new HashMap<>();
		for (HeadlineDraft headline : draft.headlines()) {
			Optional<AliasedItem> item = matching(candidates, headline.ref(), ItemKind.HEADLINE, used);
			if (item.isEmpty()) {
				dropped++;
				continue;
			}
			headlines.put(item.get().alias(), new CitedHeadline(item.get(), headline.title(), headline.note()));
		}
		List<AliasedItem> items = candidates.items();
		List<CitedStory> orderedStories = items.stream()
			.map(item -> stories.get(item.alias()))
			.filter(Objects::nonNull)
			.toList();
		List<CitedHeadline> orderedHeadlines = items.stream()
			.map(item -> headlines.get(item.alias()))
			.filter(Objects::nonNull)
			.toList();
		CitationDrops drops = new CitationDrops(dropped, items.size() - used.size());
		if (orderedStories.isEmpty() && orderedHeadlines.isEmpty()) {
			return new CitationResult.NoCitation(drops);
		}
		return new CitationResult.Cited(new CitedDigest(draft.overview(), orderedStories, orderedHeadlines), drops);
	}

	/** 그 칸에 쓸 수 있고 아직 쓰지 않은 항목 — 쓰면 {@code used} 에 더한다 */
	private static Optional<AliasedItem> matching(CandidateSet candidates, String ref, ItemKind kind,
			Set<String> used) {
		Optional<AliasedItem> item = candidates.item(ref);
		if (item.isEmpty() || item.get().kind() != kind || !used.add(ref)) {
			return Optional.empty();
		}
		return item;
	}

}
