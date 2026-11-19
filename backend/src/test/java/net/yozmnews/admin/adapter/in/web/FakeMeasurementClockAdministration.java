package net.yozmnews.admin.adapter.in.web;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.platform.ClockAdvanceResult;
import net.yozmnews.platform.MeasurementClockAdministration;
import net.yozmnews.platform.MeasurementClockStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 측정용 시계 공개 운영 API 대역 — 돌려줄 결과를 시험이 정하고, 받은 인자를 남긴다.
 */
class FakeMeasurementClockAdministration implements MeasurementClockAdministration {

	MeasurementClockStatus status = new MeasurementClockStatus.Off();

	ClockAdvanceResult advance = new ClockAdvanceResult.Off();

	final List<Advance> received = new ArrayList<>();

	void reset() {
		this.status = new MeasurementClockStatus.Off();
		this.advance = new ClockAdvanceResult.Off();
		this.received.clear();
	}

	@Override
	public MeasurementClockStatus status() {
		return this.status;
	}

	@Override
	public ClockAdvanceResult advance(@Nullable Duration by, @Nullable Instant to) {
		this.received.add(new Advance(by, to));
		return this.advance;
	}

	/** 받은 넘기기 인자 */
	record Advance(@Nullable Duration by, @Nullable Instant to) {
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeMeasurementClockAdministration fakeMeasurementClockAdministration() {
			return new FakeMeasurementClockAdministration();
		}

	}

}
