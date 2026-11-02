package net.yozmnews.evaluation.domain.tagset;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.yozmnews.CodeFences.Block;
import net.yozmnews.OperationsGuide;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.domain.tagset.TagSetRevision.Revision;
import net.yozmnews.evaluation.domain.threshold.ThresholdValues;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 운영 안내서 {@code docs/operations/tag-sets.md} 의 예시가 지금의 원문 검증과 기준 버전 대조를 통과한다 — 예시의 정본은 안내서이고, 형식 · 규칙이
 * 바뀌면 이 테스트가 안내서를 고치라고 실패한다.
 * <p>
 * 안내서의 {@code yaml} 블록은 원문의 최상위 절 하나씩이다. 받은 원문은 기본 체계를 내보낸 원문({@link TagSetWriter} — 운영자가
 * 버전 원문 조회로 받는 것)이고, 같은 절이 있으면 그 블록으로 바꾸고 없으면 끝에 더한다. 기준 버전은 기본 체계다.
 */
class TagSetsGuideExampleTests {

	/** 최상위 절의 머리 줄 — 들여쓰지 않은 키 */
	private static final Pattern SECTION = Pattern.compile("^([A-Za-z]+):", Pattern.MULTILINE);

	private final TagSetParser parser = new TagSetParser();

	@Test
	void everyCodeFenceIsYamlOrShell() {
		assertThat(OperationsGuide.TAG_SETS.blocks()).as("안내서에 코드 블록이 하나 이상 있다").isNotEmpty();
		assertThat(OperationsGuide.TAG_SETS.blocksOutsideTheAllowedInfo()).as("안내서의 코드 블록은 yaml · sh 만 쓴다").isEmpty();
	}

	@Test
	void theExampleAppliedToTheDefaultTagSetIsReadyWithLineage() throws IOException {
		TagSetDefinition base = parsed(
				new ClassPathResource("tag-sets/default.yaml").getContentAsString(StandardCharsets.UTF_8));
		String text = TagSetWriter.write(base.content(), thresholds(base));
		List<Block> sections = OperationsGuide.TAG_SETS.yamlBlocks();
		for (Block block : sections) {
			text = applied(text, block);
		}

		Revision revision = TagSetRevision.review(base.content(), thresholds(base), parsed(text));

		assertThat(sections).as("안내서에 yaml 예시가 하나 이상 있다").isNotEmpty();
		assertThat(revision).as("%s", revision).isInstanceOf(Revision.Changed.class);
		assertThat(((Revision.Changed) revision).changes().lineage()).as("예시는 계보를 보인다").isNotEmpty();
	}

	/** 블록이 가진 절을 원문에서 바꾸거나, 원문에 없으면 끝에 더한다 */
	private static String applied(String text, Block block) {
		Matcher head = SECTION.matcher(block.content());
		assertThat(head.lookingAt()).as("%d번째 줄의 예시는 최상위 절 하나로 시작한다", block.line()).isTrue();
		assertThat(SECTION.matcher(block.content()).results().count()).as("%d번째 줄의 예시는 절 하나다", block.line())
			.isEqualTo(1);
		Matcher existing = Pattern.compile("^" + Pattern.quote(head.group(1)) + ":", Pattern.MULTILINE).matcher(text);
		if (!existing.find()) {
			return text + block.content();
		}
		Matcher next = SECTION.matcher(text);
		int end = next.find(existing.end()) ? next.start() : text.length();
		return text.substring(0, existing.start()) + block.content() + text.substring(end);
	}

	private TagSetDefinition parsed(String text) {
		TagSetParse parse = this.parser.parse(text);
		assertThat(parse).as("%s", parse).isInstanceOf(TagSetParse.Parsed.class);
		return ((TagSetParse.Parsed) parse).definition();
	}

	private static TagSetThresholds thresholds(TagSetDefinition definition) {
		return new TagSetThresholds(new TagSetId(1), tagThresholds(definition, MultiTagKind.TOPIC),
				tagThresholds(definition, MultiTagKind.REGION));
	}

	private static TagThresholds tagThresholds(TagSetDefinition definition, MultiTagKind kind) {
		ThresholdValues values = definition.initialThresholds().get(kind);
		return new TagThresholds(kind, values.tagThreshold(), values.storeThreshold());
	}

}
