package net.yozmnews.evaluation.domain.threshold;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.ThresholdChangeView;
import net.yozmnews.evaluation.domain.queue.EvaluationSchedule;
import org.jspecify.annotations.Nullable;

/**
 * 저장 바닥 계산 — 정본 · docs/specs/evaluation.md §4 · evaluation-R12 · evaluation-R29 · evaluation-R34.
 * <p>
 * 저장 바닥은 한 버전 · 한 태그 종류의 평가 시각 축 위에서, 그 시각의 평가에 어느 확률까지 저장돼 있는가다. 평가마다 쓴 저장 기준은 기록되지
 * 않아 이력과 끝난 채움 기록으로 되짚는다. 이력 행 i 의 저장 기준은 닫힌 구간 {@code [c_i, c_{i+1}]} 동안 유효하다(마지막은 끝없이, 다음
 * 행의 시각이 앞서면 {@code c_i} 한 순간) — 바뀐 그 순간의 평가는 두 값 어느 쪽으로도 읽었을 수 있다. 회차는 기준을 시작 때 읽고 평가
 * 시각은 결과 뒤에 찍으므로, 평가 시각 t 의 저장 기준은 {@code [t − 읽기 지연, t]} 와 겹치는 행의 값 가운데 가장 큰 것으로 본다. 바닥은 그
 * 값과 t 를 덮은 채움의 저장 기준 가운데 작은 것이다. 모든 판단은 바닥을 크게 잡는 쪽(경고를 놓치지 않는 쪽)이다.
 * <p>
 * 바닥은 경계(바뀐 시각 · 바뀐 시각 + 읽기 지연 · 덮은 범위의 양 끝) 사이에서 상수라, 경계와 경계 사이 한 점의 값으로 구간 전체를 판단한다.
 */
public final class StoreFloors {

	/** 경계 뒤 끝없는 구간을 보는 점 — 마지막 경계 뒤의 아무 시각이나 같다 */
	private static final Duration AFTER_LAST = Duration.ofSeconds(1);

	private final List<Row> rows;

	private final List<StoreCoverage> coverages;

	private final Duration readLag;

	private final Instant origin;

	private StoreFloors(List<Row> rows, List<StoreCoverage> coverages, Duration readLag) {
		this.rows = rows;
		this.coverages = coverages;
		this.readLag = readLag;
		this.origin = rows.stream().map(Row::start).min(Instant::compareTo).orElseThrow();
	}

	/**
	 * 평가의 기준 읽기 지연을 회차의 점유 기간({@link EvaluationSchedule#LEASE})으로 잡는다 — 프로덕션 소비자는 모두 이것을 부른다.
	 * @see #of(MultiTagKind, List, List, Duration)
	 */
	public static StoreFloors of(MultiTagKind kind, List<ThresholdChangeView> changes, List<StoreCoverage> coverages) {
		return of(kind, changes, coverages, EvaluationSchedule.LEASE);
	}

	/**
	 * 이력과 덮음으로 저장 바닥을 만든다.
	 * @param changes 그 버전의 태그 기준 · 저장 기준 이력 — 저장소 순서(최근 것부터) 그대로이고, 같은 시각 행의 앞뒤는 이 순서가 정한다.
	 * 다른 태그 종류는 거른다
	 * @param coverages 그 버전 · 그 태그 종류의 끝난 채움 — 건너뛴 평가가 있는 버전의 것은 넣지 않는다
	 * @param readLag 평가가 쓴 저장 기준을 읽은 시각이 평가 시각보다 앞설 수 있는 최대 시간
	 * @throws IllegalStateException 그 태그 종류의 가장 오래된 행이 처음 값 행(이전 값 없음)이 아닐 때 — 처음 값이 첫 행이라는 불변식이
	 * 깨진 것이다
	 */
	public static StoreFloors of(MultiTagKind kind, List<ThresholdChangeView> changes, List<StoreCoverage> coverages,
			Duration readLag) {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(readLag, "readLag");
		List<ThresholdChangeView> chronological = new ArrayList<>();
		for (ThresholdChangeView change : changes) {
			if (change.kind() == kind) {
				chronological.addFirst(change);
			}
		}
		if (chronological.isEmpty() || chronological.getFirst().previousStoreThreshold() != null) {
			throw new IllegalStateException("태그 기준 · 저장 기준의 이력은 처음 값 행으로 시작해야 한다: " + kind);
		}
		List<Row> rows = new ArrayList<>();
		for (int i = 0; i < chronological.size(); i++) {
			Instant start = chronological.get(i).changedAt();
			// 다음 행의 시각이 앞서도(프로세스마다 시계가 다를 때) 그 행이 유효했던 순간 하나는 남긴다
			Instant end = (i + 1 < chronological.size()) ? latest(start, chronological.get(i + 1).changedAt()) : null;
			rows.add(new Row(start, end, chronological.get(i).storeThreshold()));
		}
		return new StoreFloors(List.copyOf(rows), List.copyOf(coverages), readLag);
	}

	/** 지난 저장 기준 최댓값을 낸다 — 처음 값 시각부터 끝없이 본 바닥의 최댓값이고, 채움이 없으면 이력의 저장 기준 최댓값과 같다 */
	public BigDecimal pastMax() {
		return floorsAt(this.origin, null).last();
	}

	/** 처음 값 시각부터 끝없이 본 바닥이 둘 이상인지 판단한다 — 버전 전체에서 평가마다 저장된 범위가 다를 수 있는가 */
	public boolean mixed() {
		return floorsAt(this.origin, null).size() > 1;
	}

	/** 평가 시각 {@code [from, to)} 에서 바닥이 둘 이상인지 판단한다 — 처음 값 시각 전은 보지 않는다 */
	public boolean mixed(Instant from, Instant to) {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		Instant start = latest(from, this.origin);
		if (!start.isBefore(to)) {
			return false;
		}
		return floorsAt(start, to).size() > 1;
	}

	/** {@code [start, end)} 의 표본점마다의 바닥 — 크기로 견줘 같은 값(scale 만 다른 값 포함)은 하나다. end 가 없으면 끝없이 */
	private TreeSet<BigDecimal> floorsAt(Instant start, @Nullable Instant end) {
		TreeSet<BigDecimal> floors = new TreeSet<>();
		for (Instant at : samples(start, end)) {
			floors.add(floor(at));
		}
		return floors;
	}

	/** 평가 시각 at 의 바닥 — at 은 처음 값 시각 이후다 */
	private BigDecimal floor(Instant at) {
		Instant readFrom = at.minus(this.readLag);
		BigDecimal stored = null;
		for (Row row : this.rows) {
			boolean overlaps = !row.start().isAfter(at) && (row.end() == null || !row.end().isBefore(readFrom));
			if (overlaps && (stored == null || row.storeThreshold().compareTo(stored) > 0)) {
				stored = row.storeThreshold();
			}
		}
		BigDecimal floor = Objects.requireNonNull(stored, "처음 값 시각 이후의 시각은 어느 행과 겹친다");
		for (StoreCoverage coverage : this.coverages) {
			if (coverage.covers(at) && coverage.storeThreshold().compareTo(floor) < 0) {
				floor = coverage.storeThreshold();
			}
		}
		return floor;
	}

	/** [start, end) 의 경계와 경계 사이 한 점씩 — end 가 없으면 마지막 경계 뒤 한 점 */
	private List<Instant> samples(Instant start, @Nullable Instant end) {
		TreeSet<Instant> points = new TreeSet<>();
		points.add(start);
		for (Instant boundary : boundaries()) {
			if (boundary.isAfter(start) && (end == null || boundary.isBefore(end))) {
				points.add(boundary);
			}
		}
		List<Instant> samples = new ArrayList<>();
		Instant previous = null;
		for (Instant point : points) {
			if (previous != null) {
				samples.add(middle(previous, point));
			}
			samples.add(point);
			previous = point;
		}
		samples.add((end == null) ? points.last().plus(AFTER_LAST) : middle(points.last(), end));
		return samples;
	}

	private List<Instant> boundaries() {
		List<Instant> boundaries = new ArrayList<>();
		for (Row row : this.rows) {
			boundaries.add(row.start());
			if (row.end() != null) {
				boundaries.add(row.end().plus(this.readLag));
			}
		}
		for (StoreCoverage coverage : this.coverages) {
			boundaries.add(coverage.from());
			boundaries.add(coverage.to());
		}
		return boundaries;
	}

	private static Instant middle(Instant from, Instant to) {
		return from.plus(Duration.between(from, to).dividedBy(2));
	}

	private static Instant latest(Instant a, Instant b) {
		return a.isAfter(b) ? a : b;
	}

	/** 이력 행 하나 — 저장 기준이 [start, end] 동안 유효하다(end 가 없으면 끝없이) */
	private record Row(Instant start, @Nullable Instant end, BigDecimal storeThreshold) {
	}

}
