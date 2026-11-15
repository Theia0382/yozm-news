package net.yozmnews.llm.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import net.yozmnews.llm.domain.catalog.ModelCatalog;
import net.yozmnews.llm.domain.catalog.ModelCatalogParse;
import net.yozmnews.llm.domain.catalog.ModelCatalogParser;
import net.yozmnews.llm.domain.catalog.ModelCatalogProblem;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * 허용 모델 목록의 기동 적재 — docs/specs/llm.md §4 · llm-R3.
 * <p>
 * 운영자 파일을 가리켰으면 그 파일이, 아니면 앱에 든 기본 파일이 목록 전체다. 읽지 못하거나 형식이 틀리면 기동하지 않는다 — 기본 목록으로
 * 떨어지지 않는다. 역할 애너테이션이 없다(두 역할 공통).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmProperties.class)
class ModelCatalogConfiguration {

	/** 앱에 든 기본 목록 */
	static final String DEFAULT_LOCATION = "classpath:llm-models/default.yaml";

	private static final String PROPERTY = LlmProperties.PREFIX + ".models-file";

	private static final String VARIABLE = "YOZMNEWS_LLM_MODELS_FILE";

	@Bean
	ModelCatalog modelCatalog(LlmProperties properties, ResourceLoader resourceLoader) {
		return load(properties.modelsFile(), resourceLoader.getResource(DEFAULT_LOCATION), new ModelCatalogParser());
	}

	/**
	 * 목록을 읽어 검증한다.
	 * @param modelsFile 운영자 파일 경로 — null 이면 기본 파일
	 * @throws IllegalStateException 경로가 비었거나, 파일이 없거나 읽지 못했거나, 형식이 틀렸을 때 — 문구에 어느 파일인지와 문제를 모두 담는다
	 */
	static ModelCatalog load(@Nullable String modelsFile, Resource defaults, ModelCatalogParser parser) {
		if (modelsFile == null) {
			return parse(defaults, "기본 허용 모델 목록 파일(" + defaults.getDescription() + ")", parser);
		}
		String source = PROPERTY + "(환경 변수 " + VARIABLE + ")";
		if (modelsFile.isBlank()) {
			throw new IllegalStateException(source + " 가 비었다 — 운영자 파일을 쓰지 않으면 줄을 지운다");
		}
		return parse(new FileSystemResource(modelsFile), source + " 가 가리키는 허용 모델 목록 파일(" + modelsFile + ")", parser);
	}

	private static ModelCatalog parse(Resource resource, String what, ModelCatalogParser parser) {
		if (!resource.isReadable()) {
			throw new IllegalStateException(what + "이 없거나 읽을 수 없다");
		}
		String text;
		try {
			text = resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException(what + "을 읽지 못했다 — " + ex.getClass().getSimpleName(), ex);
		}
		return switch (parser.parse(text)) {
			case ModelCatalogParse.Parsed parsed -> parsed.catalog();
			case ModelCatalogParse.Invalid invalid -> throw invalid(what, invalid.problems());
		};
	}

	private static IllegalStateException invalid(String what, List<ModelCatalogProblem> problems) {
		return new IllegalStateException(what + "의 형식이 틀렸다\n"
				+ problems.stream().map(problem -> "  " + problem).collect(Collectors.joining("\n")));
	}

}
