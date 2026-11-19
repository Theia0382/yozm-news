package net.yozmnews.digest.domain.candidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.content.SectionEvent;
import net.yozmnews.digest.domain.input.CandidateArticle;
import net.yozmnews.digest.domain.input.DigestInput;
import net.yozmnews.digest.domain.input.InputEvent;
import net.yozmnews.digest.domain.input.InputItem;

/**
 * 이번 생성의 항목 · 기사와 별칭 — 정본 · docs/specs/digest.md §5 · digest-R3
 * <p>
 * 기사 별칭은 입력 순서(항목 → 사건 → 기사)대로 {@value #ALIAS_PREFIX}1, {@value #ALIAS_PREFIX}2 … 이고 후보 행의 열쇠다 — 프롬프트에는
 * 싣지 않는다. 항목 별칭은 입력 순서대로 {@value #ITEM_PREFIX}1, {@value #ITEM_PREFIX}2 … 이고 출력이 항목을 가리키는 이름이다.
 * {@link #retain} 으로 좁혀도 남은 기사 · 항목의 별칭은 그대로다.
 */
public final class CandidateSet {

	/** 기사 별칭의 접두 */
	public static final String ALIAS_PREFIX = "A";

	/** 항목 별칭의 접두 */
	public static final String ITEM_PREFIX = "I";

	private final List<AliasedCandidate> entries;

	private final List<AliasedItem> items;

	private final Map<String, AliasedItem> byAlias;

	private CandidateSet(List<AliasedCandidate> entries, List<AliasedItem> items) {
		this.entries = List.copyOf(entries);
		this.items = List.copyOf(items);
		Map<String, AliasedItem> index = new LinkedHashMap<>();
		for (AliasedItem item : this.items) {
			index.put(item.alias(), item);
		}
		this.byAlias = Collections.unmodifiableMap(index);
	}

	/**
	 * 입력의 항목 · 기사에 입력 순서대로 별칭을 매긴다 — 같은 사건 · 기사가 두 번 없는 것은 입력이 지킨다.
	 */
	public static CandidateSet of(DigestInput input) {
		Objects.requireNonNull(input, "input");
		List<AliasedCandidate> entries = new ArrayList<>();
		List<AliasedItem> items = new ArrayList<>(input.items().size());
		for (InputItem item : input.items()) {
			List<SectionEvent> events = new ArrayList<>(item.events().size());
			List<DigestCandidate> articles = new ArrayList<>();
			for (InputEvent event : item.events()) {
				events.add(new SectionEvent(event.event().value(), event.reason()));
				for (CandidateArticle article : event.articles()) {
					entries.add(new AliasedCandidate(ALIAS_PREFIX + (entries.size() + 1), article.candidate()));
					articles.add(article.candidate());
				}
			}
			items.add(new AliasedItem(ITEM_PREFIX + (items.size() + 1), item.kind(), events, articles));
		}
		return new CandidateSet(entries, items);
	}

	/** 기사 별칭 순서의 기사 — 후보 행 */
	public List<AliasedCandidate> entries() {
		return this.entries;
	}

	/** 항목 별칭 순서의 항목 */
	public List<AliasedItem> items() {
		return this.items;
	}

	/**
	 * @return 그 별칭의 항목 — 없는 별칭이면 빈 값(대소문자를 가린다)
	 */
	public Optional<AliasedItem> item(String alias) {
		Objects.requireNonNull(alias, "alias");
		return Optional.ofNullable(this.byAlias.get(alias));
	}

	/** 기사 — 기사 별칭 순서 */
	public Set<ArticleId> articleIds() {
		Set<ArticleId> ids = new LinkedHashSet<>();
		for (AliasedCandidate entry : this.entries) {
			ids.add(entry.candidate().articleId());
		}
		return Collections.unmodifiableSet(ids);
	}

	/**
	 * 주어진 기사만 남긴다 — 항목의 기사도 좁히고, 기사가 남지 않은 항목은 뺀다. 별칭은 다시 매기지 않는다
	 */
	public CandidateSet retain(Set<ArticleId> present) {
		Objects.requireNonNull(present, "present");
		List<AliasedCandidate> kept = this.entries.stream()
			.filter(entry -> present.contains(entry.candidate().articleId()))
			.toList();
		List<AliasedItem> items = new ArrayList<>();
		for (AliasedItem item : this.items) {
			List<DigestCandidate> articles = item.articles()
				.stream()
				.filter(article -> present.contains(article.articleId()))
				.toList();
			if (!articles.isEmpty()) {
				items.add(new AliasedItem(item.alias(), item.kind(), item.events(), articles));
			}
		}
		return new CandidateSet(kept, items);
	}

	/** 기사 수 */
	public int size() {
		return this.entries.size();
	}

	/** 항목의 사건 수 — 다이제스트의 후보 수 */
	public int eventCount() {
		return this.items.stream().mapToInt(item -> item.events().size()).sum();
	}

	/** 기사가 없는가 — 기사가 없으면 항목도 없다 */
	public boolean isEmpty() {
		return this.entries.isEmpty();
	}

}
