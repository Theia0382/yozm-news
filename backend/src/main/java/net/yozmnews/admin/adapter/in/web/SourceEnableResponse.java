package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.source.ActivationRefusal;
import net.yozmnews.source.SourceEnableResult;
import org.jspecify.annotations.Nullable;

/**
 * 소스 켜기의 결과 — {@code REFUSED} 면 {@code refusal} 이 있다. docs/specs/admin.md §3
 */
record SourceEnableResponse(Outcome result, @Nullable ActivationRefusal refusal) {

	static SourceEnableResponse of(SourceEnableResult result) {
		return switch (result) {
			case SourceEnableResult.Enabled enabled -> new SourceEnableResponse(Outcome.ENABLED, null);
			case SourceEnableResult.Refused refused -> new SourceEnableResponse(Outcome.REFUSED, refused.reason());
		};
	}

	enum Outcome {

		/** 켰다 */
		ENABLED,
		/** 켜지 않았다 */
		REFUSED

	}

}
