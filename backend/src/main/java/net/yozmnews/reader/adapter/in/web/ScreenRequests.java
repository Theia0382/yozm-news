package net.yozmnews.reader.adapter.in.web;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.event.EventPeriod;
import net.yozmnews.storyline.StorylineCursor;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 독자 화면의 요청 매개변수 읽기와 그 400 · 404 — docs/specs/reader.md §5 reader-R1 · R2 · R4 · R6.
 */
final class ScreenRequests {

	private ScreenRequests() {
	}

	/**
	 * 목록 기간 — {@code [until − 길이, until)}.
	 * @param until 기간 끝 — 없으면 지금
	 * @throws ResponseStatusException 400 — 시각 형식이 틀렸거나, 지금보다 뒤거나, 기간을 만들 수 없을 때
	 */
	static EventPeriod period(StorylineWindow window, @Nullable String until, Instant now) {
		Instant end = (until == null) ? now : instant(until);
		if (end.isAfter(now)) {
			throw badRequest("until 은 지금보다 뒤일 수 없다: " + until);
		}
		Instant start;
		try {
			start = end.minus(window.length());
		}
		catch (ArithmeticException | DateTimeException ex) {
			throw badRequest("기간을 만들 수 없다: " + until);
		}
		return EventPeriod.of(start, end).orElseThrow(() -> badRequest("기간을 만들 수 없다: " + until));
	}

	/**
	 * 다음 쪽 커서 — 둘 다 없으면 첫 쪽.
	 * @throws ResponseStatusException 400 — 하나만 오거나, 시각 형식이 틀렸거나, ID 가 1 미만일 때
	 */
	static @Nullable StorylineCursor cursor(@Nullable String afterLastArticleAt, @Nullable Long afterId) {
		if ((afterLastArticleAt == null) != (afterId == null)) {
			throw badRequest("afterLastArticleAt 과 afterId 는 함께 준다");
		}
		if (afterLastArticleAt == null) {
			return null;
		}
		long id = Objects.requireNonNull(afterId);
		if (id < 1) {
			throw badRequest("스토리라인 ID 는 1 이상이다: " + id);
		}
		return new StorylineCursor(instant(afterLastArticleAt), new StorylineId(id));
	}

	/**
	 * 경로의 스토리라인 ID.
	 * @throws ResponseStatusException 404 — 1 미만이면 없는 스토리라인과 같다
	 */
	static StorylineId storyline(long id) {
		if (id < 1) {
			throw unknownStoryline(id);
		}
		return new StorylineId(id);
	}

	/** 없는 스토리라인의 404 */
	static ResponseStatusException unknownStoryline(long id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 스토리라인: " + id);
	}

	/**
	 * 카드로 낼 스토리라인 ID — 받은 순서, 같은 ID 는 처음 자리 한 번.
	 * @throws ResponseStatusException 400 — 받은 값이 1 ~ {@link StorylineDirectory#MAX_PAGE_SIZE} 개(같은 ID 포함)가 아니거나 1 미만
	 *         ID 가 있을 때
	 */
	static List<StorylineId> ids(List<Long> ids) {
		if (ids.isEmpty() || ids.size() > StorylineDirectory.MAX_PAGE_SIZE) {
			throw badRequest("ids 는 1 ~ " + StorylineDirectory.MAX_PAGE_SIZE + "개다: " + ids.size());
		}
		Set<StorylineId> distinct = new LinkedHashSet<>();
		for (Long id : ids) {
			if (id == null || id < 1) {
				throw badRequest("스토리라인 ID 는 1 이상이다: " + id);
			}
			distinct.add(new StorylineId(id));
		}
		return List.copyOf(distinct);
	}

	private static Instant instant(String value) {
		try {
			return Instant.parse(value);
		}
		catch (DateTimeParseException ex) {
			throw badRequest("시각은 ISO-8601 형식이다: " + value);
		}
	}

	private static ResponseStatusException badRequest(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}

}
