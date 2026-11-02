package net.yozmnews.source.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.platform.YamlProblemKind;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 공통 YAML 문제 종류 → 수집 프로필 문제 코드 — 같은 이름으로 옮겨 운영자에게 보이는 코드 문자열이 그대로다.
 * <p>
 * 지키는 규칙: source-R32 · platform-R17
 */
class ProblemCodeTests {

	@ParameterizedTest
	@CsvSource({ "TOO_LARGE, TOO_LARGE", "SYNTAX_ERROR, SYNTAX_ERROR", "NOT_A_MAPPING, NOT_A_MAPPING",
			"MULTIPLE_DOCUMENTS, MULTIPLE_DOCUMENTS", "UNKNOWN_KEY, UNKNOWN_KEY", "DUPLICATE_KEY, DUPLICATE_KEY",
			"ALIAS_NOT_ALLOWED, ALIAS_NOT_ALLOWED", "ANCHOR_NOT_ALLOWED, ANCHOR_NOT_ALLOWED",
			"TAG_NOT_ALLOWED, TAG_NOT_ALLOWED", "NULL_NOT_ALLOWED, NULL_NOT_ALLOWED", "WRONG_TYPE, WRONG_TYPE" })
	void carriesACommonKindOverToTheCodeOfTheSameName(YamlProblemKind kind, ProblemCode code) {
		assertThat(ProblemCode.of(kind)).isEqualTo(code);
	}

}
