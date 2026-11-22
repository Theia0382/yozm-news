package net.yozmnews.user.domain.summary;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.yozmnews.platform.OutputValue;
import net.yozmnews.platform.StructuredOutputReader;
import net.yozmnews.platform.StructuredRead;

/**
 * 정리 출력 읽기 — {@link SummaryOutputSchema} 의 서술대로 읽고 걸은 값을 글로 옮긴다. docs/specs/user.md §5
 * <p>
 * 꼭대기마다의 글이 하나라도 빠지면 읽지 못한 것이다 — 일부만 저장하지 않는다. 상한을 넘은 글은 서술대로 잘려 읽힌다.
 */
public final class SummaryOutputReader {

	private SummaryOutputReader() {
	}

	/**
	 * @param text 생성기가 돌려준 원문
	 * @param aliases 프롬프트의 꼭대기 별칭
	 */
	public static SummaryRead read(String text, List<String> aliases) {
		Objects.requireNonNull(text, "text");
		return switch (StructuredOutputReader.read(text, SummaryOutputSchema.shape(aliases))) {
			case StructuredRead.Unreadable unreadable -> new SummaryRead.Unreadable(unreadable.reason());
			case StructuredRead.Read read -> moved(read.root(), aliases);
		};
	}

	private static SummaryRead moved(OutputValue.Obj root, List<String> aliases) {
		SummaryText whole = text(root.obj(SummaryOutputSchema.WHOLE));
		if (aliases.size() == 1) {
			return new SummaryRead.Read(whole, List.of(whole));
		}
		OutputValue.Obj keyed = root.obj(SummaryOutputSchema.TOPS);
		List<SummaryText> tops = new ArrayList<>();
		for (String alias : aliases) {
			if (!keyed.values().containsKey(alias)) {
				return new SummaryRead.Unreadable("$." + SummaryOutputSchema.TOPS + "." + alias + ": 없다");
			}
			tops.add(text(keyed.obj(alias)));
		}
		return new SummaryRead.Read(whole, tops);
	}

	private static SummaryText text(OutputValue.Obj value) {
		return new SummaryText(value.string(SummaryOutputSchema.TITLE), value.string(SummaryOutputSchema.SUMMARY),
				value.string(SummaryOutputSchema.RECENT));
	}

}
