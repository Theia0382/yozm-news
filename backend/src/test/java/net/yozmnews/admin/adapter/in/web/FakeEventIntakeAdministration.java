package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.FakeResults.given;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.event.EventIntakeAdministration;
import net.yozmnews.event.IntakeFillResult;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 사건 묶기 채우기 운영 API 대역 — 다음에 돌려줄 결과를 시험이 정하고, 받은 호출을 순서대로 남긴다.
 */
class FakeEventIntakeAdministration implements EventIntakeAdministration {

	@Nullable IntakeFillResult fill;

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.fill = null;
		this.calls.clear();
	}

	@Override
	public IntakeFillResult fill(int days) {
		this.calls.add("fill " + days);
		return given(this.fill);
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeEventIntakeAdministration fakeEventIntakeAdministration() {
			return new FakeEventIntakeAdministration();
		}

	}

}
