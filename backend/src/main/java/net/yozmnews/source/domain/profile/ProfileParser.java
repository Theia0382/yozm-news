package net.yozmnews.source.domain.profile;

import java.util.List;
import java.util.Objects;

import net.yozmnews.source.domain.profile.ProfileBinder.BindResult;
import net.yozmnews.source.domain.profile.ProfileValidator.Kind;
import net.yozmnews.source.domain.profile.ProfileValidator.ValidationResult;
import net.yozmnews.source.domain.profile.text.ProfileTextReader;
import net.yozmnews.source.domain.profile.text.ReadResult;

/**
 * 프로필 텍스트 파서 — docs/specs/source.md §4
 */
public final class ProfileParser {

	private final ProfileTextReader reader;

	public ProfileParser(ProfileTextReader reader) {
		this.reader = Objects.requireNonNull(reader, "reader");
	}

	/** 운영자 프로필 원문을 해석한다 — 이용조건 검토일은 선택이다 */
	public ProfileParse parseOperatorProfile(String text) {
		return parse(text, Kind.OPERATOR_PROFILE);
	}

	/** 프리셋 파일 원문을 해석한다 — 이용조건 검토일이 반드시 있다 */
	ProfileParse parsePresetFile(String text) {
		return parse(text, Kind.PRESET_FILE);
	}

	private ProfileParse parse(String text, Kind kind) {
		ReadResult read = this.reader.read(text);
		return switch (read) {
			case ReadResult.Unreadable unreadable -> new ProfileParse.Invalid(List.of(unreadable.problem()));
			case ReadResult.Read tree -> {
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
