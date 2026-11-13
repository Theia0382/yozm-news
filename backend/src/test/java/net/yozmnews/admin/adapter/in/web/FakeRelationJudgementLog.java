package net.yozmnews.admin.adapter.in.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.RelationJudgementId;
import net.yozmnews.evaluation.RelationJudgementLog;
import net.yozmnews.evaluation.RelationJudgementView;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 관계 판정 원본 조회 대역 — 있는 원본을 시험이 정하고, 받은 ID 묶음을 남긴다. 공개 조회처럼 상한을 넘는 묶음을 거부한다.
 */
class FakeRelationJudgementLog implements RelationJudgementLog {

	final Map<RelationJudgementId, RelationJudgementView> views = new LinkedHashMap<>();

	final List<List<Long>> calls = new ArrayList<>();

	void reset() {
		this.views.clear();
		this.calls.clear();
	}

	@Override
	public Map<RelationJudgementId, RelationJudgementView> findAll(Collection<RelationJudgementId> ids) {
		if (ids.size() > MAX_IDS) {
			throw new IllegalArgumentException("관계 판정 원본은 한 번에 " + MAX_IDS + "건까지 찾는다: " + ids.size());
		}
		this.calls.add(ids.stream().map(RelationJudgementId::value).toList());
		Map<RelationJudgementId, RelationJudgementView> found = new LinkedHashMap<>();
		ids.forEach(id -> {
			RelationJudgementView view = this.views.get(id);
			if (view != null) {
				found.put(id, view);
			}
		});
		return found;
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeRelationJudgementLog fakeRelationJudgementLog() {
			return new FakeRelationJudgementLog();
		}

	}

}
