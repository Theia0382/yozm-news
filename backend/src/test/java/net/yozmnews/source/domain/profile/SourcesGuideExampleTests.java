package net.yozmnews.source.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.yozmnews.source.domain.profile.CodeFences.Block;
import net.yozmnews.source.domain.profile.text.ProfileTextReader;
import org.junit.jupiter.api.Test;

/**
 * 운영 안내서 {@code docs/operations/sources.md} 의 예시 프로필이 지금의 검증기를 통과한다 — 예시의 정본은 안내서이고, 형식이 바뀌면 이 테스트가
 * 안내서를 고치라고 실패한다.
 */
class SourcesGuideExampleTests {

	/** 안내서에 쓸 수 있는 코드 블록 — 프로필을 다른 표지로 쓰면 검증을 빠져나가므로 이 밖의 블록을 두지 않는다 */
	private static final Set<String> ALLOWED_INFO = Set.of("yaml", "sh");

	private final ProfileParser parser = new ProfileParser(new ProfileTextReader());

	@Test
	void everyCodeFenceIsYamlOrShell() {
		List<Block> blocks = CodeFences.extract(SourcesGuide.text());
		List<String> outside = blocks.stream()
			.filter((block) -> !ALLOWED_INFO.contains(block.info()))
			.map((block) -> block.line() + "번째 줄: '" + block.info() + "'")
			.toList();

		assertThat(blocks).as("안내서에 코드 블록이 하나 이상 있다").isNotEmpty();
		assertThat(outside).as("안내서의 코드 블록은 yaml · sh 만 쓴다").isEmpty();
	}

	@Test
	void everyYamlBlockIsAValidOperatorProfile() {
		List<Block> profiles = CodeFences.extract(SourcesGuide.text())
			.stream()
			.filter((block) -> block.info().equals("yaml"))
			.toList();
		List<String> invalid = new ArrayList<>();
		for (Block block : profiles) {
			switch (this.parser.parseOperatorProfile(block.content())) {
				case ProfileParse.Parsed parsed -> {
				}
				case ProfileParse.Invalid problems -> invalid.add(block.line() + "번째 줄의 예시: " + problems.problems());
			}
		}

		assertThat(profiles).as("안내서에 yaml 예시가 하나 이상 있다").isNotEmpty();
		assertThat(invalid).as("안내서의 yaml 예시는 운영자 프로필 검증을 통과한다").isEmpty();
	}

}
