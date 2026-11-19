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
 * 요청의 별칭 항목만 쓴다 — 프롬프트 · 모델 · 추론 강도는 보지 않는다. 주요 뉴스 항목마다 제목 · 요약을, 헤드라인 항목마다 제목을 쓰고
 * 코멘트는 쓰지 않는다. 결과는 언제나 끝까지 쓴 응답이다.
 */
public final class FakeDigestGenerator implements DigestGenerator {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Override
	public GenerationOutcome generate(DigestRequest request) {
		Objects.requireNonNull(request, "request");
		List<AliasedItem> items = request.candidates().items();
		List<Map<String, Object>> stories = new ArrayList<>();
		List<Map<String, Object>> headlines = new ArrayList<>();
		for (AliasedItem item : items) {
			switch (item.kind()) {
				case TOP_STORY -> {
					Map<String, Object> story = new LinkedHashMap<>();
					story.put(DigestOutputSchema.REF, item.alias());
					story.put(DigestOutputSchema.TITLE, "로컬 주요 뉴스 " + (stories.size() + 1));
					story.put(DigestOutputSchema.SUMMARY, "로컬 대체물이 만든 요약이다.");
					stories.add(story);
				}
				case HEADLINE -> {
					Map<String, Object> headline = new LinkedHashMap<>();
					headline.put(DigestOutputSchema.REF, item.alias());
					headline.put(DigestOutputSchema.TITLE, "로컬 헤드라인 " + (headlines.size() + 1));
					headlines.add(headline);
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
