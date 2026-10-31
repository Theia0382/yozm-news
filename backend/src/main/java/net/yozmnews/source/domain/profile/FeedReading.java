package net.yozmnews.source.domain.profile;

import java.net.URI;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 피드 읽기 설정 — 한 피드를 받아 기사로 만드는 데 쓰이는 프로필 값의 묶음. 무엇이 읽기 설정인지의 정본이다. docs/specs/source.md §2 ·
 * source-R9.
 * <p>
 * 이것이 같으면 활성화 관문이 본 미리보기가 그대로 성립한다. 이름공간은 검증 때 {@code items} 의 요소 이름으로 풀려 있어 따로 두지 않는다.
 *
 * @param url 피드 URL
 * @param language 날짜 텍스트를 읽는 로캘이기도 한 소스 언어
 */
public record FeedReading(URI url, Locale language, List<PreCorrection> preCorrections,
		Map<ItemField, List<ValueSource>> items, DateParsing dates) {

	public FeedReading {
		Objects.requireNonNull(url, "url");
		Objects.requireNonNull(language, "language");
		preCorrections = List.copyOf(preCorrections);
		EnumMap<ItemField, List<ValueSource>> copy = new EnumMap<>(ItemField.class);
		items.forEach((field, sources) -> copy.put(field, List.copyOf(sources)));
		items = Collections.unmodifiableMap(copy);
		Objects.requireNonNull(dates, "dates");
	}

}
