package net.yozmnews.report.domain.input;

import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.report.ReportPeriod;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 생성의 입력 — 재료를 모은 사용자 · 리포트 기간 · 사용자 시간대 · 갈래 재료 · 직전 리포트 글 · 통계 스냅숏 · 직전 리포트 · 정본 · docs/specs/report.md §5 ·
 * report-R3
 * <p>
 * 갈래는 재료가 있을 때만 든다(report-R14). 직전 리포트 글은 갈래가 아니다(report-R20). 스냅숏의 꼴은 {@code ReportSnapshot} 이 쓰고
 * (report-R19), 여기서는 JSON 객체 하나인지만 본다.
 *
 * @param user 재료를 모은 사용자 — 생성이 리포트 행의 사용자와 대조한다(report-R13)
 * @param period 리포트 기간 — 생성이 리포트 행의 기간과 대조한다(report-R13)
 * @param zone 사용자 시간대 — 사건 줄의 날짜를 이 시간대로 적었다
 * @param sections 갈래 — 입력 순서(report-R14)
 * @param priors 직전 리포트 글 — 직전 리포트 · 주간 리포트(report-R20)
 * @param statsSnapshot 통계 스냅숏(JSON 객체 하나)
 * @param previous 직전 리포트(report-R18) — 없으면 {@code null}. 그 스냅숏을 읽지 못했어도 직전 리포트가 있으면 있다. 시작할 때 리포트 행에
 * 남긴다
 */
public record ReportInput(UserId user, ReportPeriod period, ZoneId zone, List<InputSection> sections,
		List<PriorReport> priors, String statsSnapshot, @Nullable ReportId previous) {

	private static final JsonMapper JSON = JsonMapper.builder()
		.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
		.build();

	/**
	 * @throws IllegalArgumentException 갈래 종류가 겹치거나 스냅숏이 JSON 객체 하나가 아닐 때
	 */
	public ReportInput {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(zone, "zone");
		sections = List.copyOf(sections);
		priors = List.copyOf(priors);
		Objects.requireNonNull(statsSnapshot, "statsSnapshot");
		Set<ReportSectionKind> kinds = EnumSet.noneOf(ReportSectionKind.class);
		for (InputSection section : sections) {
			if (!kinds.add(section.kind())) {
				throw new IllegalArgumentException("갈래 종류가 겹친다: " + section.kind());
			}
		}
		if (!isObject(statsSnapshot)) {
			throw new IllegalArgumentException("스냅숏이 JSON 객체 하나가 아니다");
		}
	}

	private static boolean isObject(String text) {
		try {
			JsonNode node = JSON.readTree(text);
			return node != null && node.isObject();
		}
		catch (JacksonException ex) {
			return false;
		}
	}

}
