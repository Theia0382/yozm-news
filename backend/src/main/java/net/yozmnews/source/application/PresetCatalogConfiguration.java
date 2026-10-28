package net.yozmnews.source.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import net.yozmnews.source.domain.profile.Preset;
import net.yozmnews.source.domain.profile.PresetCatalog;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.profile.PresetParse;
import net.yozmnews.source.domain.profile.ProfileParser;
import net.yozmnews.source.domain.profile.ProfileProblem;
import net.yozmnews.source.domain.profile.text.ProfileTextReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;

/**
 * 저장소의 프리셋 파일({@code source-presets/{key}.yaml})을 기동 때 모두 읽어 {@link PresetCatalog} 로 둔다. 하나라도 검증에
 * 실패하거나, 디렉터리에 {@code .yaml} 이 아닌 파일 · 키 형식이 아닌 이름이 있으면 기동을 멈춘다 — 프리셋은 배포물의 일부라 CI 의
 * 테스트가 먼저 잡는다.
 */
@Configuration(proxyBeanMethods = false)
class PresetCatalogConfiguration {

	static final String LOCATION = "classpath*:source-presets/*";

	private static final String EXTENSION = ".yaml";

	/** 프리셋 파일과 운영자 프로필을 같은 읽기기 · 검증기로 읽는다 */
	@Bean
	ProfileParser profileParser() {
		return new ProfileParser(new ProfileTextReader());
	}

	@Bean
	PresetCatalog presetCatalog(ResourceLoader resourceLoader, ProfileParser profileParser) {
		return load(ResourcePatternUtils.getResourcePatternResolver(resourceLoader), LOCATION, profileParser);
	}

	/**
	 * @throws IllegalStateException 적재에 실패한 파일이 하나라도 있을 때 — 메시지에 파일마다 문제를 모두 담는다
	 * @throws IllegalArgumentException 여러 classpath 위치에 같은 이름의 프리셋 파일이 있을 때
	 * @throws UncheckedIOException 위치나 파일을 읽지 못했을 때
	 */
	static PresetCatalog load(ResourcePatternResolver resolver, String location, ProfileParser parser) {
		List<Preset> presets = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		for (Resource resource : resources(resolver, location)) {
			String filename = Objects.requireNonNullElse(resource.getFilename(), resource.getDescription());
			if (!filename.endsWith(EXTENSION) || !resource.isReadable()) {
				failures.add(filename + ": 프리셋 디렉터리에는 {key}" + EXTENSION + " 파일만 둔다");
				continue;
			}
			PresetKey key;
			try {
				key = new PresetKey(filename.substring(0, filename.length() - EXTENSION.length()));
			}
			catch (IllegalArgumentException ex) {
				failures.add(filename + ": 파일 이름이 프리셋 키 형식이 아니다(소문자 · 숫자 · 하이픈)");
				continue;
			}
			switch (Preset.parse(key, read(resource), parser)) {
				case PresetParse.Parsed parsed -> presets.add(parsed.preset());
				case PresetParse.Invalid invalid -> failures.add(filename + ":\n" + describe(invalid.problems()));
			}
		}
		if (!failures.isEmpty()) {
			throw new IllegalStateException("프리셋을 적재하지 못했다 (" + location + ")\n" + String.join("\n", failures));
		}
		return new PresetCatalog(presets);
	}

	private static List<Resource> resources(ResourcePatternResolver resolver, String location) {
		try {
			return Arrays.stream(resolver.getResources(location))
				.sorted(Comparator.comparing(resource -> Objects.requireNonNullElse(resource.getFilename(), "")))
				.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("프리셋 위치를 읽지 못했다: " + location, ex);
		}
	}

	private static String read(Resource resource) {
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("프리셋 파일을 읽지 못했다: " + resource.getDescription(), ex);
		}
	}

	private static String describe(List<ProfileProblem> problems) {
		return String.join("\n", problems.stream().map(problem -> "  " + problem).toList());
	}

}
