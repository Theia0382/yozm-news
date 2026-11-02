package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetVersionView;

/**
 * 태그 체계 버전 목록(번호가 큰 것부터)과 재평가 기간의 상한 — docs/specs/admin.md §3. 상한은
 * {@link TagSetAdministration#MAX_REEVALUATION_DAYS} 에서 읽는다.
 */
record TagSetListResponse(List<TagSetVersionItem> versions, int maxReevaluationDays) {

	static TagSetListResponse of(List<TagSetVersionView> versions) {
		return new TagSetListResponse(versions.stream().map(TagSetVersionItem::of).toList(),
				TagSetAdministration.MAX_REEVALUATION_DAYS);
	}

	/** 버전 하나 — ID 는 JSON 수다(admin-R1) */
	record TagSetVersionItem(long id, int number, boolean active, ChangeActor createdBy, Instant createdAt) {

		static TagSetVersionItem of(TagSetVersionView version) {
			return new TagSetVersionItem(version.id().value(), version.number(), version.active(), version.createdBy(),
					version.createdAt());
		}

	}

}
