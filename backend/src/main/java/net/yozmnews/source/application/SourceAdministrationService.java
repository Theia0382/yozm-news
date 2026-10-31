package net.yozmnews.source.application;

import java.util.List;
import java.util.Optional;

import net.yozmnews.source.ArticleSampleView;
import net.yozmnews.source.CriterionShortfall;
import net.yozmnews.source.DocumentProblemView;
import net.yozmnews.source.FeedEnableResult;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FetchFailureView;
import net.yozmnews.source.PresetView;
import net.yozmnews.source.PreviewReport;
import net.yozmnews.source.ProfilePreviewResult;
import net.yozmnews.source.ProfileProblemView;
import net.yozmnews.source.ProfileRevisionResult;
import net.yozmnews.source.SkippedItemView;
import net.yozmnews.source.SourceAdministration;
import net.yozmnews.source.SourceEnableResult;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.SourceRegistrationResult;
import net.yozmnews.source.SourceView;
import net.yozmnews.source.SummaryLengthsView;
import net.yozmnews.source.domain.feed.DocumentProblem;
import net.yozmnews.source.domain.fetch.FetchFailure;
import net.yozmnews.source.domain.gate.FeedActivation;
import net.yozmnews.source.domain.gate.FeedPreview;
import net.yozmnews.source.domain.gate.PreviewOutcome;
import net.yozmnews.source.domain.gate.ProfilePreview;
import net.yozmnews.source.domain.gate.Shortfall;
import net.yozmnews.source.domain.gate.SummaryLengths;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.profile.ProfileProblem;
import net.yozmnews.source.domain.profile.text.TextLocation;
import net.yozmnews.source.domain.registry.Activation;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * 공개 운영 API {@link SourceAdministration} 의 구현 — 유스케이스에 맡기고 결과를 공개 타입으로 옮긴다. docs/specs/source.md §4.
 * <p>
 * 트랜잭션을 열지 않는다 — 피드 켜기 · 원문 미리보기는 트랜잭션 밖에서 피드를 받는다.
 */
@Service
class SourceAdministrationService implements SourceAdministration {

	private final SourceCatalog catalog;

	private final SourceRegistration registration;

	private final SourceOperations operations;

	private final ProfilePreviewer previewer;

	SourceAdministrationService(SourceCatalog catalog, SourceRegistration registration, SourceOperations operations,
			ProfilePreviewer previewer) {
		this.catalog = catalog;
		this.registration = registration;
		this.operations = operations;
		this.previewer = previewer;
	}

	@Override
	public List<PresetView> presets() {
		return this.catalog.presets();
	}

	@Override
	public List<SourceView> sources() {
		return this.catalog.sources();
	}

	@Override
	public Optional<String> operatorProfileText(SourceId id) {
		return this.catalog.operatorProfileText(id);
	}

	@Override
	public SourceRegistrationResult registerPreset(String presetKey) {
		PresetKey key;
		try {
			key = new PresetKey(presetKey);
		}
		catch (IllegalArgumentException ex) {
			return new SourceRegistrationResult.PresetNotFound(presetKey);
		}
		return registration(this.registration.registerPreset(key));
	}

	@Override
	public SourceRegistrationResult registerOperatorProfile(String text) {
		return registration(this.registration.registerOperatorProfile(text));
	}

	@Override
	public ProfileRevisionResult reviseOperatorProfile(SourceId id, String text) {
		return switch (this.registration.reviseOperatorProfile(id, text)) {
			case Revision.Revised revised -> new ProfileRevisionResult.Revised(keys(revised.disabledFeeds()),
					revised.sourceDisabled(), keys(revised.addedFeeds()));
			case Revision.InvalidProfile invalid ->
				new ProfileRevisionResult.InvalidProfile(problems(invalid.problems()));
			case Revision.NotOperatorProfile notOperator -> new ProfileRevisionResult.NotOperatorProfile();
		};
	}

	@Override
	public ProfilePreviewResult previewOperatorProfile(String text, String feedKey) {
		FeedKey key;
		try {
			key = new FeedKey(feedKey);
		}
		catch (IllegalArgumentException ex) {
			return new ProfilePreviewResult.FeedNotInProfile(feedKey);
		}
		return switch (this.previewer.preview(text, key)) {
			case ProfilePreview.Invalid invalid ->
				new ProfilePreviewResult.InvalidProfile(problems(invalid.problems()));
			case ProfilePreview.FeedNotInProfile missing -> new ProfilePreviewResult.FeedNotInProfile(feedKey);
			case ProfilePreview.Previewed previewed -> switch (previewed.outcome()) {
				case PreviewOutcome.Passed passed -> new ProfilePreviewResult.Passed(report(passed.preview()));
				case PreviewOutcome.BelowCriteria below ->
					new ProfilePreviewResult.BelowCriteria(report(below.preview()), shortfalls(below.shortfalls()));
				case PreviewOutcome.Unfetched unfetched ->
					new ProfilePreviewResult.Unfetched(failure(unfetched.failure()));
			};
		};
	}

	@Override
	public SourceEnableResult enableSource(SourceId id) {
		return switch (this.operations.enableSource(id)) {
			case Activation.Activated activated -> new SourceEnableResult.Enabled();
			case Activation.Refused refused -> new SourceEnableResult.Refused(refused.reason());
		};
	}

	@Override
	public void disableSource(SourceId id) {
		this.operations.disableSource(id);
	}

	@Override
	public FeedEnableResult enableFeed(FeedId id) {
		return switch (this.operations.enableFeed(id)) {
			case FeedActivation.Activated activated ->
				new FeedEnableResult.Enabled((activated.preview() != null) ? report(activated.preview()) : null);
			case FeedActivation.Refused refused -> new FeedEnableResult.Refused(refused.reason());
			case FeedActivation.BelowCriteria below ->
				new FeedEnableResult.BelowCriteria(report(below.preview()), shortfalls(below.shortfalls()));
			case FeedActivation.Unfetched unfetched -> new FeedEnableResult.Unfetched(failure(unfetched.failure()));
			case FeedActivation.ReadingChanged changed -> new FeedEnableResult.ReadingChanged();
		};
	}

	@Override
	public void disableFeed(FeedId id) {
		this.operations.disableFeed(id);
	}

	@Override
	public boolean changeSourcePollInterval(SourceId id, @Nullable Integer minutes) {
		return this.operations.changeSourcePollInterval(id, minutes);
	}

	@Override
	public boolean changeFeedPollInterval(FeedId id, @Nullable Integer minutes) {
		return this.operations.changeFeedPollInterval(id, minutes);
	}

	private static SourceRegistrationResult registration(Registration registration) {
		return switch (registration) {
			case Registration.Registered registered -> new SourceRegistrationResult.Registered(registered.id());
			case Registration.PresetNotFound missing ->
				new SourceRegistrationResult.PresetNotFound(missing.key().value());
			case Registration.PresetAlreadyRegistered already ->
				new SourceRegistrationResult.PresetAlreadyRegistered(already.key().value());
			case Registration.InvalidProfile invalid ->
				new SourceRegistrationResult.InvalidProfile(problems(invalid.problems()));
		};
	}

	private static PreviewReport report(FeedPreview preview) {
		SummaryLengths summaries = preview.summaries();
		return new PreviewReport(preview.items(), preview.articles(),
				preview.skipped().stream().map(item -> new SkippedItemView(item.position(), item.reason())).toList(),
				preview.datedArticles(), preview.publishedAtUnread(),
				new SummaryLengthsView(summaries.present(), summaries.absent(), summaries.min(), summaries.median(),
						summaries.max()),
				preview.replacedCharacters(), preview.decodedCharacters(), preview.latestPublishedAt(),
				preview.movedTo(),
				preview.samples()
					.stream()
					.map(sample -> new ArticleSampleView(sample.title(), sample.publishedAt(),
							sample.publishedAtEstimated()))
					.toList());
	}

	private static List<CriterionShortfall> shortfalls(List<Shortfall> shortfalls) {
		return shortfalls.stream()
			.map(shortfall -> new CriterionShortfall(shortfall.criterion(), shortfall.actual(), shortfall.threshold()))
			.toList();
	}

	private static FetchFailureView failure(FetchFailure failure) {
		@Nullable DocumentProblem problem = failure.problem();
		return new FetchFailureView(failure.reason(), failure.permanence(), failure.message(), failure.httpStatus(),
				(problem != null)
						? new DocumentProblemView(problem.kind(), problem.message(), problem.line(), problem.column())
						: null);
	}

	private static List<String> keys(List<FeedKey> feeds) {
		return feeds.stream().map(FeedKey::value).toList();
	}

	private static List<ProfileProblemView> problems(List<ProfileProblem> problems) {
		return problems.stream().map(SourceAdministrationService::problem).toList();
	}

	private static ProfileProblemView problem(ProfileProblem problem) {
		TextLocation location = problem.location();
		boolean known = !location.equals(TextLocation.UNKNOWN);
		return new ProfileProblemView(problem.path().toString(), problem.code().name(), problem.message(),
				known ? location.line() : null, known ? location.column() : null);
	}

}
