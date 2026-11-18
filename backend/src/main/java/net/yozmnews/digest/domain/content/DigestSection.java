package net.yozmnews.digest.domain.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.digest.SectionKind;
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
 * 다이제스트 내용 aggregate — 섹션 하나와 그 인용 · docs/specs/digest.md §4 · digest-R7
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

	@PersistenceCreator
	DigestSection(@Nullable Long id, long digestId, SectionKind kind, int position, @Nullable String title,
			@Nullable String body, List<DigestItem> items) {
		this.id = id;
		this.digestId = digestId;
		this.kind = Objects.requireNonNull(kind, "kind");
		this.position = position;
		this.title = title;
		this.body = body;
		this.items = List.copyOf(items);
	}

	/**
	 * 인용을 검증한 출력을 섹션으로 나눈다 — 개요(0), 주요 뉴스(1 … k), 추가 헤드라인이 있으면 그 묶음(k + 1).
	 */
	public static List<DigestSection> of(DigestId digest, CitedDigest cited) {
		Objects.requireNonNull(digest, "digest");
		Objects.requireNonNull(cited, "cited");
		List<DigestSection> sections = new ArrayList<>();
		sections.add(new DigestSection(null, digest.value(), SectionKind.OVERVIEW, 0, null, cited.overview(),
				List.of()));
		for (CitedStory story : cited.stories()) {
			List<DigestItem> items = new ArrayList<>();
			for (DigestCandidate ref : story.refs()) {
				items.add(new DigestItem(ref.articleId().value(), null, ref.reason()));
			}
			sections.add(new DigestSection(null, digest.value(), SectionKind.TOP_STORY, sections.size(), story.title(),
					story.summary(), items));
		}
		if (!cited.headlines().isEmpty()) {
			List<DigestItem> items = new ArrayList<>();
			for (CitedHeadline headline : cited.headlines()) {
				items.add(new DigestItem(headline.ref().articleId().value(), headline.note(),
						headline.ref().reason()));
			}
			sections.add(new DigestSection(null, digest.value(), SectionKind.MORE_HEADLINES, sections.size(), null,
					null, items));
		}
		return List.copyOf(sections);
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
	 * @return 주요 뉴스의 제목 — 다른 종류면 빈 값
	 */
	public Optional<String> title() {
		return Optional.ofNullable(this.title);
	}

	/**
	 * @return 개요 · 주요 뉴스의 본문 — 추가 헤드라인 묶음이면 빈 값
	 */
	public Optional<String> body() {
		return Optional.ofNullable(this.body);
	}

	/** 인용 — 순서대로 */
	public List<DigestItem> items() {
		return this.items;
	}

}
