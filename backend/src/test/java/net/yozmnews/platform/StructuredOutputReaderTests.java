package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.platform.OutputShape.OnBlank;
import net.yozmnews.platform.OutputShape.OverLimit;
import org.junit.jupiter.api.Test;

/**
 * LLM 출력 읽기 — 서술을 따라 걷기 · docs/specs/platform.md §5 · platform-R31
 * <p>
 * 다이제스트가 아닌 시험 서술로 범용 성질을 본다: 이름(필수, 10) · 별명(선택 · null 받음 · 빈 값은 없음, 5 넘으면 값만 지움) · 칸(키 꼴 —
 * 키 {@code a} · {@code b}, 값은 제목(필수, 4 넘으면 그 칸 버림)) · 줄(배열 — 원소는 문자열 3, 넘으면 그 원소 버림, 빈 값 그대로).
 */
class StructuredOutputReaderTests {

	private static final OutputShape.Obj ROOT = new OutputShape.Obj(List.of(
			new OutputShape.Property("name", new OutputShape.Str(10, OnBlank.UNREADABLE, OverLimit.UNREADABLE), true,
					false),
			new OutputShape.Property("nick", new OutputShape.Str(5, OnBlank.ABSENT, OverLimit.DROP_VALUE), false, true),
			new OutputShape.Property("cells",
					new OutputShape.Keyed(List.of("a", "b"),
							new OutputShape.Obj(List.of(new OutputShape.Property("title",
									new OutputShape.Str(4, OnBlank.UNREADABLE, OverLimit.DROP_ITEM), true, false)))),
					true, false),
			new OutputShape.Property("lines",
					new OutputShape.Arr(new OutputShape.Str(3, OnBlank.KEEP, OverLimit.DROP_ITEM)), true, false)));

	@Test
	void readsAWholeOutputAndStripsStrings() {
		StructuredRead.Read read = read("""
				{"name":" 홍길동 ","nick":"길동","cells":{"a":{"title":"가나"},"b":{"title":"다라"}},"lines":["ab"," "]}""");

		assertThat(read.overLimit()).isZero();
		assertThat(read.root().string("name")).isEqualTo("홍길동");
		assertThat(read.root().optionalString("nick")).contains("길동");
		assertThat(read.root().obj("cells").values()).containsOnlyKeys("a", "b");
		assertThat(read.root().obj("cells").obj("b").string("title")).isEqualTo("다라");
		assertThat(read.root().arr("lines").items()).containsExactly(new OutputValue.Str("ab"), new OutputValue.Str(""));
	}

	@Test
	void anOptionalPropertyThatIsMissingNullOrBlankIsAbsent() {
		for (String nick : List.of("", ",\"nick\":null", ",\"nick\":\"  \"")) {
			StructuredRead.Read read = read("{\"name\":\"이름\"" + nick + ",\"cells\":{},\"lines\":[]}");
			assertThat(read.root().values()).as(nick).doesNotContainKey("nick");
			assertThat(read.root().optionalString("nick")).as(nick).isEmpty();
		}
	}

	@Test
	void keyedLeavesOutMissingAndNullKeysAndIgnoresUndescribedKeys() {
		StructuredRead.Read read = read("""
				{"name":"이름","cells":{"b":null,"z":{"title":"모름"}},"extra":1,"lines":[]}""");

		assertThat(read.root().obj("cells").values()).isEmpty();
		assertThat(read.root().values()).doesNotContainKey("extra");
		assertThat(read.overLimit()).isZero();
	}

	@Test
	void keepsTheDescribedOrderOfKeyedValues() {
		StructuredRead.Read read = read("""
				{"name":"이름","cells":{"b":{"title":"둘"},"a":{"title":"하나"}},"lines":[]}""");

		assertThat(read.root().obj("cells").values().keySet()).containsExactly("a", "b");
	}

	@Test
	void dropsTheNearestKeyedValueWhenAStringInsideIsOverTheLimitAndCountsIt() {
		StructuredRead.Read read = read("""
				{"name":"이름","cells":{"a":{"title":"다섯글자다"},"b":{"title":"넷글자다"}},"lines":[]}""");

		assertThat(read.root().obj("cells").values()).containsOnlyKeys("b");
		assertThat(read.overLimit()).isEqualTo(1);
	}

	@Test
	void dropsOnlyTheArrayElementThatIsOverTheLimit() {
		StructuredRead.Read read = read("""
				{"name":"이름","cells":{},"lines":["abc","abcd","x"]}""");

		assertThat(read.root().arr("lines").items()).containsExactly(new OutputValue.Str("abc"), new OutputValue.Str("x"));
		assertThat(read.overLimit()).isEqualTo(1);
	}

	@Test
	void clearsOnlyTheValueOfADropValueStringAndCountsIt() {
		StructuredRead.Read read = read("""
				{"name":"이름","nick":"여섯글자이다","cells":{},"lines":[]}""");

		assertThat(read.root().values()).doesNotContainKey("nick");
		assertThat(read.overLimit()).isEqualTo(1);
	}

	@Test
	void countsADroppedKeyedValueOnceEvenWhenAValueInsideWasAlsoCleared() {
		OutputShape.Obj noted = new OutputShape.Obj(List.of(new OutputShape.Property("cells",
				new OutputShape.Keyed(List.of("a"),
						new OutputShape.Obj(List.of(
								new OutputShape.Property("note",
										new OutputShape.Str(2, OnBlank.ABSENT, OverLimit.DROP_VALUE), false, true),
								new OutputShape.Property("title",
										new OutputShape.Str(2, OnBlank.UNREADABLE, OverLimit.DROP_ITEM), true, false)))),
				true, false)));

		StructuredRead both = StructuredOutputReader.read("{\"cells\":{\"a\":{\"note\":\"넘는말\",\"title\":\"넘는제목\"}}}",
				noted);
		StructuredRead noteOnly = StructuredOutputReader.read("{\"cells\":{\"a\":{\"note\":\"넘는말\",\"title\":\"제목\"}}}",
				noted);

		assertThat(both).isInstanceOf(StructuredRead.Read.class);
		assertThat(((StructuredRead.Read) both).root().obj("cells").values()).isEmpty();
		assertThat(((StructuredRead.Read) both).overLimit()).isEqualTo(1);
		assertThat(noteOnly).isInstanceOf(StructuredRead.Read.class);
		assertThat(((StructuredRead.Read) noteOnly).root().obj("cells").obj("a").values()).containsOnlyKeys("title");
		assertThat(((StructuredRead.Read) noteOnly).overLimit()).isEqualTo(1);
	}

	@Test
	void anUnreadablePropertyBesideADroppedOneFailsTheWholeOutputWhateverTheOrder() {
		OutputShape.Obj pair = new OutputShape.Obj(List.of(new OutputShape.Property("cells",
				new OutputShape.Keyed(List.of("a"),
						new OutputShape.Obj(List.of(
								new OutputShape.Property("title",
										new OutputShape.Str(2, OnBlank.UNREADABLE, OverLimit.DROP_ITEM), true, false),
								new OutputShape.Property("body",
										new OutputShape.Str(2, OnBlank.UNREADABLE, OverLimit.DROP_ITEM), true, false)))),
				true, false)));

		assertThat(StructuredOutputReader.read("{\"cells\":{\"a\":{\"title\":\"넘는제목\",\"body\":\"\"}}}", pair))
			.isEqualTo(new StructuredRead.Unreadable("$.cells.a.body: 비었다"));
		assertThat(StructuredOutputReader.read("{\"cells\":{\"a\":{\"body\":\"\",\"title\":\"넘는제목\"}}}", pair))
			.isEqualTo(new StructuredRead.Unreadable("$.cells.a.body: 비었다"));
	}

	@Test
	void theRootCannotBeDropped() {
		OutputShape.Obj droppable = new OutputShape.Obj(List.of(
				new OutputShape.Property("title", new OutputShape.Str(2, OnBlank.UNREADABLE, OverLimit.DROP_ITEM), true,
						false)));

		assertThat(StructuredOutputReader.read("{\"title\":\"넘는다\"}", droppable))
			.isEqualTo(new StructuredRead.Unreadable("$: 최상위를 버릴 수 없다"));
	}

	@Test
	void unwrapsOneCodeFenceOnly() {
		String body = "{\"name\":\"이름\",\"cells\":{},\"lines\":[]}";

		assertThat(StructuredOutputReader.read("```json\n" + body + "\n```", ROOT)).isInstanceOf(StructuredRead.Read.class);
		assertThat(StructuredOutputReader.read("```\n" + body + "\n```", ROOT)).isInstanceOf(StructuredRead.Read.class);
		assertThat(StructuredOutputReader.read("```json\n```json\n" + body + "\n```\n```", ROOT))
			.isEqualTo(new StructuredRead.Unreadable("JSON 이 아니다"));
	}

	@Test
	void rejectsWhatIsNotOneJsonObject() {
		assertThat(StructuredOutputReader.read("[]", ROOT)).isEqualTo(new StructuredRead.Unreadable("$: 객체가 아니다"));
		assertThat(StructuredOutputReader.read("{\"name\":\"이름\",\"cells\":{},\"lines\":[]} 뒤", ROOT))
			.isEqualTo(new StructuredRead.Unreadable("JSON 이 아니다"));
		assertThat(StructuredOutputReader.read("", ROOT)).isEqualTo(new StructuredRead.Unreadable("JSON 이 아니다"));
	}

	@Test
	void rejectsADuplicateKey() {
		assertThat(StructuredOutputReader.read("{\"name\":\"하나\",\"name\":\"둘\",\"cells\":{},\"lines\":[]}", ROOT))
			.isEqualTo(new StructuredRead.Unreadable("JSON 이 아니다"));
	}

	@Test
	void rejectsMissingRequiredNullWhereNotAllowedAndWrongTypes() {
		assertThat(StructuredOutputReader.read("{\"cells\":{},\"lines\":[]}", ROOT))
			.isEqualTo(new StructuredRead.Unreadable("$.name: 없다"));
		assertThat(StructuredOutputReader.read("{\"name\":null,\"cells\":{},\"lines\":[]}", ROOT))
			.isEqualTo(new StructuredRead.Unreadable("$.name: null 이다"));
		assertThat(StructuredOutputReader.read("{\"name\":1,\"cells\":{},\"lines\":[]}", ROOT))
			.isEqualTo(new StructuredRead.Unreadable("$.name: 문자열이 아니다"));
		assertThat(StructuredOutputReader.read("{\"name\":\"이름\",\"cells\":[],\"lines\":[]}", ROOT))
			.isEqualTo(new StructuredRead.Unreadable("$.cells: 객체가 아니다"));
		assertThat(StructuredOutputReader.read("{\"name\":\"이름\",\"cells\":{},\"lines\":{}}", ROOT))
			.isEqualTo(new StructuredRead.Unreadable("$.lines: 배열이 아니다"));
		assertThat(StructuredOutputReader.read("{\"name\":\"이름\",\"cells\":{},\"lines\":[null]}", ROOT))
			.isEqualTo(new StructuredRead.Unreadable("$.lines[0]: null 이다"));
	}

	@Test
	void anUnreadableOverLimitOrBlankFailsTheWholeOutput() {
		assertThat(StructuredOutputReader.read("{\"name\":\"열한글자가넘는이름이다\",\"cells\":{},\"lines\":[]}", ROOT))
			.isEqualTo(new StructuredRead.Unreadable("$.name: 10 코드 포인트를 넘는다"));
		assertThat(StructuredOutputReader.read("{\"name\":\" \",\"cells\":{},\"lines\":[]}", ROOT))
			.isEqualTo(new StructuredRead.Unreadable("$.name: 비었다"));
	}

	private static StructuredRead.Read read(String text) {
		StructuredRead read = StructuredOutputReader.read(text, ROOT);
		assertThat(read).isInstanceOf(StructuredRead.Read.class);
		return (StructuredRead.Read) read;
	}

}
