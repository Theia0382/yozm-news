package net.yozmnews.evaluation.domain.tagset;

import java.util.List;

import net.yozmnews.evaluation.domain.tagset.TagSetBinder.BindResult;
import net.yozmnews.evaluation.domain.tagset.TagSetValidator.ValidationResult;
import net.yozmnews.platform.YamlRead;
import net.yozmnews.platform.YamlTextReader;

/**
 * 태그 체계 원문 파서 — docs/specs/evaluation.md §4 · evaluation-R5.
 * <p>
 * 원문 → {@link YamlTextReader} → {@link TagSetBinder} → {@link TagSetValidator}. 입력 오류는 예외가 아니라 문제 목록으로
 * 돌려준다. 스레드 안전하다.
 */
public final class TagSetParser {

	/** 원문 길이 상한(문자 수) — evaluation-R5 의 정본 */
	public static final int MAX_CHARS = 65_536;

	private final YamlTextReader reader = new YamlTextReader(MAX_CHARS);

	public TagSetParse parse(String text) {
		YamlRead read = this.reader.read(text);
		return switch (read) {
			case YamlRead.Unreadable unreadable -> new TagSetParse.Invalid(List.of(TagSetProblem.of(unreadable.problem())));
			case YamlRead.Read tree -> {
				// 형이 틀린 값 위에서 의미를 검사하면 문제가 연쇄로 불어나므로 바인딩 문제가 있으면 여기서 멈춘다
				BindResult bound = TagSetBinder.bind(tree.root());
				if (!bound.problems().isEmpty()) {
					yield new TagSetParse.Invalid(bound.problems());
				}
				yield switch (TagSetValidator.validate(bound.draft())) {
					case ValidationResult.Valid valid -> new TagSetParse.Parsed(valid.definition());
					case ValidationResult.Invalid invalid -> new TagSetParse.Invalid(invalid.problems());
				};
			}
		};
	}

}
