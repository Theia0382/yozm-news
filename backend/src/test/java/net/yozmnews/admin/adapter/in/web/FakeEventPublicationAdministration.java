package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.FakeResults.given;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.yozmnews.platform.BatchResult;
import net.yozmnews.platform.DiscardResult;
import net.yozmnews.platform.EventPublicationAdministration;
import net.yozmnews.platform.ResubmissionResult;
import net.yozmnews.platform.StrandedPublications;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 이벤트 발행 공개 운영 API 대역 — 돌려줄 결과를 시험이 정하고, 받은 ID 를 남긴다. 한 번에 받는 발행 수를 세지 않는다.
 */
class FakeEventPublicationAdministration implements EventPublicationAdministration {

	@Nullable StrandedPublications stranded;

	@Nullable BatchResult<ResubmissionResult> resubmission;

	@Nullable BatchResult<DiscardResult> discard;

	final List<Set<UUID>> resubmitted = new ArrayList<>();

	final List<Set<UUID>> discarded = new ArrayList<>();

	void reset() {
		this.stranded = null;
		this.resubmission = null;
		this.discard = null;
		this.resubmitted.clear();
		this.discarded.clear();
	}

	@Override
	public StrandedPublications strandedPublications() {
		return given(this.stranded);
	}

	@Override
	public BatchResult<ResubmissionResult> resubmit(Set<UUID> ids) {
		this.resubmitted.add(Set.copyOf(ids));
		return given(this.resubmission);
	}

	@Override
	public BatchResult<DiscardResult> discard(Set<UUID> ids) {
		this.discarded.add(Set.copyOf(ids));
		return given(this.discard);
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeEventPublicationAdministration fakeEventPublicationAdministration() {
			return new FakeEventPublicationAdministration();
		}

	}

}
