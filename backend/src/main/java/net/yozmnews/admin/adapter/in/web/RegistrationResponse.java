package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.source.ProfileProblemView;
import net.yozmnews.source.SourceRegistrationResult;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 소스 등록의 결과 — docs/specs/admin.md §3.
 * <p>
 * {@code REGISTERED} 면 {@code sourceId} 가, {@code INVALID_PROFILE} 이면 {@code problems} 가 있다. 카탈로그에 없는 프리셋은 결과가 아니라
 * 404 다.
 */
record RegistrationResponse(Outcome result, @Nullable Long sourceId, List<ProfileProblemView> problems) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws ResponseStatusException 404 — 카탈로그에 없는 프리셋일 때
	 */
	static RegistrationResponse of(SourceRegistrationResult result) {
		return switch (result) {
			case SourceRegistrationResult.Registered registered ->
				new RegistrationResponse(Outcome.REGISTERED, registered.id().value(), List.of());
			case SourceRegistrationResult.PresetAlreadyRegistered already ->
				new RegistrationResponse(Outcome.ALREADY_REGISTERED, null, List.of());
			case SourceRegistrationResult.InvalidProfile invalid ->
				new RegistrationResponse(Outcome.INVALID_PROFILE, null, invalid.problems());
			case SourceRegistrationResult.PresetNotFound missing ->
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "카탈로그에 없는 프리셋이다: " + missing.key());
		};
	}

	enum Outcome {

		/** 등록했다 — 소스와 피드는 꺼진 채다 */
		REGISTERED,
		/** 그 프리셋을 참조하는 소스가 이미 있다 */
		ALREADY_REGISTERED,
		/** 운영자 원문이 검증을 통과하지 못했다 */
		INVALID_PROFILE

	}

}
