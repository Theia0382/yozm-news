package net.yozmnews.source.domain.profile;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 프리셋 하나 — docs/specs/source.md §4 · source-R12.
 *
 * @param key 프리셋 키(파일 이름)
 * @param profile 검증된 프로필 — 이용조건 검토일이 반드시 있다
 * @param text 파일 원문(주석 포함)
 */
public record Preset(PresetKey key, CollectionProfile profile, String text) {

	/**
	 * 이용조건 검토일이 있는지 확인한다.
	 * @throws IllegalArgumentException 프로필에 이용조건 검토일이 없을 때
	 */
	public Preset {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(profile, "profile");
		Objects.requireNonNull(text, "text");
		if (profile.termsReviewedAt().isEmpty()) {
			throw new IllegalArgumentException("프리셋에는 이용조건 검토일이 있어야 한다: " + key);
		}
	}

	/** 프리셋 파일 원문을 검증해 프리셋으로 만든다 — 입력 오류는 {@link PresetParse.Invalid} 로 돌려준다 */
	public static PresetParse parse(PresetKey key, String text, ProfileParser parser) {
		Objects.requireNonNull(key, "key");
		return switch (parser.parsePresetFile(text)) {
			case ProfileParse.Parsed parsed -> new PresetParse.Parsed(new Preset(key, parsed.profile(), text));
			case ProfileParse.Invalid invalid -> new PresetParse.Invalid(invalid.problems());
		};
	}

	/** 이용조건 검토일을 돌려준다 — 프로필의 값이고, 생성자가 있음을 지킨다 */
	public LocalDate termsReviewedAt() {
		return this.profile.termsReviewedAt().orElseThrow();
	}

}
