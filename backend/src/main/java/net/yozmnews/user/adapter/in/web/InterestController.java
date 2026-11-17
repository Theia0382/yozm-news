package net.yozmnews.user.adapter.in.web;

import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import jakarta.validation.Valid;
import net.yozmnews.platform.AllowedWithoutLlmKey;
import net.yozmnews.platform.UserId;
import net.yozmnews.platform.WebRole;
import net.yozmnews.source.SourceId;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.application.DigestSettings;
import net.yozmnews.user.application.DigestVolumeSettings;
import net.yozmnews.user.application.ExcludedSources;
import net.yozmnews.user.application.ModelSelection;
import net.yozmnews.user.application.TagPreferences;
import net.yozmnews.user.application.TagPreferences.UnknownTagKeysException;
import net.yozmnews.user.domain.interest.DigestVolume;
import net.yozmnews.user.domain.interest.ReportSettings;
import net.yozmnews.user.domain.preference.PreferenceSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관심 설정 — 태그 선호 · 제외 소스 · 일정 · 관심사 · 리포트 · 모델 · 다이제스트 분량의 조회 · 저장. docs/specs/user.md §3 · user-R46 · user-R49 ~ user-R51
 * <p>
 * 모두 세션 사용자의 것이고 키 없이도 연다 — 관심 설정은 내용을 내지 않는다(user-R5).
 */
@WebRole
@RestController
class InterestController {

	private static final String OPEN_WITHOUT_KEY = "관심 설정은 내용을 내지 않는다";

	private final TagPreferences tagPreferences;

	private final ExcludedSources excludedSources;

	private final DigestSettings digestSettings;

	private final ModelSelection modelSelection;

	private final DigestVolumeSettings volumeSettings;

	private final InstantSource clock;

	@Autowired
	InterestController(TagPreferences tagPreferences, ExcludedSources excludedSources, DigestSettings digestSettings,
			ModelSelection modelSelection, DigestVolumeSettings volumeSettings) {
		this(tagPreferences, excludedSources, digestSettings, modelSelection, volumeSettings, InstantSource.system());
	}

	InterestController(TagPreferences tagPreferences, ExcludedSources excludedSources, DigestSettings digestSettings,
			ModelSelection modelSelection, DigestVolumeSettings volumeSettings, InstantSource clock) {
		this.tagPreferences = Objects.requireNonNull(tagPreferences, "tagPreferences");
		this.excludedSources = Objects.requireNonNull(excludedSources, "excludedSources");
		this.digestSettings = Objects.requireNonNull(digestSettings, "digestSettings");
		this.modelSelection = Objects.requireNonNull(modelSelection, "modelSelection");
		this.volumeSettings = Objects.requireNonNull(volumeSettings, "volumeSettings");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** 활성 체계와 태그 선호 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@GetMapping("/api/interests/tags")
	InterestTagsResponse tags(UserId user) {
		return InterestTagsResponse.of(this.tagPreferences.view(user, now()));
	}

	/** 태그 선호 저장 — 그 버전에 없는 키는 400 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@PutMapping("/api/interests/tags")
	InterestTagsSaveResponse saveTags(UserId user, @Valid @RequestBody InterestTagsRequest body) {
		if (body.tagSetId() < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "체계 버전 ID 는 1 이상이다");
		}
		PreferenceSet requested = new PreferenceSet(new HashSet<>(body.topics()), new HashSet<>(body.excludedRegions()),
				new HashSet<>(body.excludedFormats()));
		try {
			return InterestTagsSaveResponse
				.of(this.tagPreferences.save(user, new TagSetId(body.tagSetId()), requested, now()));
		}
		catch (UnknownTagKeysException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "체계에 없는 태그 키다", ex);
		}
	}

	/** 고를 소스와 제외 소스 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@GetMapping("/api/interests/sources")
	InterestSourcesResponse interestSources(UserId user) {
		return InterestSourcesResponse.of(this.excludedSources.view(user, now()));
	}

	/** 제외 소스 저장 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@PutMapping("/api/interests/sources")
	InterestSourcesSaveResponse saveSources(UserId user, @Valid @RequestBody InterestSourcesRequest body) {
		List<SourceId> requested = body.excluded().stream().map(SourceId::new).toList();
		return new InterestSourcesSaveResponse(InterestSourcesSaveResponse.Result.SAVED,
				this.excludedSources.save(user, new HashSet<>(requested), now())
					.stream()
					.map(SourceId::value)
					.sorted()
					.toList());
	}

	/** 일정 · 관심사 · 리포트 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@GetMapping("/api/interests/settings")
	InterestSettingsResponse settings(UserId user) {
		return InterestSettingsResponse.of(this.digestSettings.view(user, now()));
	}

	/** 일정 · 관심사 · 리포트 저장 — 생성 시각이 {@code HH:mm} 이 아니면 400 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@PutMapping("/api/interests/settings")
	InterestSettingsSaveResponse saveSettings(UserId user, @Valid @RequestBody InterestSettingsRequest body) {
		LocalTime time = DigestTimes.parse(body.digestTime())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "생성 시각은 HH:mm 이다"));
		return InterestSettingsSaveResponse.of(this.digestSettings.save(user, body.timezone(), time,
				body.interestText(), new ReportSettings(body.weeklyReport(), body.weeklyReportDay(), body.monthlyReport()),
				now()));
	}

	/** 다이제스트 분량 — 최대 후보 수의 단계 · 직접 입력 · 큰 슬롯 수와 단계의 값 · 범위 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@GetMapping("/api/interests/volume")
	InterestVolumeResponse volume(UserId user) {
		return InterestVolumeResponse.of(this.volumeSettings.view(user, now()));
	}

	/** 다이제스트 분량 저장 — 직접 입력과 직접 입력한 수의 짝이 맞지 않으면 400 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@PutMapping("/api/interests/volume")
	InterestVolumeSaveResponse saveVolume(UserId user, @Valid @RequestBody InterestVolumeRequest body) {
		if (!DigestVolume.customCountMatches(body.volume(), body.customCount())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "직접 입력일 때만 직접 입력한 수를 보낸다");
		}
		return InterestVolumeSaveResponse.of(
				this.volumeSettings.save(user, body.volume(), body.customCount(), body.bigSlots(), now()));
	}

	/** 고를 수 있는 모델 · 고른 모델 · 쓸 모델 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@GetMapping("/api/interests/model")
	InterestModelResponse model(UserId user) {
		return InterestModelResponse.of(this.modelSelection.view(user, now()));
	}

	/** 모델 고르기 */
	@AllowedWithoutLlmKey(reason = OPEN_WITHOUT_KEY)
	@PutMapping("/api/interests/model")
	InterestModelSaveResponse chooseModel(UserId user, @Valid @RequestBody InterestModelRequest body) {
		return InterestModelSaveResponse
			.of(this.modelSelection.choose(user, body.provider(), body.modelId(), now()));
	}

	private Instant now() {
		return this.clock.instant();
	}

}
