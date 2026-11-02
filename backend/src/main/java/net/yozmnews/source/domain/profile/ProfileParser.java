package net.yozmnews.source.domain.profile;

import java.util.List;

import net.yozmnews.platform.YamlRead;
import net.yozmnews.platform.YamlTextReader;
import net.yozmnews.source.domain.profile.ProfileBinder.BindResult;
import net.yozmnews.source.domain.profile.ProfileValidator.Kind;
import net.yozmnews.source.domain.profile.ProfileValidator.ValidationResult;

/**
 * 프로필 텍스트 파서 — docs/specs/source.md §4
 */
public final class ProfileParser {

	/** 원문 길이 상한(문자 수) — source-R32 의 정본 */
	public static final int MAX_CHARS = 65_536;

	private final YamlTextReader reader = new YamlTextReader(MAX_CHARS);

	/** 운영자 프로필 원문을 해석한다 — 이용조건 검토일은 선택이다 */
	public ProfileParse parseOperatorProfile(String text) {
		return parse(text, Kind.OPERATOR_PROFILE);
	}

	/** 프리셋 파일 원문을 해석한다 — 이용조건 검토일이 반드시 있다 */
	ProfileParse parsePresetFile(String text) {
		return parse(text, Kind.PRESET_FILE);
	}

	private ProfileParse parse(String text, Kind kind) {
		YamlRead read = this.reader.read(text);
		return switch (read) {
			case YamlRead.Unreadable unreadable -> new ProfileParse.Invalid(List.of(ProfileProblem.of(unreadable.problem())));
			case YamlRead.Read tree -> {
				// 형이 틀린 값 위에서 의미를 검사하면 문제가 연쇄로 불어나므로 바인딩 문제가 있으면 여기서 멈춘다
				BindResult bound = ProfileBinder.bind(tree.root());
				if (!bound.problems().isEmpty()) {
					yield new ProfileParse.Invalid(bound.problems());
				}
				yield switch (ProfileValidator.validate(bound.draft(), kind)) {
					case ValidationResult.Valid valid -> new ProfileParse.Parsed(valid.profile());
					case ValidationResult.Invalid invalid -> new ProfileParse.Invalid(invalid.problems());
				};
			}
		};
	}

}
