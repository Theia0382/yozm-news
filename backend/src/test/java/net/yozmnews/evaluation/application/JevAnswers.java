package net.yozmnews.evaluation.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TopicCategory;
import tools.jackson.databind.json.JsonMapper;

/**
 * 시험 도우미 — 체계의 모든 질문에 답한 Jev 원본 응답을 만든다. 기본 체계는 질문이 50개를 넘어 리터럴로 쓰지 않는다 — 고른 태그의 확률만 넘기고
 * 나머지 주제 · 지역은 {@value #OTHERS} 다. 오라클은 시험이 넘긴 확률 리터럴이다.
 */
final class JevAnswers {

	/** 고르지 않은 주제 · 지역의 확률 — 어느 저장 기준(0.05 이상)에도 저장되지 않는다 */
	static final double OTHERS = 0.01;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private JevAnswers() {
	}

	/** 그 체계의 질문마다 답한 응답 본문 — 주제는 전체 키 · 지역은 키로 확률을 고른다 */
	static String of(TagSetView tagSet, Map<String, Double> topics, Map<String, Double> regions) {
		Map<String, Object> answers = new LinkedHashMap<>();
		for (TopicCategory category : tagSet.topics()) {
			for (AskedTag subtopic : category.subtopics()) {
				answers.put("topic." + subtopic.key(), noul(topics.getOrDefault(subtopic.key(), OTHERS)));
			}
		}
		for (AskedTag region : tagSet.regions()) {
			answers.put("region." + region.key(), noul(regions.getOrDefault(region.key(), OTHERS)));
		}
		List<FormatOption> options = tagSet.format().options();
		Map<String, Double> formats = new LinkedHashMap<>();
		for (int i = 0; i < options.size(); i++) {
			formats.put(options.get(i).key(), (i == 0) ? 0.5 : 0.5 / (options.size() - 1));
		}
		answers.put("format", Map.of("type", "choice", "probabilities", formats));
		Map<String, Double> importance = new LinkedHashMap<>();
		for (int position = 0; position < tagSet.importance().levels().size(); position++) {
			importance.put(Integer.toString(position), (position == 0) ? 1.0 : 0.0);
		}
		answers.put("importance", Map.of("type", "score", "probabilities", importance));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", "jev-1.14.0");
		body.put("answers", answers);
		body.put("usage", Map.of("input_tokens", 3000));
		return JSON.writeValueAsString(body);
	}

	private static Map<String, Object> noul(double probability) {
		return Map.of("type", "noul", "noul", probability);
	}

}
