package net.yozmnews.reader.adapter.in.web;

import java.time.Duration;

/**
 * 스토리라인 목록의 기간 — 기간 끝에서 거슬러 잰 길이의 정본. docs/specs/reader.md §5 reader-R1.
 */
enum StorylineWindow {

	/** 최근 24시간 */
	DAY(1),

	/** 최근 7일 */
	WEEK(7),

	/** 최근 30일 */
	MONTH(30);

	private final int days;

	StorylineWindow(int days) {
		this.days = days;
	}

	/** 기간 길이 — 일수 × 24시간 */
	Duration length() {
		return Duration.ofHours(24L * this.days);
	}

}
