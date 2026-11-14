package net.yozmnews.credential.domain.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * KEK 파일의 형식 판정 — 손으로 쓴 원문으로 규칙마다 문제 문구를 견주고, 문구에 값이 없음을 본다. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: credential-R5 · credential-R8
 */
class KekFileTests {

	/** 0x01 로 채운 32바이트의 Base64 */
	private static final String ONES = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";

	/** 0x02 로 채운 32바이트의 Base64 */
	private static final String TWOS = "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=";

	@Test
	void readsVersionsAndTakesTheLargestAsActive() {
		KekFileRead read = KekFile.parse("# 주석\n\n1:" + ONES + "\r\n 2 : " + TWOS + " \n");

		assertThat(read).isInstanceOfSatisfying(KekFileRead.Parsed.class, parsed -> {
			assertThat(parsed.keys().versions()).containsExactly(1, 2);
			assertThat(parsed.keys().activeVersion()).isEqualTo(2);
			assertThat(parsed.keys().toString()).isEqualTo("KeyEncryptionKeys[versions=[1, 2]]");
		});
	}

	@Test
	void reportsEveryProblemWithItsLineAndWithoutTheValue() {
		String text = String.join("\n", "1:" + ONES, "값만있는줄", "0:" + ONES, "x:" + ONES, "1:" + TWOS, "3:!!!!", "4:AQEB",
				"99999999999:" + ONES);

		assertThat(KekFile.parse(text)).isInstanceOfSatisfying(KekFileRead.Invalid.class,
				invalid -> assertThat(invalid.problems()).containsExactly("2번째 줄: 버전:값 꼴이 아니다",
						"3번째 줄: 버전은 1 이상의 정수여야 한다", "4번째 줄: 버전은 1 이상의 정수여야 한다", "5번째 줄: 버전 1 이 겹친다",
						"6번째 줄: 값이 표준 Base64 가 아니다", "7번째 줄: 값은 32바이트여야 한다 (지금 3바이트)",
						"8번째 줄: 버전은 1 이상의 정수여야 한다")
					.noneMatch(problem -> problem.contains(ONES) || problem.contains(TWOS)
							|| problem.contains("값만있는줄") || problem.contains("!!!!")));
	}

	@Test
	void rejectsAFileWithoutAnyKeyLine() {
		assertThat(KekFile.parse("# 주석뿐\n\n")).isEqualTo(new KekFileRead.Invalid(java.util.List.of("버전:값 줄이 하나도 없다")));
		assertThat(KekFile.parse("")).isEqualTo(new KekFileRead.Invalid(java.util.List.of("버전:값 줄이 하나도 없다")));
	}

	@Test
	void rejectsATextLongerThanTheLimit() {
		assertThat(KekFile.MAX_CHARS).isEqualTo(65_536);
		assertThat(KekFile.parse("1:" + ONES + "\n" + "#".repeat(65_536)))
			.isEqualTo(new KekFileRead.Invalid(java.util.List.of("파일이 65536자를 넘는다")));
		assertThat(KekFile.parse("1:" + ONES + "\n" + "#".repeat(65_536 - 47))).isInstanceOf(KekFileRead.Parsed.class);
	}

}
