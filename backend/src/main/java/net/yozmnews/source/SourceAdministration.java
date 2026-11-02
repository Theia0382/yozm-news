package net.yozmnews.source;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import net.yozmnews.source.domain.poll.FeedStatusRule;
import net.yozmnews.source.domain.profile.PollInterval;
import org.jspecify.annotations.Nullable;

/**
 * 소스 · 피드의 운영 기능 — 운영자 화면이 부르는 공개 API. docs/specs/source.md §3.
 * <p>
 * 없는 소스 · 피드는 {@link UnknownSourceException} · {@link UnknownFeedException}, 동시에 바뀐 소스 · 피드는
 * {@link org.springframework.dao.OptimisticLockingFailureException} 이다. 피드 켜기와 원문 미리보기는 피드를 받으므로 호출자의
 * 트랜잭션 밖에서 부른다.
 */
public interface SourceAdministration {

	/** 수집 주기의 하한(분) — 값의 정본은 수집 프로필의 하한이다(source-R14) */
	int MINIMUM_POLL_INTERVAL_MINUTES = Math.toIntExact(PollInterval.MINIMUM.toMinutes());

	/** 피드 상태의 판정 기준값과 재판정 간격 — 값의 정본은 피드 상태 판정 규칙이다(source-R30 · source-R31 · source-R47) */
	FeedStatusCriteria FEED_STATUS_CRITERIA = new FeedStatusCriteria(FeedStatusRule.DEGRADED_AFTER_FAILURES,
			FeedStatusRule.DEGRADED_AFTER_PERMANENT_FAILURES, FeedStatusRule.STALE_AFTER,
			Duration.parse(FeedStatusRule.RECHECK_INTERVAL));

	/** 카탈로그의 프리셋 — 키 순 */
	List<PresetView> presets();

	/** 숨기지 않은 소스 — 소스 ID 순, 피드는 피드 ID 순. 피드마다 수집 상태를 싣는다 */
	List<SourceView> sources();

	/** 숨긴 소스 — 숨긴 시각 순(같으면 소스 ID 순) */
	List<SourceView> hiddenSources();

	/** 운영자 원문 — 프리셋을 참조하는 소스면 비어 있다 */
	Optional<String> operatorProfileText(SourceId id);

	/** 프리셋을 참조하는 소스를 등록한다(source-R1 · source-R2) — 키 형식이 틀리면 {@code PresetNotFound} */
	SourceRegistrationResult registerPreset(String presetKey);

	/** 운영자 원문으로 소스를 등록한다(source-R1) */
	SourceRegistrationResult registerOperatorProfile(String text);

	/** 운영자 원문을 고친다 — 읽기 설정이 바뀐 켜진 피드와 검토일이 사라진 소스를 끈다 */
	ProfileRevisionResult reviseOperatorProfile(SourceId id, String text);

	/** 등록 전 운영자 원문의 피드 하나를 미리본다(source-R9) — 피드 식별자 형식이 틀리면 {@code FeedNotInProfile} */
	ProfilePreviewResult previewOperatorProfile(String text, String feedKey);

	/** 소스를 켠다(source-R4) */
	SourceEnableResult enableSource(SourceId id);

	void disableSource(SourceId id);

	/** 피드를 켠다 — 꺼진 피드는 한 번 받아 미리보고 기준을 넘을 때만 켠다(source-R5 · source-R9) */
	FeedEnableResult enableFeed(FeedId id);

	void disableFeed(FeedId id);

	/** 소스의 운영자 수집 주기를 바꾼다 — null 이면 지운다. 하한보다 짧으면 바꾸지 않고 false(source-R14) */
	boolean changeSourcePollInterval(SourceId id, @Nullable Integer minutes);

	/** 피드의 운영자 수집 주기를 바꾼다 — null 이면 지운다. 하한보다 짧으면 바꾸지 않고 false(source-R14) */
	boolean changeFeedPollInterval(FeedId id, @Nullable Integer minutes);

	/** 소스를 숨긴다 — 소스 · 그 피드를 끈다. 이미 숨겼으면 아무것도 바꾸지 않는다 */
	void hideSource(SourceId id);

	/** 숨긴 소스를 다시 보인다 — 소스 · 피드는 꺼진 채다 */
	void unhideSource(SourceId id);

	/** 피드를 숨긴다 — 프로필을 얻었고 그 프로필에 없는 피드만 */
	FeedRemovalResult hideFeed(FeedId id);

	/** 숨긴 피드를 다시 보인다 — 꺼진 채다 */
	void unhideFeed(FeedId id);

	/** 소스를 지운다 — 그 피드 · 수집 상태 · 요청 예약 · 출처 이름까지 지우고 {@link SourceDeleted} 를 발행한다. 숨긴 소스도 지운다 */
	void deleteSource(SourceId id);

	/** 피드를 지운다 — 프로필을 얻었고 그 프로필에 없는 피드만. {@link FeedDeleted} 를 발행한다 */
	FeedRemovalResult deleteFeed(FeedId id);

}
