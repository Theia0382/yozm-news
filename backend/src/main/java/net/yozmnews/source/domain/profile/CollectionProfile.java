package net.yozmnews.source.domain.profile;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * 검증을 통과한 수집 프로필 — 프리셋이든 운영자 프로필이든 같은 모델이다. 검증기만 만든다(생성자가 패키지 전용). 모든 값은
 * 컴파일된 형태(URI · 로캘 · 형식 · 시간대 · 인코딩)라 쓰는 쪽이 다시 검증하지 않는다.
 */
public final class CollectionProfile {

	private final SourceInfo source;

	private final @Nullable LocalDate termsReviewedAt;

	private final List<FeedSpec> feeds;

	private final @Nullable PollInterval pollInterval;

	private final List<PreCorrection> preCorrections;

	private final Map<ItemField, List<ValueSource>> items;

	private final DateParsing dates;

	CollectionProfile(SourceInfo source, @Nullable LocalDate termsReviewedAt, List<FeedSpec> feeds,
			@Nullable PollInterval pollInterval, List<PreCorrection> preCorrections,
			Map<ItemField, List<ValueSource>> items, DateParsing dates) {
		this.source = Objects.requireNonNull(source, "source");
		this.termsReviewedAt = termsReviewedAt;
		this.feeds = List.copyOf(feeds);
		this.pollInterval = pollInterval;
		this.preCorrections = List.copyOf(preCorrections);
		EnumMap<ItemField, List<ValueSource>> copy = new EnumMap<>(ItemField.class);
		items.forEach((field, sources) -> copy.put(field, List.copyOf(sources)));
		this.items = Collections.unmodifiableMap(copy);
		this.dates = Objects.requireNonNull(dates, "dates");
	}

	public SourceInfo source() {
		return this.source;
	}

	/** 이용조건 검토일. 프리셋에는 늘 있고, 운영자 프로필에는 없을 수 있다 */
	public Optional<LocalDate> termsReviewedAt() {
		return Optional.ofNullable(this.termsReviewedAt);
	}

	/** 피드 — 프로필에 적힌 순서 */
	public List<FeedSpec> feeds() {
		return this.feeds;
	}

	public Optional<FeedSpec> feed(FeedKey key) {
		return this.feeds.stream().filter(feed -> feed.key().equals(key)).findFirst();
	}

	/** 소스의 권장 수집 주기. 없을 수 있다 — 실제 주기는 {@link PollIntervalPolicy} */
	public Optional<PollInterval> pollInterval() {
		return Optional.ofNullable(this.pollInterval);
	}

	/** 사전 보정 — 적용 순서 */
	public List<PreCorrection> preCorrections() {
		return this.preCorrections;
	}

	/** 항목별로 값을 읽을 곳 — 모든 항목이 있고, 각 목록은 시도 순서다. 빈 목록은 그 항목을 읽지 않는다는 뜻이다 */
	public Map<ItemField, List<ValueSource>> items() {
		return this.items;
	}

	public DateParsing dates() {
		return this.dates;
	}

}
