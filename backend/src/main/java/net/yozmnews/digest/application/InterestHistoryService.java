package net.yozmnews.digest.application;

import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.digest.InterestHistory;
import net.yozmnews.digest.InterestHistorySpan;
import net.yozmnews.digest.InterestPeriodView;
import net.yozmnews.digest.InterestPoint;
import net.yozmnews.digest.InterestTimeline;
import net.yozmnews.digest.domain.interest.InterestPeriod;
import net.yozmnews.digest.domain.interest.InterestPeriods;
import net.yozmnews.digest.domain.interest.InterestRecord;
import net.yozmnews.digest.domain.interest.InterestTimelines;
import net.yozmnews.digest.domain.interest.InterestWindow;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.UserTimezones;
import org.springframework.stereotype.Service;

/**
 * {@link InterestHistory} 의 구현 — 계정 유무와 시간대({@code user} 의 {@link UserTimezones}) · 활성 체계 버전({@code tag}) · 관심도 재료
 * ({@link InterestMaterials})를 차례로 읽고, 기간 경계({@link InterestPeriods})와 기준 시각마다의 관심 · 변화({@link InterestTimelines})는
 * {@code domain} 이 정한다. 재료는 첫 기준 시각의 창 시작부터 마지막 기준 시각까지 한 번 읽는다. 트랜잭션 없음, 역할 애너테이션 없음.
 * docs/specs/digest.md §4 · digest-R34 ~ digest-R37
 */
@Service
class InterestHistoryService implements InterestHistory {

	private final UserTimezones timezones;

	private final TagSetDirectory tagSets;

	private final InterestMaterials materials;

	private final InstantSource clock;

	InterestHistoryService(UserTimezones timezones, TagSetDirectory tagSets, InterestMaterials materials,
			InstantSource clock) {
		this.timezones = Objects.requireNonNull(timezones, "timezones");
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.materials = Objects.requireNonNull(materials, "materials");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public Optional<InterestTimeline> recent(UserId user, InterestHistorySpan span) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(span, "span");
		Optional<ZoneId> zone = this.timezones.timezoneOf(user);
		if (zone.isEmpty()) {
			return Optional.empty();
		}
		List<InterestPeriod> periods = InterestPeriods.recent(zone.get(), span, this.clock.instant());
		Computed computed = compute(user, periods.stream().map(InterestPeriod::reference).toList());
		List<InterestPeriodView> views = new ArrayList<>();
		for (int i = 0; i < computed.points().size(); i++) {
			// 기간 0 은 견줌용 — 점 i 는 기간 i + 1 의 것
			InterestPeriod period = periods.get(i + 1);
			views.add(new InterestPeriodView(period.firstDay(), period.lastDay(), period.inProgress(),
					computed.points().get(i)));
		}
		return Optional.of(new InterestTimeline(computed.tagSet(), zone.get(), span, views,
				InterestTimelines.prominent(computed.points())));
	}

	@Override
	public Optional<List<InterestPoint>> at(UserId user, List<Instant> references) {
		Objects.requireNonNull(user, "user");
		InterestTimelines.requireReferences(references);
		if (this.timezones.timezoneOf(user).isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(compute(user, List.copyOf(references)).points());
	}

	/** 기준 시각마다의 관심 — 재료의 키 버전이 활성 버전과 어긋나면 처음부터 다시 한다 */
	private Computed compute(UserId user, List<Instant> references) {
		Instant from = InterestWindow.start(references.get(0));
		Instant to = references.get(references.size() - 1);
		for (int attempt = 0; attempt < ActiveEvaluations.ATTEMPTS; attempt++) {
			TagSetId active = this.tagSets.activeTagSet().id();
			Optional<InterestRecord> record = this.materials.read(user, active, from, to);
			if (record.isPresent()) {
				return new Computed(active, InterestTimelines.points(record.get(), references));
			}
		}
		throw new IllegalStateException(
				"관심도 재료의 태그 체계 활성 버전이 " + ActiveEvaluations.ATTEMPTS + " 번 어긋났다");
	}

	private record Computed(TagSetId tagSet, List<InterestPoint> points) {
	}

}
