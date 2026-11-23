package net.yozmnews.reader.adapter.in.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import net.yozmnews.digest.DigestItemCursor;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 독자 화면의 요청 매개변수 읽기와 그 400 · 404 — docs/specs/reader.md §5 reader-R4 · R6 · R9 · R10.
 */
final class ScreenRequests {

	/** 다이제스트 날짜의 꼴 — 연도 네 자리 · 월 두 자리 · 일 두 자리 */
	private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	private ScreenRequests() {
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

	/**
	 * 다이제스트 날짜 — ISO-8601 날짜(YYYY-MM-DD). 연도가 네 자리라 DB 의 날짜 범위 안에 든다.
	 * @throws ResponseStatusException 400 — 그 꼴이 아니거나 날짜로 읽을 수 없을 때
	 */
	static LocalDate date(String value) {
		if (!DATE.matcher(value).matches()) {
			throw badRequest("날짜는 ISO-8601 형식(YYYY-MM-DD)이다: " + value);
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException ex) {
			throw badRequest("날짜는 ISO-8601 형식(YYYY-MM-DD)이다: " + value);
		}
	}

	/**
	 * 지난 다이제스트 항목의 다음 쪽 커서 — 두 값은 함께 오거나 함께 없다.
	 * @return 둘 다 없으면 null
	 * @throws ResponseStatusException 400 — 하나만 왔거나 날짜를 읽을 수 없거나 위치가 0 보다 작을 때
	 */
	static @Nullable DigestItemCursor itemCursor(@Nullable String beforeDate, @Nullable Integer beforePosition) {
		if (beforeDate == null && beforePosition == null) {
			return null;
		}
		if (beforeDate == null || beforePosition == null) {
			throw badRequest("커서는 beforeDate 와 beforePosition 을 함께 넘긴다");
		}
		if (beforePosition < 0) {
			throw badRequest("beforePosition 은 0 이상이다: " + beforePosition);
		}
		return new DigestItemCursor(date(beforeDate), beforePosition);
	}

	/** 그 사용자의 정리된 사용자 스토리라인이 없을 때의 404 */
	static ResponseStatusException unknownUserStoryline(long id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 사용자 스토리라인: " + id);
	}

	/** 그 날짜에 다이제스트가 없을 때의 404 */
	static ResponseStatusException unknownDigest(LocalDate date) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "그 날짜의 다이제스트가 없다: " + date);
	}

	private static ResponseStatusException badRequest(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}

}
