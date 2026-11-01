package net.yozmnews.source.domain.profile;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 마크다운의 코드 울타리 블록을 뽑는다.
 * <p>
 * 여는 줄은 앞 공백을 뺀 내용이 백틱 또는 물결을 세 개 이상 이어 시작하는 줄이다 — 목록 안에 들여쓴 울타리도 잡는다. 정보 문자열은 울타리 뒤의 첫
 * 낱말이다. 닫는 줄은 앞 공백을 뺀 내용이 같은 울타리 문자를 여는 줄 이상 이어 쓴 것뿐인 줄이다. 블록 내용은 여는 줄의 들여쓰기만큼 앞 공백을 뺀다. 울타리
 * 없이 들여쓴 코드 블록은 잡지 않는다.
 */
final class CodeFences {

	/**
	 * 코드 블록 하나.
	 *
	 * @param line 여는 줄의 번호(1부터)
	 * @param info 정보 문자열의 첫 낱말 — 없으면 빈 문자열
	 * @param content 블록 내용 — 줄마다 줄바꿈으로 끝난다
	 */
	record Block(int line, String info, String content) {
	}

	private record Opening(int indent, char fence, int length, String info) {
	}

	private CodeFences() {
	}

	/** {@code markdown} 의 코드 블록을 문서 순서로. 닫히지 않은 울타리가 있으면 실패한다. */
	static List<Block> extract(String markdown) {
		String[] lines = markdown.split("\r?\n", -1);
		List<Block> blocks = new ArrayList<>();
		int index = 0;
		while (index < lines.length) {
			Opening opening = opening(lines[index]);
			if (opening == null) {
				index++;
				continue;
			}
			int openingLine = index + 1;
			StringBuilder content = new StringBuilder();
			int cursor = index + 1;
			while (cursor < lines.length && !closes(lines[cursor], opening)) {
				content.append(dedent(lines[cursor], opening.indent())).append('\n');
				cursor++;
			}
			if (cursor == lines.length) {
				throw new IllegalArgumentException(openingLine + "번째 줄의 코드 울타리가 닫히지 않았다");
			}
			blocks.add(new Block(openingLine, opening.info(), content.toString()));
			index = cursor + 1;
		}
		return List.copyOf(blocks);
	}

	private static @Nullable Opening opening(String line) {
		int indent = leadingSpaces(line);
		String rest = line.substring(indent);
		if (rest.isEmpty() || (rest.charAt(0) != '`' && rest.charAt(0) != '~')) {
			return null;
		}
		char fence = rest.charAt(0);
		int length = run(rest, fence);
		if (length < 3) {
			return null;
		}
		String infoString = rest.substring(length).strip();
		String info = infoString.isEmpty() ? "" : infoString.split("\\s+", 2)[0];
		return new Opening(indent, fence, length, info);
	}

	private static boolean closes(String line, Opening opening) {
		String rest = line.substring(leadingSpaces(line));
		int length = run(rest, opening.fence());
		return length >= opening.length() && rest.substring(length).isBlank();
	}

	private static String dedent(String line, int indent) {
		return line.substring(Math.min(indent, leadingSpaces(line)));
	}

	private static int leadingSpaces(String line) {
		return run(line, ' ');
	}

	private static int run(String text, char c) {
		int count = 0;
		while (count < text.length() && text.charAt(count) == c) {
			count++;
		}
		return count;
	}

}
