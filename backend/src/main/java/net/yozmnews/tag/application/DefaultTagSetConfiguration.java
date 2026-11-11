package net.yozmnews.tag.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import net.yozmnews.tag.domain.tagset.TagSetDefinition;
import net.yozmnews.tag.domain.tagset.TagSetParse;
import net.yozmnews.tag.domain.tagset.TagSetParser;
import net.yozmnews.tag.domain.tagset.TagSetProblem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * 기본 체계 파일의 기동 검증 — docs/specs/tag.md §4 · tag-R1. 기본 체계 파일에는 계보 절을 둘 수 없다.
 * <p>
 * 역할 애너테이션이 없다 — 두 역할 모두 기동 때 검증한다. 기본 체계 파일은 배포물의 일부라 잘못된 파일은 CI 의 테스트가 먼저 잡는다.
 */
@Configuration(proxyBeanMethods = false)
class DefaultTagSetConfiguration {

	static final String LOCATION = "classpath:tag-sets/default.yaml";

	@Bean
	TagSetDefinition defaultTagSet(ResourceLoader resourceLoader) {
		return load(resourceLoader.getResource(LOCATION), new TagSetParser());
	}

	/**
	 * 자원을 읽어 검증한다.
	 * @throws IllegalStateException 자원이 없거나 검증에 걸렸을 때 — 메시지에 문제를 줄:열 · 경로 · 코드와 함께 모두 담는다
	 * @throws UncheckedIOException 자원을 읽지 못했을 때
	 */
	static TagSetDefinition load(Resource resource, TagSetParser parser) {
		if (!resource.exists()) {
			throw new IllegalStateException("기본 태그 체계 파일이 없다: " + resource.getDescription());
		}
		String text;
		try {
			text = resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("기본 태그 체계 파일을 읽지 못했다: " + resource.getDescription(), ex);
		}
		return switch (parser.parse(text)) {
			case TagSetParse.Parsed parsed -> {
				Optional<TagSetProblem> lineage = parsed.definition().lineageProblemForDefault();
				if (lineage.isPresent()) {
					throw failure(resource, List.of(lineage.get()));
				}
				yield parsed.definition();
			}
			case TagSetParse.Invalid invalid -> throw failure(resource, invalid.problems());
		};
	}

	private static IllegalStateException failure(Resource resource, List<TagSetProblem> problems) {
		return new IllegalStateException("기본 태그 체계 파일을 적재하지 못했다 (" + resource.getDescription() + ")\n"
				+ problems.stream().map(problem -> "  " + problem).collect(Collectors.joining("\n")));
	}

}
