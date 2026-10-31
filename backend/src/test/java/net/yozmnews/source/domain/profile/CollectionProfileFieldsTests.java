package net.yozmnews.source.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * 프로필 모델의 값마다 피드 읽기 설정인지가 정해져 있다 — 값이 늘면 이 표를 고치기 전까지 실패한다(누락은 크게 실패).
 * <p>
 * 지키는 규칙: source-R9 · source-R50
 */
class CollectionProfileFieldsTests {

	/** 프로필 모델의 값 → 분류. 읽기 설정은 {@link FeedReading} 의 구성 요소와 같아야 한다 */
	private static final Map<String, String> CLASSIFIED = Map.ofEntries(
			Map.entry("CollectionProfile.source", "묶음"), Map.entry("CollectionProfile.termsReviewedAt", "운영"),
			Map.entry("CollectionProfile.feeds", "묶음"), Map.entry("CollectionProfile.pollInterval", "운영"),
			Map.entry("CollectionProfile.preCorrections", "읽기:preCorrections"),
			Map.entry("CollectionProfile.items", "읽기:items"), Map.entry("CollectionProfile.dates", "읽기:dates"),
			Map.entry("SourceInfo.name", "표시"), Map.entry("SourceInfo.homepage", "표시"),
			Map.entry("SourceInfo.language", "읽기:language"), Map.entry("FeedSpec.key", "식별"),
			Map.entry("FeedSpec.url", "읽기:url"), Map.entry("FeedSpec.section", "표시"),
			Map.entry("FeedSpec.pollInterval", "운영"));

	@Test
	void everyValueOfTheProfileModelIsClassified() {
		Map<String, String> declared = new TreeMap<>();
		for (Field field : CollectionProfile.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers())) {
				declared.put("CollectionProfile." + field.getName(), "");
			}
		}
		for (Class<?> type : new Class<?>[] { SourceInfo.class, FeedSpec.class }) {
			for (RecordComponent component : type.getRecordComponents()) {
				declared.put(type.getSimpleName() + "." + component.getName(), "");
			}
		}

		assertThat(declared.keySet()).isEqualTo(new TreeMap<>(CLASSIFIED).keySet());
	}

	@Test
	void theReadingValuesAreExactlyTheFeedReadingComponents() {
		Set<String> reading = CLASSIFIED.values()
			.stream()
			.filter(kind -> kind.startsWith("읽기:"))
			.map(kind -> kind.substring("읽기:".length()))
			.collect(Collectors.toSet());

		assertThat(Arrays.stream(FeedReading.class.getRecordComponents()).map(RecordComponent::getName))
			.containsExactlyInAnyOrderElementsOf(reading);
	}

}
