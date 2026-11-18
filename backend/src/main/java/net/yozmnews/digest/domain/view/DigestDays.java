package net.yozmnews.digest.domain.view;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.DigestAttempt;
import net.yozmnews.digest.DigestDay;
import net.yozmnews.digest.DigestDayUsage;
import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.DigestSectionView;
import net.yozmnews.digest.DigestStatus;
import net.yozmnews.digest.ShownDigest;
import net.yozmnews.digest.domain.digest.FailureCauses;
import org.jspecify.annotations.Nullable;

/**
 * 날짜 보기를 만드는 규칙 — 정본. 보이는 다이제스트 · 가장 최근 시도 · 그 날짜의 사용량. docs/specs/digest.md §5 · digest-R38
 * <p>
 * 어느 행이 보이는가는 행의 표시({@link DigestDayRow#shown})를 따른다 — 그 판정의 정본은 저장소 구현의 질의(digest-R13)다.
 */
public final class DigestDays {

	private DigestDays() {
	}

	/**
	 * @param rows 그 날짜의 다이제스트 행 전부 — 비어 있지 않다
	 * @param sections 보이는 다이제스트의 섹션 — 보이는 다이제스트가 없으면 쓰지 않는다
	 * @throws IllegalArgumentException 행이 없거나 보이는 행이 둘 이상일 때
	 * @throws IllegalStateException 보이는 행이 {@code READY} 가 아니거나 공급자 · 모델 · 후보 수 · 토큰 · 끝낸 시각이 없을 때
	 */
	public static DigestDay of(LocalDate date, List<DigestDayRow> rows, List<DigestSectionView> sections,
			DigestNeighbors neighbors) {
		Objects.requireNonNull(date, "date");
		Objects.requireNonNull(rows, "rows");
		Objects.requireNonNull(sections, "sections");
		Objects.requireNonNull(neighbors, "neighbors");
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("그 날짜의 다이제스트 행이 없다: " + date);
		}
		@Nullable DigestDayRow shown = shownRow(rows);
		DigestDayRow latest = rows.stream().max(Comparator.comparingLong(DigestDayRow::id)).orElseThrow();
		return new DigestDay(date, neighbors.previous(), neighbors.next(),
				(shown == null) ? null : shownDigest(shown, sections), attempt(latest), usage(rows));
	}

	/**
	 * 그 날짜의 행 가운데 보이는 다이제스트 — 섹션을 읽을 행을 고를 때도 이것을 쓴다.
	 * @return 없으면 null
	 * @throws IllegalArgumentException 보이는 행이 둘 이상일 때
	 */
	public static @Nullable DigestDayRow shownRow(List<DigestDayRow> rows) {
		List<DigestDayRow> shown = rows.stream().filter(DigestDayRow::shown).toList();
		if (shown.size() > 1) {
			throw new IllegalArgumentException("보이는 다이제스트가 둘 이상이다: " + shown);
		}
		return shown.isEmpty() ? null : shown.getFirst();
	}

	private static ShownDigest shownDigest(DigestDayRow row, List<DigestSectionView> sections) {
		if (row.status() != DigestStatus.READY) {
			throw new IllegalStateException("보이는 다이제스트가 READY 가 아니다: " + row.id() + " " + row.status());
		}
		return new ShownDigest(row.id(), row.periodStart(), row.periodEnd(), required(row.provider(), row, "provider"),
				required(row.model(), row, "model"), required(row.candidateCount(), row, "candidateCount"),
				required(row.inputTokens(), row, "inputTokens"), required(row.outputTokens(), row, "outputTokens"),
				required(row.completedAt(), row, "completedAt"), sections);
	}

	private static DigestAttempt attempt(DigestDayRow row) {
		@Nullable DigestFailure failure = row.failure();
		return new DigestAttempt(row.id(), row.status(), failure, (failure == null) ? null : FailureCauses.of(failure),
				row.completedAt());
	}

	/** 시도 수는 행 수, 토큰은 공급자가 답한 행(입력 · 출력 토큰이 함께 있는 행)의 합이다 */
	private static DigestDayUsage usage(List<DigestDayRow> rows) {
		long input = 0;
		long output = 0;
		for (DigestDayRow row : rows) {
			if (row.inputTokens() != null && row.outputTokens() != null) {
				input += row.inputTokens();
				output += row.outputTokens();
			}
		}
		return new DigestDayUsage(rows.size(), input, output);
	}

	private static <T> T required(@Nullable T value, DigestDayRow row, String name) {
		if (value == null) {
			throw new IllegalStateException("보이는 다이제스트에 " + name + " 가 없다: " + row.id());
		}
		return value;
	}

}
