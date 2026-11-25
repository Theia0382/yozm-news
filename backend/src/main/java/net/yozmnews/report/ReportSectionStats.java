package net.yozmnews.report;

import java.util.List;
import java.util.Objects;

import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;

/**
 * 보이는 갈래 섹션 하나의 숫자 — 생성에 쓴 통계 스냅숏에서 그 갈래의 칸 · docs/specs/report.md §3 · report-R19 · report-R25
 * <p>
 * 숫자는 생성 때 센 값 그대로이고, 사용자 스토리라인 줄의 스토리라인은 생성 때 헤드를 지금 스토리라인으로 푼 것이다 — 합쳐진 헤드는 남는 스토리라인, 지운 헤드의
 * 줄은 없고, 풀린 뒤 같은 스토리라인이 된 줄은 첫 줄만 있다.
 */
public sealed interface ReportSectionStats {

	/**
	 * 추적한 사용자 스토리라인의 보도량 — {@link ReportSectionKind#STORYLINES} 섹션의 숫자
	 * @param userStorylines 사용자 스토리라인마다 — 스냅숏의 순서
	 */
	record UserStorylines(List<UserStorylineCount> userStorylines) implements ReportSectionStats {

		public UserStorylines {
			userStorylines = List.copyOf(userStorylines);
		}

	}

	/**
	 * 사용자 스토리라인 하나의 보도량
	 * @param storyline 생성 때 헤드를 지금 스토리라인으로 푼 것
	 * @param articles 이번 기간 기사 수
	 * @param previous 이은 직전 값 — 없으면 null
	 * @param trackedInPeriod 기간 안에 추적을 더했는가
	 */
	record UserStorylineCount(StorylineId storyline, long articles, @Nullable Long previous, ReportTrend trend,
			boolean trackedInPeriod) {

		public UserStorylineCount {
			Objects.requireNonNull(storyline, "storyline");
			Objects.requireNonNull(trend, "trend");
		}

	}

	/**
	 * 구독 주제의 보도량 — {@link ReportSectionKind#COVERAGE} 섹션의 숫자
	 * @param topics 구독 행마다 — 스냅숏의 순서
	 */
	record Coverage(List<TopicCount> topics) implements ReportSectionStats {

		public Coverage {
			topics = List.copyOf(topics);
		}

	}

	/**
	 * 구독 주제 하나의 보도량
	 * @param name 생성 때의 주제 이름
	 * @param articles 이번 기간 기사 수
	 * @param previous 이은 직전 값 — 없으면 null
	 */
	record TopicCount(String name, long articles, @Nullable Long previous, ReportTrend trend) {

		public TopicCount {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(trend, "trend");
		}

	}

	/**
	 * 관심의 변화 — {@link ReportSectionKind#INTERESTS} 섹션의 숫자
	 * @param topics 주제 줄 — 스냅숏의 순서
	 * @param userStorylines 사용자 스토리라인 줄 — 스냅숏의 순서
	 */
	record Interests(List<TopicLevel> topics, List<UserStorylineLevel> userStorylines) implements ReportSectionStats {

		public Interests {
			topics = List.copyOf(topics);
			userStorylines = List.copyOf(userStorylines);
		}

	}

	/**
	 * 주제 하나의 관심
	 * @param name 생성 때의 주제 이름
	 * @param start 기간 시작의 관심 값 — 신호가 없으면 null
	 * @param end 기간 끝의 관심 값 — 신호가 없으면 null
	 */
	record TopicLevel(String name, @Nullable Double start, @Nullable Double end, InterestShift shift) {

		public TopicLevel {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(shift, "shift");
		}

	}

	/**
	 * 사용자 스토리라인 하나의 관심
	 * @param storyline 생성 때 헤드를 지금 스토리라인으로 푼 것
	 * @param start 기간 시작의 관심 값 — 신호가 없으면 null
	 * @param end 기간 끝의 관심 값 — 신호가 없으면 null
	 */
	record UserStorylineLevel(StorylineId storyline, @Nullable Double start, @Nullable Double end, InterestShift shift) {

		public UserStorylineLevel {
			Objects.requireNonNull(storyline, "storyline");
			Objects.requireNonNull(shift, "shift");
		}

	}

}
