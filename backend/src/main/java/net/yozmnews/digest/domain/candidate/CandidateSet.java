package net.yozmnews.digest.domain.candidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.article.ArticleId;

/**
 * 이번 생성의 후보와 별칭 — 정본 · docs/specs/digest.md §5 · digest-R3
 * <p>
 * 별칭은 입력 순서대로 {@value #ALIAS_PREFIX}1, {@value #ALIAS_PREFIX}2 … 이다. {@link #retain} 으로 좁혀도 남은 후보의 별칭은
 * 그대로다.
 */
public final class CandidateSet {

	/** 별칭의 접두 */
	public static final String ALIAS_PREFIX = "A";

	private final List<AliasedCandidate> entries;

	private final Map<String, DigestCandidate> byAlias;

	private CandidateSet(List<AliasedCandidate> entries) {
		this.entries = List.copyOf(entries);
		Map<String, DigestCandidate> index = new LinkedHashMap<>();
		for (AliasedCandidate entry : this.entries) {
			index.put(entry.alias(), entry.candidate());
		}
		this.byAlias = Collections.unmodifiableMap(index);
	}

	/**
	 * 후보에 입력 순서대로 별칭을 매긴다.
	 * @throws IllegalArgumentException 같은 기사가 두 번 있을 때
	 */
	public static CandidateSet of(List<DigestCandidate> candidates) {
		Objects.requireNonNull(candidates, "candidates");
		Set<ArticleId> seen = new HashSet<>();
		List<AliasedCandidate> entries = new ArrayList<>(candidates.size());
		for (DigestCandidate candidate : candidates) {
			Objects.requireNonNull(candidate, "candidate");
			if (!seen.add(candidate.articleId())) {
				throw new IllegalArgumentException("같은 기사가 후보에 두 번 있다: " + candidate.articleId().value());
			}
			entries.add(new AliasedCandidate(ALIAS_PREFIX + (entries.size() + 1), candidate));
		}
		return new CandidateSet(entries);
	}

	/** 별칭 순서의 후보 */
	public List<AliasedCandidate> entries() {
		return this.entries;
	}

	/**
	 * @return 그 별칭의 후보 — 없는 별칭이면 빈 값(대소문자를 가린다)
	 */
	public Optional<DigestCandidate> byAlias(String alias) {
		Objects.requireNonNull(alias, "alias");
		return Optional.ofNullable(this.byAlias.get(alias));
	}

	/** 후보 기사 — 별칭 순서 */
	public Set<ArticleId> articleIds() {
		Set<ArticleId> ids = new LinkedHashSet<>();
		for (AliasedCandidate entry : this.entries) {
			ids.add(entry.candidate().articleId());
		}
		return Collections.unmodifiableSet(ids);
	}

	/** 주어진 기사의 후보만 남긴다 — 별칭은 다시 매기지 않는다 */
	public CandidateSet retain(Set<ArticleId> present) {
		Objects.requireNonNull(present, "present");
		List<AliasedCandidate> kept = new ArrayList<>();
		for (AliasedCandidate entry : this.entries) {
			if (present.contains(entry.candidate().articleId())) {
				kept.add(entry);
			}
		}
		return new CandidateSet(kept);
	}

	public int size() {
		return this.entries.size();
	}

	public boolean isEmpty() {
		return this.entries.isEmpty();
	}

}
