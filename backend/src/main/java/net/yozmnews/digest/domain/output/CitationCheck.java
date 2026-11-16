package net.yozmnews.digest.domain.output;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.candidate.DigestCandidate;

/**
 * 인용 검증 — 정본 · docs/specs/digest.md §5 · digest-R6
 */
public final class CitationCheck {

	private CitationCheck() {
	}

	/** 읽은 출력의 별칭을 후보와 대조한다 */
	public static CitationResult check(DigestDraft draft, CandidateSet candidates) {
		Objects.requireNonNull(draft, "draft");
		Objects.requireNonNull(candidates, "candidates");
		int removedRefs = 0;
		int droppedItems = 0;
		List<CitedStory> stories = new ArrayList<>();
		Set<ArticleId> storyArticles = new HashSet<>();
		for (StoryDraft story : draft.topStories()) {
			List<DigestCandidate> refs = new ArrayList<>();
			Set<ArticleId> cited = new HashSet<>();
			for (String alias : story.refs()) {
				Optional<DigestCandidate> candidate = candidates.byAlias(alias);
				if (candidate.isEmpty() || !cited.add(candidate.get().articleId())) {
					removedRefs++;
					continue;
				}
				refs.add(candidate.get());
			}
			if (refs.isEmpty()) {
				droppedItems++;
				continue;
			}
			stories.add(new CitedStory(story.title(), story.summary(), refs));
			storyArticles.addAll(cited);
		}
		List<CitedHeadline> headlines = new ArrayList<>();
		Set<ArticleId> headlineArticles = new HashSet<>();
		for (HeadlineDraft headline : draft.moreHeadlines()) {
			Optional<DigestCandidate> candidate = candidates.byAlias(headline.ref());
			if (candidate.isEmpty() || storyArticles.contains(candidate.get().articleId())
					|| !headlineArticles.add(candidate.get().articleId())) {
				droppedItems++;
				continue;
			}
			headlines.add(new CitedHeadline(candidate.get(), headline.note()));
		}
		CitationDrops drops = new CitationDrops(removedRefs, droppedItems);
		if (stories.isEmpty() && headlines.isEmpty()) {
			return new CitationResult.NoCitation(drops);
		}
		return new CitationResult.Cited(new CitedDigest(draft.overview(), stories, headlines), drops);
	}

}
