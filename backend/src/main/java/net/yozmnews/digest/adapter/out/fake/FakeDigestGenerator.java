package net.yozmnews.digest.adapter.out.fake;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import tools.jackson.databind.json.JsonMapper;

import net.yozmnews.digest.domain.candidate.AliasedCandidate;
import net.yozmnews.digest.domain.generator.DigestGenerator;
import net.yozmnews.digest.domain.generator.DigestRequest;
import net.yozmnews.digest.domain.generator.GeneratedOutput;
import net.yozmnews.digest.domain.generator.TokenUsage;
import net.yozmnews.digest.domain.output.DigestOutputSchema;

/**
 * 다이제스트 생성기의 로컬 대체물 — 외부 호출 없이 고정 규칙으로 출력 원문을 만든다 · docs/specs/digest.md §4 · digest-R12
 */
public final class FakeDigestGenerator implements DigestGenerator {

	/** 주요 뉴스로 쓰는 앞 후보 수 — 나머지는 추가 헤드라인 */
	static final int STORIES = 3;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Override
	public GeneratedOutput generate(DigestRequest request) {
		Objects.requireNonNull(request, "request");
		List<AliasedCandidate> entries = request.candidates().entries();
		List<Map<String, Object>> stories = new ArrayList<>();
		List<Map<String, Object>> headlines = new ArrayList<>();
		for (int index = 0; index < entries.size(); index++) {
			String alias = entries.get(index).alias();
			if (index < STORIES) {
				Map<String, Object> story = new LinkedHashMap<>();
				story.put(DigestOutputSchema.TITLE, "로컬 주요 뉴스 " + (index + 1));
				story.put(DigestOutputSchema.SUMMARY, "로컬 대체물이 만든 요약이다.");
				story.put(DigestOutputSchema.REFS, List.of(alias));
				stories.add(story);
			}
			else {
				headlines.add(Map.of(DigestOutputSchema.REF, alias));
			}
		}
		Map<String, Object> output = new LinkedHashMap<>();
		output.put(DigestOutputSchema.OVERVIEW, "로컬 대체물이 만든 다이제스트다. 후보 " + entries.size() + "건.");
		output.put(DigestOutputSchema.TOP_STORIES, stories);
		output.put(DigestOutputSchema.MORE_HEADLINES, headlines);
		return new GeneratedOutput(JSON.writeValueAsString(output), new TokenUsage(0, 0));
	}

}
