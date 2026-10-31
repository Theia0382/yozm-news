package net.yozmnews.source.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;

import net.yozmnews.source.domain.profile.Preset;
import net.yozmnews.source.domain.profile.PresetCatalog;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.profile.ProfileParser;
import net.yozmnews.source.domain.profile.text.ProfileTextReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 프리셋 디렉터리 적재 — 테스트 리소스의 합성 프리셋으로 본다. Spring 컨텍스트는 띄우지 않는다.
 * <p>
 * 지키는 규칙: source-R12
 */
@ExtendWith(OutputCaptureExtension.class)
class PresetCatalogLoadTests {

	private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

	private final ProfileParser parser = new ProfileParser(new ProfileTextReader());

	@Test
	void loadsEveryPresetWithItsFileNameAsTheKey() {
		PresetCatalog catalog = load("classpath*:source-presets-test/valid/*");

		assertThat(catalog.all()).extracting(Preset::key)
			.containsExactly(new PresetKey("alpha"), new PresetKey("beta-news"));
		assertThat(catalog.find(new PresetKey("beta-news"))).get()
			.extracting(Preset::termsReviewedAt)
			.isEqualTo(LocalDate.of(2026, 9, 30));
		assertThat(catalog.find(new PresetKey("gamma"))).isEmpty();
	}

	@Test
	void failsWithEveryProblemOfAnInvalidPreset() {
		assertThatIllegalStateException().isThrownBy(() -> load("classpath*:source-presets-test/invalid/*"))
			.withMessageContaining("broken.yaml")
			.withMessageContaining("termsReviewedAt")
			.withMessageContaining("MISSING_REQUIRED")
			.withMessageContaining("preCorrections[0].rule")
			.withMessageContaining("UNKNOWN_PRE_CORRECTION");
	}

	@Test
	void failsOnAFileThatIsNotYaml() {
		assertThatIllegalStateException().isThrownBy(() -> load("classpath*:source-presets-test/yml/*"))
			.withMessageContaining("wrong.yml");
	}

	@Test
	void failsOnAFileNameOutsideTheKeyForm() {
		assertThatIllegalStateException().isThrownBy(() -> load("classpath*:source-presets-test/badname/*"))
			.withMessageContaining("Bad_Name.yaml");
	}

	@Test
	void loadsAnEmptyCatalogWhereThereAreNoFiles() {
		assertThat(load("classpath*:source-presets-test/none/*").all()).isEmpty();
	}

	@Test
	void loadsTheRepositoryPresets() {
		// 저장소의 프리셋이 모두 검증을 통과해야 기동이 된다. 기대값은 저장소에 둔 프리셋의 키 · 이용조건 검토일 · 피드 수다 —
		// 프리셋을 더하거나 그 검토일 · 피드를 바꾸면 여기도 바꾼다
		assertThat(load(PresetCatalogConfiguration.LOCATION).all())
			.extracting(preset -> preset.key().value(), Preset::termsReviewedAt, preset -> preset.profile().feeds().size())
			.containsExactly(tuple("donga", LocalDate.of(2026, 10, 31), 16), tuple("jtbc", LocalDate.of(2026, 10, 31), 8),
					tuple("kyunghyang", LocalDate.of(2026, 10, 31), 12), tuple("sbs", LocalDate.of(2026, 10, 31), 8));
	}

	@Test
	void logsHowManyPresetsItLoaded(CapturedOutput output) {
		load(PresetCatalogConfiguration.LOCATION);
		// 대조 — 수와 키는 적재한 결과에서 나온다
		load("classpath*:source-presets-test/none/*");

		assertThat(output.getOut()).contains("프리셋 4개를 적재했다: donga, jtbc, kyunghyang, sbs")
			.contains("프리셋 0개를 적재했다: ");
	}

	@Test
	void rejectsTwoPresetsWithTheSameKey() {
		Preset alpha = load("classpath*:source-presets-test/valid/*").find(new PresetKey("alpha")).orElseThrow();

		assertThatIllegalArgumentException().isThrownBy(() -> new PresetCatalog(List.of(alpha, alpha)));
	}

	private PresetCatalog load(String location) {
		return PresetCatalogConfiguration.load(this.resolver, location, this.parser);
	}

}
