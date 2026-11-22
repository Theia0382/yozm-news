package net.yozmnews.digest.domain.output;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.platform.OutputShape;
import net.yozmnews.platform.OutputValue;
import net.yozmnews.platform.StructuredOutputReader;
import net.yozmnews.platform.StructuredRead;

/**
 * 출력 원문 읽기 — 정본 · docs/specs/digest.md §5 · digest-R4 · digest-R5
 * <p>
 * 이번 요청의 출력 서술({@link DigestOutputSchema#root})을 따라 읽는 것은 {@code platform} 의 {@link StructuredOutputReader} 이고, 이
 * 클래스는 걸은 값을 {@link DigestDraft} 로 옮긴다. 주요 뉴스 칸에서 없는 키는 빼고 읽고, 헤드라인 원소는 별칭({@link DigestOutputSchema#REF})으로
 * 모은다 — 같은 별칭이 또 나오면 처음 원소만 쓴다. 빠진 항목은 부르는 쪽이 항목 별칭과 견준다.
 */
public final class DigestOutputReader {

	private DigestOutputReader() {
	}

	/**
	 * 출력 원문을 읽는다
	 * @param root 이번 요청의 출력 서술({@link DigestOutputSchema#root})
	 */
	public static OutputRead read(String text, OutputShape.Obj root) {
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(root, "root");
		return switch (StructuredOutputReader.read(text, root)) {
			case StructuredRead.Read read -> new OutputRead.Read(draft(read.root()), read.overLimit());
			case StructuredRead.Unreadable unreadable -> new OutputRead.Unreadable(unreadable.reason());
		};
	}

	private static DigestDraft draft(OutputValue.Obj root) {
		Map<String, StoryDraft> stories = new LinkedHashMap<>();
		root.obj(DigestOutputSchema.TOP_STORIES).values().forEach((alias, element) -> {
			OutputValue.Obj story = (OutputValue.Obj) element;
			stories.put(alias, new StoryDraft(story.string(DigestOutputSchema.TITLE),
					story.string(DigestOutputSchema.SUMMARY)));
		});
		Map<String, HeadlineDraft> headlines = new LinkedHashMap<>();
		root.arr(DigestOutputSchema.HEADLINES).items().forEach(element -> {
			OutputValue.Obj headline = (OutputValue.Obj) element;
			headlines.putIfAbsent(headline.string(DigestOutputSchema.REF),
					new HeadlineDraft(headline.string(DigestOutputSchema.TITLE),
							headline.optionalString(DigestOutputSchema.NOTE).orElse(null)));
		});
		return new DigestDraft(root.string(DigestOutputSchema.OVERVIEW), stories, headlines);
	}

}
