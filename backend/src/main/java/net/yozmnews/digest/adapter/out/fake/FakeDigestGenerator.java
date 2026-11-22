package net.yozmnews.digest.adapter.out.fake;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.digest.domain.candidate.AliasedItem;
import net.yozmnews.digest.domain.generator.DigestGenerator;
import net.yozmnews.digest.domain.generator.DigestRequest;
import net.yozmnews.digest.domain.generator.GenerationOutcome;
import net.yozmnews.digest.domain.generator.TokenUsage;
import net.yozmnews.digest.domain.output.DigestOutputSchema;
import net.yozmnews.llm.GenerationStop;
import tools.jackson.databind.json.JsonMapper;

/**
 * 다이제스트 생성기의 로컬 대체물 — 외부 호출 없이 고정 규칙으로 출력 원문을 만든다 · docs/specs/digest.md §4 · digest-R12
 * <p>
 * 요청의 별칭 항목만 쓴다 — 프롬프트 · 모델 · 추론 강도는 보지 않는다. 언제나 모든 항목의 칸을 쓴다 — 주요 뉴스 칸에 주요 뉴스 항목마다 제목 ·
 * 요약을 그 항목 별칭을 키로, 헤드라인 칸에 헤드라인 항목마다 별칭 · 제목의 원소를 쓰고 코멘트는 쓰지 않는다. 결과는 언제나 끝까지 쓴 응답이다.
 */
public final class FakeDigestGenerator implements DigestGenerator {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Override
	public GenerationOutcome generate(DigestRequest request) {
		Objects.requireNonNull(request, "request");
		List<AliasedItem> items = request.candidates().items();
		Map<String, Object> stories = new LinkedHashMap<>();
		List<Object> headlines = new ArrayList<>();
		for (AliasedItem item : items) {
			Map<String, Object> entry = new LinkedHashMap<>();
			switch (item.kind()) {
				case TOP_STORY -> {
					entry.put(DigestOutputSchema.TITLE, "로컬 주요 뉴스 " + (stories.size() + 1));
					entry.put(DigestOutputSchema.SUMMARY, "로컬 대체물이 만든 요약이다.");
					stories.put(item.alias(), entry);
				}
				case HEADLINE -> {
					entry.put(DigestOutputSchema.REF, item.alias());
					entry.put(DigestOutputSchema.TITLE, "로컬 헤드라인 " + (headlines.size() + 1));
					headlines.add(entry);
				}
			}
		}
		Map<String, Object> output = new LinkedHashMap<>();
		output.put(DigestOutputSchema.OVERVIEW, "로컬 대체물이 만든 다이제스트다. 항목 " + items.size() + "개.");
		output.put(DigestOutputSchema.TOP_STORIES, stories);
		output.put(DigestOutputSchema.HEADLINES, headlines);
		return new GenerationOutcome.Generated(JSON.writeValueAsString(output), new TokenUsage(0, 0),
				GenerationStop.COMPLETE);
	}

}
