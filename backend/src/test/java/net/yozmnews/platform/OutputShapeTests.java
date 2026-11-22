package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import java.util.Map;

import net.yozmnews.platform.OutputShape.OnBlank;
import net.yozmnews.platform.OutputShape.OverLimit;
import org.junit.jupiter.api.Test;

/**
 * LLM 출력 서술 마디 · 걸은 값 · 읽은 결과의 값 규칙 · docs/specs/platform.md §3
 */
class OutputShapeTests {

	private static final OutputShape.Str TEXT = new OutputShape.Str(3, OnBlank.UNREADABLE, OverLimit.UNREADABLE);

	@Test
	void keyedRefusesNoKeysAndRepeatedKeys() {
		assertThatIllegalArgumentException().isThrownBy(() -> new OutputShape.Keyed(List.of(), TEXT))
			.withMessage("키가 없다");
		assertThatIllegalArgumentException().isThrownBy(() -> new OutputShape.Keyed(List.of("a", "a"), TEXT))
			.withMessage("겹친 키가 있다: [a, a]");
		assertThat(new OutputShape.Keyed(List.of("a"), TEXT).keys()).containsExactly("a");
	}

	@Test
	void aStringLimitIsAtLeastOne() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new OutputShape.Str(0, OnBlank.UNREADABLE, OverLimit.UNREADABLE))
			.withMessage("문자열 상한이 1 보다 작다: 0");
		assertThat(new OutputShape.Str(1, OnBlank.UNREADABLE, OverLimit.UNREADABLE).maxCodePoints()).isEqualTo(1);
	}

	@Test
	void aReadRefusesANegativeOverLimitCount() {
		OutputValue.Obj root = new OutputValue.Obj(Map.of());

		assertThatIllegalArgumentException().isThrownBy(() -> new StructuredRead.Read(root, -1))
			.withMessage("overLimit 이 음수다: -1");
		assertThat(new StructuredRead.Read(root, 0).overLimit()).isZero();
	}

	@Test
	void objectAccessorsRefuseAMissingValueOrAnotherShape() {
		OutputValue.Obj obj = new OutputValue.Obj(Map.of("s", new OutputValue.Str("값"), "a",
				new OutputValue.Arr(List.of())));

		assertThat(obj.string("s")).isEqualTo("값");
		assertThat(obj.optionalString("없음")).isEmpty();
		assertThat(obj.arr("a").items()).isEmpty();
		assertThatIllegalStateException().isThrownBy(() -> obj.string("없음")).withMessage("값이 없다: 없음");
		assertThatIllegalStateException().isThrownBy(() -> obj.obj("s")).withMessage("객체가 아니다: s");
		assertThatIllegalStateException().isThrownBy(() -> obj.optionalString("a")).withMessage("문자열이 아니다: a");
	}

}
