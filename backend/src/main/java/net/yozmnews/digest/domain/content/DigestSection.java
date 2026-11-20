package net.yozmnews.digest.domain.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.digest.SectionKind;
import net.yozmnews.digest.domain.candidate.AliasedItem;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.digest.domain.output.CitedDigest;
import net.yozmnews.digest.domain.output.CitedHeadline;
import net.yozmnews.digest.domain.output.CitedStory;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 다이제스트 내용 aggregate — 섹션 하나와 그 인용 · 사건 · docs/specs/digest.md §4 · digest-R7
 * <p>
 * 인용 섹션은 항목 하나다 — 인용은 항목의 표본 기사, 섹션 사건은 항목의 사건과 그 선정 이유다.
 * <p>
 * 끝날 때 한 번 만들어 쓰고 바꾸지 않는다.
 */
@Table("digest_sections")
public final class DigestSection {

	@Id
	private @Nullable Long id;

	private final long digestId;

	private final SectionKind kind;

	private final int position;

	private final @Nullable String title;

	private final @Nullable String body;

	@MappedCollection(idColumn = "digest_section_id", keyColumn = "position")
	private final List<DigestItem> items;

	@MappedCollection(idColumn = "digest_section_id", keyColumn = "position")
	private final List<SectionEvent> events;

	@PersistenceCreator
	DigestSection(@Nullable Long id, long digestId, SectionKind kind, int position, @Nullable String title,
			@Nullable String body, List<DigestItem> items, List<SectionEvent> events) {
		this.id = id;
		this.digestId = digestId;
		this.kind = Objects.requireNonNull(kind, "kind");
		this.position = position;
		this.title = title;
		this.body = body;
		this.items = List.copyOf(items);
		this.events = List.copyOf(events);
	}

	/**
	 * 항목과 대조한 출력을 섹션으로 나눈다 — 개요(0), 주요 뉴스 항목마다(1 … k — 제목 · 요약), 헤드라인 항목마다(k + 1 … — 제목 · 본문은
	 * 코멘트). 인용은 항목의 남은 표본 기사, 섹션 사건은 항목의 사건 순서다.
	 */
	public static List<DigestSection> of(DigestId digest, CitedDigest cited) {
		Objects.requireNonNull(digest, "digest");
		Objects.requireNonNull(cited, "cited");
		List<DigestSection> sections = new ArrayList<>();
		sections.add(new DigestSection(null, digest.value(), SectionKind.OVERVIEW, 0, null, cited.overview(),
				List.of(), List.of()));
		for (CitedStory story : cited.stories()) {
			sections.add(new DigestSection(null, digest.value(), SectionKind.TOP_STORY, sections.size(), story.title(),
					story.summary(), items(story.item()), story.item().events()));
		}
		for (CitedHeadline headline : cited.headlines()) {
			sections.add(new DigestSection(null, digest.value(), SectionKind.HEADLINE, sections.size(),
					headline.title(), headline.note(), items(headline.item()), headline.item().events()));
		}
		return List.copyOf(sections);
	}

	private static List<DigestItem> items(AliasedItem item) {
		List<DigestItem> items = new ArrayList<>(item.articles().size());
		for (DigestCandidate article : item.articles()) {
			items.add(new DigestItem(article.articleId().value()));
		}
		return items;
	}

	/**
	 * @return 저장소가 매긴 식별자 — 저장 전이면 빈 값
	 */
	public Optional<Long> id() {
		return Optional.ofNullable(this.id);
	}

	public DigestId digestId() {
		return new DigestId(this.digestId);
	}

	public SectionKind kind() {
		return this.kind;
	}

	/** 다이제스트 안의 순서 — 섹션을 지우면 빈 번호가 생길 수 있다 */
	public int position() {
		return this.position;
	}

	/**
	 * @return 주요 뉴스 · 헤드라인의 제목 — 개요면 빈 값
	 */
	public Optional<String> title() {
		return Optional.ofNullable(this.title);
	}

	/**
	 * @return 개요 · 주요 뉴스의 본문, 헤드라인의 코멘트 — 코멘트가 없는 헤드라인이면 빈 값
	 */
	public Optional<String> body() {
		return Optional.ofNullable(this.body);
	}

	/** 인용 — 순서대로 */
	public List<DigestItem> items() {
		return this.items;
	}

	/** 섹션 사건 — 순서대로. 개요는 비었다 */
	public List<SectionEvent> events() {
		return this.events;
	}

}
