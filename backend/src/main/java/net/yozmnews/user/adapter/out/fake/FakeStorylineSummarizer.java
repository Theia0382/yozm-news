package net.yozmnews.user.adapter.out.fake;

import java.util.List;
import java.util.Objects;

import net.yozmnews.llm.GenerationStop;
import net.yozmnews.user.domain.summary.StorylineSummarizer;
import net.yozmnews.user.domain.summary.SummaryEvent;
import net.yozmnews.user.domain.summary.SummaryInput;
import net.yozmnews.user.domain.summary.SummaryOutcome;
import net.yozmnews.user.domain.summary.SummaryOutputSchema;
import net.yozmnews.user.domain.summary.SummaryRequest;
import net.yozmnews.user.domain.summary.SummaryRules;
import net.yozmnews.user.domain.summary.SummaryText;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 정리 생성기의 로컬 대체물 — 공급자를 부르지 않고 재료에서 고정 규칙으로 출력을 만든다. profile {@code local} 에서 사용자의 키와 무관하게
 * 생성기다. docs/specs/user.md §5
 * <p>
 * 규칙: 글 한 벌의 제목은 그 범위에서 가장 늦은 사건의 대표 기사 제목(상한까지), 흐름 요약은 사건 수를 담은 고정 문장, 최근 전개는 가장 늦은
 * 사건의 제목을 담은 고정 문장이다. 지난 정리를 고치는 호출에서 새 사건이 없는 범위는 지난 글을 그대로 쓴다(지난 글이 없던
 * 꼭대기는 그 꼭대기의 새 사건으로 쓴다). 결과는 언제나 끝까지 쓴 응답이고
 * 토큰은 사용자 구획 · 출력의 코드 포인트 수다.
 */
public final class FakeStorylineSummarizer implements StorylineSummarizer {

	/** 제목의 머리 — 로컬 대체물이 만든 글임을 드러낸다 */
	public static final String MARK = "[로컬 대체물] ";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Override
	public SummaryOutcome summarize(SummaryRequest request) {
		Objects.requireNonNull(request, "request");
		SummaryInput input = request.input();
		List<Long> tops = input.topIds();
		List<String> aliases = request.prompt().aliases();
		ObjectNode root = JSON.createObjectNode();
		List<SummaryEvent> all = input.tops().values().stream().flatMap(List::stream).distinct().toList();
		boolean extending = input.previousWhole() != null;
		root.set(SummaryOutputSchema.WHOLE, node(text(all, input.previousWhole(), extending)));
		if (tops.size() > 1) {
			ObjectNode keyed = root.putObject(SummaryOutputSchema.TOPS);
			for (int i = 0; i < tops.size(); i++) {
				keyed.set(aliases.get(i), node(
						text(input.tops().get(tops.get(i)), input.previousTops().get(tops.get(i)), extending)));
			}
		}
		String text = JSON.writeValueAsString(root);
		return new SummaryOutcome.Generated(text, codePoints(request.prompt().userContent()), codePoints(text),
				GenerationStop.COMPLETE);
	}

	private static SummaryText text(List<SummaryEvent> events, @Nullable SummaryText previous, boolean extending) {
		if (events.isEmpty()) {
			// 넣을 사건이 없는 범위는 지난 글이 있다 — 재료의 불변식
			return Objects.requireNonNull(previous, "previous");
		}
		SummaryEvent latest = events.stream()
			.max((a, b) -> (a.firstArticleAt().equals(b.firstArticleAt())) ? Long.compare(a.eventId(), b.eventId())
					: a.firstArticleAt().compareTo(b.firstArticleAt()))
			.orElseThrow();
		String counted = (!extending) ? "사건 " + events.size() + "건을 정리했다."
				: "지난 정리에 새 사건 " + events.size() + "건을 더했다.";
		return new SummaryText(cut(MARK + latest.title(), SummaryRules.MAX_TITLE), counted,
				cut("가장 최근 사건: " + latest.title(), SummaryRules.MAX_RECENT));
	}

	private static ObjectNode node(SummaryText text) {
		ObjectNode node = JSON.createObjectNode();
		node.put(SummaryOutputSchema.TITLE, text.title());
		node.put(SummaryOutputSchema.SUMMARY, text.summary());
		node.put(SummaryOutputSchema.RECENT, text.recent());
		return node;
	}

	private static String cut(String value, int maxCodePoints) {
		if (value.codePointCount(0, value.length()) <= maxCodePoints) {
			return value;
		}
		return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
	}

	private static int codePoints(String value) {
		return value.codePointCount(0, value.length());
	}

}
