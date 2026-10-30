package net.yozmnews.source.domain.profile;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.yozmnews.source.domain.profile.ProfileDraft.DatesDraft;
import net.yozmnews.source.domain.profile.ProfileDraft.FeedDraft;
import net.yozmnews.source.domain.profile.ProfileDraft.Located;
import net.yozmnews.source.domain.profile.ProfileDraft.PreCorrectionDraft;
import net.yozmnews.source.domain.profile.ProfileDraft.SourceDraft;
import net.yozmnews.source.domain.profile.text.TextLocation;
import org.jspecify.annotations.Nullable;

/**
 * 프로필 값 규칙의 검증기 — 정본 · docs/specs/source.md §4 · source-R11
 */
final class ProfileValidator {

	/**
	 * 날짜 형식을 검사하는 표본 시각 — 분이 0 이 아니어야 한다. 분이 0 이면 시까지만 담은 패턴이 왕복을 통과해 버린다. 오전/오후 없는
	 * 12시간제 패턴은 다시 읽은 값에서 시각이 정해지지 않아 거부된다.
	 */
	static final LocalDateTime DATE_SAMPLE = LocalDateTime.of(2026, 10, 25, 21, 37, 52);

	private static final URI INVALID_URL = URI.create("http://invalid");

	private static final Set<String> ISO_LANGUAGES = Set.of(Locale.getISOLanguages());

	private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9._-]*");

	private static final Pattern ELEMENT = Pattern.compile("(?:(" + NAME + "):)?(" + NAME + ")");

	/** 검증하는 원문의 종류 */
	enum Kind {

		/** 프리셋 파일 — 이용조건 검토일이 반드시 있다 */
		PRESET_FILE,
		/** 운영자 프로필 — 이용조건 검토일은 선택이다(없으면 활성화 관문이 켜지 못하게 막는다) */
		OPERATOR_PROFILE

	}

	/** 검증 결과 */
	sealed interface ValidationResult {

		/** 검증을 통과했다 */
		record Valid(CollectionProfile profile) implements ValidationResult {

		}

		/** 문제가 있다 */
		record Invalid(List<ProfileProblem> problems) implements ValidationResult {

			public Invalid {
				problems = List.copyOf(problems);
			}

		}

	}

	private final List<ProfileProblem> problems = new ArrayList<>();

	private ProfileValidator() {
	}

	static ValidationResult validate(ProfileDraft draft, Kind kind) {
		ProfileValidator validator = new ProfileValidator();
		CollectionProfile profile = validator.profile(draft, kind);
		return validator.problems.isEmpty() ? new ValidationResult.Valid(profile)
				: new ValidationResult.Invalid(validator.problems);
	}

	/** 문제가 있으면 쓰지 않을 값으로 채운 프로필을 돌려준다 — 호출자는 문제 목록만 본다 */
	private CollectionProfile profile(ProfileDraft draft, Kind kind) {
		SourceInfo source = source(draft);
		LocalDate termsReviewedAt = termsReviewedAt(draft, kind);
		List<FeedSpec> feeds = feeds(draft);
		PollInterval pollInterval = pollInterval(draft.pollIntervalMinutes());
		List<PreCorrection> preCorrections = preCorrections(draft);
		Map<String, String> namespaces = namespaces(draft);
		Map<ItemField, List<ValueSource>> items = items(draft, namespaces);
		DateParsing dates = dates(draft.dates(), source.language());
		return new CollectionProfile(source, termsReviewedAt, feeds, pollInterval, preCorrections, items, dates);
	}

	private SourceInfo source(ProfileDraft draft) {
		ProfilePath path = ProfilePath.of(ProfileBinder.SOURCE);
		SourceDraft source = draft.source();
		if (source == null) {
			missing(path, draft.location());
			return new SourceInfo("", INVALID_URL, Locale.ROOT);
		}
		String name = required(source.name(), path.child(ProfileBinder.NAME), source.location());
		if (source.name() != null && name.isBlank()) {
			problem(source.name(), ProblemCode.BLANK, "이름이 비어 있다");
		}
		URI homepage = orInvalid(url(source.homepage(), path.child(ProfileBinder.HOMEPAGE), source.location()));
		Locale language = language(source.language(), path.child(ProfileBinder.LANGUAGE), source.location());
		return new SourceInfo(name, homepage, language);
	}

	private @Nullable LocalDate termsReviewedAt(ProfileDraft draft, Kind kind) {
		Located<String> value = draft.termsReviewedAt();
		if (value == null) {
			boolean required = switch (kind) {
				case PRESET_FILE -> true;
				case OPERATOR_PROFILE -> false;
			};
			if (required) {
				missing(ProfilePath.of(ProfileBinder.TERMS_REVIEWED_AT), draft.location());
			}
			return null;
		}
		try {
			return LocalDate.parse(value.value(), DateTimeFormatter.ISO_LOCAL_DATE);
		}
		catch (DateTimeParseException ex) {
			problem(value, ProblemCode.INVALID_TERMS_REVIEWED_AT, "이용조건 검토일은 yyyy-MM-dd 날짜여야 한다: " + value.value());
			return null;
		}
	}

	private List<FeedSpec> feeds(ProfileDraft draft) {
		Located<List<FeedDraft>> feeds = draft.feeds();
		if (feeds == null) {
			missing(ProfilePath.of(ProfileBinder.FEEDS), draft.location());
			return List.of();
		}
		if (feeds.value().isEmpty()) {
			problem(feeds, ProblemCode.NO_FEEDS, "피드가 하나 이상 있어야 한다");
			return List.of();
		}
		List<FeedSpec> specs = new ArrayList<>();
		Set<URI> urls = new HashSet<>();
		for (FeedDraft feed : feeds.value()) {
			FeedKey key = feedKey(feed);
			URI url = url(feed.url(), feed.path().child(ProfileBinder.URL), feed.location());
			if (url != null && feed.url() != null && !urls.add(url)) {
				problem(feed.url(), ProblemCode.DUPLICATE_FEED_URL, "피드 URL 이 앞의 피드와 겹친다: " + url);
			}
			String section = required(feed.section(), feed.path().child(ProfileBinder.SECTION), feed.location());
			if (feed.section() != null && section.isBlank()) {
				problem(feed.section(), ProblemCode.BLANK, "섹션 이름이 비어 있다");
			}
			PollInterval pollInterval = pollInterval(feed.pollIntervalMinutes());
			if (key != null) {
				specs.add(new FeedSpec(key, orInvalid(url), section, pollInterval));
			}
		}
		return specs;
	}

	private @Nullable FeedKey feedKey(FeedDraft feed) {
		Located<String> key = feed.key();
		if (key == null) {
			missing(feed.path().child(ProfileBinder.KEY), feed.location());
			return null;
		}
		if (!Identifiers.isSlug(key.value())) {
			problem(key, ProblemCode.INVALID_FEED_KEY, "피드 식별자는 소문자 · 숫자를 하이픈으로 이은 " + Identifiers.SLUG_MAX_LENGTH
					+ "자 이하여야 한다: " + key.value());
			return null;
		}
		return new FeedKey(key.value());
	}

	private @Nullable PollInterval pollInterval(@Nullable Located<Integer> minutes) {
		if (minutes == null) {
			return null;
		}
		return PollInterval.ofMinutes(minutes.value()).orElseGet(() -> {
			problem(minutes, ProblemCode.POLL_INTERVAL_TOO_SHORT,
					"수집 주기는 " + PollInterval.MINIMUM.toMinutes() + "분 이상이어야 한다: " + minutes.value());
			return null;
		});
	}

	private List<PreCorrection> preCorrections(ProfileDraft draft) {
		Located<List<PreCorrectionDraft>> corrections = draft.preCorrections();
		if (corrections == null) {
			return List.of();
		}
		List<PreCorrection> result = new ArrayList<>();
		Set<PreCorrection.Rule> seen = EnumSet.noneOf(PreCorrection.Rule.class);
		for (PreCorrectionDraft correction : corrections.value()) {
			Located<String> rule = correction.rule();
			if (rule == null) {
				missing(correction.path().child(ProfileBinder.RULE), correction.location());
				continue;
			}
			PreCorrection.Rule known = PreCorrection.Rule.ofKey(rule.value()).orElse(null);
			if (known == null) {
				problem(rule, ProblemCode.UNKNOWN_PRE_CORRECTION, "알 수 없는 사전 보정 규칙: " + rule.value());
				continue;
			}
			// 같은 규칙이 둘이면 어느 쪽을 쓸지(force-encoding 의 문자셋 등)가 적용 순서에 숨는다
			if (!seen.add(known)) {
				problem(rule, ProblemCode.DUPLICATE_PRE_CORRECTION, "같은 사전 보정 규칙이 앞에 또 있다: " + rule.value());
				continue;
			}
			PreCorrection built = switch (known) {
				case REPLACE_UNDEFINED_ENTITIES -> withoutCharset(correction, new PreCorrection.ReplaceUndefinedEntities());
				case STRIP_INVALID_CONTROL_CHARS -> withoutCharset(correction, new PreCorrection.StripInvalidControlChars());
				case FORCE_ENCODING -> forceEncoding(correction);
			};
			if (built != null) {
				result.add(built);
			}
		}
		return result;
	}

	private PreCorrection withoutCharset(PreCorrectionDraft correction, PreCorrection built) {
		if (correction.charset() != null) {
			problem(correction.charset(), ProblemCode.UNKNOWN_KEY,
					"규칙 '" + built.rule().key() + "' 은 charset 을 받지 않는다");
		}
		return built;
	}

	private PreCorrection.@Nullable ForceEncoding forceEncoding(PreCorrectionDraft correction) {
		Located<String> charset = correction.charset();
		if (charset == null) {
			missing(correction.path().child(ProfileBinder.CHARSET), correction.location());
			return null;
		}
		try {
			if (Charset.isSupported(charset.value())) {
				return new PreCorrection.ForceEncoding(Charset.forName(charset.value()));
			}
		}
		catch (IllegalCharsetNameException ex) {
			// 아래에서 알린다
		}
		problem(charset, ProblemCode.INVALID_CHARSET, "지원하지 않는 문자 인코딩: " + charset.value());
		return null;
	}

	/** 접두사 → 이름공간 URI 를 만든다 — 기본 접두사를 포함한다 */
	private Map<String, String> namespaces(ProfileDraft draft) {
		Map<String, String> namespaces = new LinkedHashMap<>(KnownNamespaces.PREFIXES);
		Located<Map<String, Located<String>>> declared = draft.namespaces();
		if (declared == null) {
			return namespaces;
		}
		declared.value().forEach((prefix, uri) -> {
			if (!NAME.matcher(prefix).matches()) {
				problem(uri, ProblemCode.INVALID_ELEMENT_NAME, "이름공간 접두사 형식이 틀렸다: " + prefix);
				return;
			}
			if (KnownNamespaces.PREFIXES.containsKey(prefix)) {
				problem(uri, ProblemCode.PREFIX_REDEFINED, "기본 접두사 '" + prefix + "' 는 다시 정의할 수 없다");
				return;
			}
			try {
				if (new URI(uri.value()).isAbsolute()) {
					namespaces.put(prefix, uri.value());
					return;
				}
			}
			catch (URISyntaxException ex) {
				// 아래에서 알린다
			}
			problem(uri, ProblemCode.INVALID_URL, "이름공간은 절대 URI 여야 한다: " + uri.value());
		});
		return namespaces;
	}

	private Map<ItemField, List<ValueSource>> items(ProfileDraft draft, Map<String, String> namespaces) {
		Map<ItemField, List<ValueSource>> items = new EnumMap<>(ItemField.class);
		for (ItemField field : ItemField.values()) {
			items.put(field, List.of(new ValueSource.Standard()));
		}
		Located<Map<ItemField, Located<List<Located<String>>>>> declared = draft.items();
		if (declared == null) {
			return items;
		}
		declared.value().forEach((field, sources) -> {
			List<ValueSource> resolved = new ArrayList<>();
			Set<ValueSource> seen = new HashSet<>();
			for (Located<String> source : sources.value()) {
				ValueSource value = valueSource(source, namespaces);
				if (value == null) {
					continue;
				}
				if (!seen.add(value)) {
					problem(source, ProblemCode.DUPLICATE_VALUE_SOURCE, "같은 값이 앞에 또 있다: " + source.value());
					continue;
				}
				resolved.add(value);
			}
			items.put(field, resolved);
		});
		return items;
	}

	private @Nullable ValueSource valueSource(Located<String> source, Map<String, String> namespaces) {
		if (source.value().equals(ValueSource.STANDARD_TOKEN)) {
			return new ValueSource.Standard();
		}
		Matcher matcher = ELEMENT.matcher(source.value());
		if (!matcher.matches()) {
			problem(source, ProblemCode.INVALID_ELEMENT_NAME, "요소 이름은 '접두사:이름' · '이름' 또는 "
					+ ValueSource.STANDARD_TOKEN + " 이어야 한다: " + source.value());
			return null;
		}
		String prefix = matcher.group(1);
		if (prefix == null) {
			return new ValueSource.Element(new ElementName("", matcher.group(2)));
		}
		String uri = namespaces.get(prefix);
		if (uri == null) {
			problem(source, ProblemCode.UNKNOWN_NAMESPACE_PREFIX, "정의되지 않은 이름공간 접두사: " + prefix);
			return null;
		}
		return new ValueSource.Element(new ElementName(uri, matcher.group(2)));
	}

	private DateParsing dates(@Nullable DatesDraft dates, Locale language) {
		if (dates == null) {
			return new DateParsing(List.of(), DateParsing.DEFAULT_ZONE);
		}
		ZoneId zone = DateParsing.DEFAULT_ZONE;
		Located<String> timeZone = dates.timeZone();
		if (timeZone != null) {
			try {
				zone = ZoneId.of(timeZone.value());
			}
			catch (DateTimeException ex) {
				problem(timeZone, ProblemCode.INVALID_TIME_ZONE, "알 수 없는 시간대: " + timeZone.value());
			}
		}
		List<DatePattern> patterns = new ArrayList<>();
		Located<List<Located<String>>> formats = dates.formats();
		if (formats != null) {
			for (Located<String> format : formats.value()) {
				DatePattern pattern = datePattern(format, language, zone);
				if (pattern != null) {
					patterns.add(pattern);
				}
			}
		}
		return new DateParsing(patterns, zone);
	}

	/**
	 * 패턴이 컴파일되고, 표본 시각을 형식화한 뒤 다시 읽었을 때 연 · 월 · 일 · 시 · 분이 그대로 돌아오는지 본다
	 */
	private @Nullable DatePattern datePattern(Located<String> format, Locale language, ZoneId zone) {
		DateTimeFormatter formatter;
		try {
			formatter = DateTimeFormatter.ofPattern(format.value(), language);
		}
		catch (IllegalArgumentException ex) {
			problem(format, ProblemCode.INVALID_DATE_FORMAT, "날짜 형식 패턴을 읽을 수 없다: " + format.value());
			return null;
		}
		try {
			TemporalAccessor parsed = formatter.parse(formatter.format(DATE_SAMPLE.atZone(zone)));
			LocalDate date = parsed.query(TemporalQueries.localDate());
			LocalTime time = parsed.query(TemporalQueries.localTime());
			if (date != null && time != null && date.equals(DATE_SAMPLE.toLocalDate())
					&& time.getHour() == DATE_SAMPLE.getHour() && time.getMinute() == DATE_SAMPLE.getMinute()) {
				return new DatePattern(format.value(), formatter);
			}
		}
		catch (DateTimeException ex) {
			// 아래에서 알린다
		}
		problem(format, ProblemCode.INVALID_DATE_FORMAT, "날짜 형식은 연 · 월 · 일 · 시 · 분을 모두 담아야 한다: " + format.value());
		return null;
	}

	private String required(@Nullable Located<String> value, ProfilePath path, TextLocation parent) {
		if (value == null) {
			missing(path, parent);
			return "";
		}
		return value.value();
	}

	/**
	 * URL 값을 읽는다.
	 * @return 문제가 있으면 null — 문제는 이미 알렸다
	 */
	private @Nullable URI url(@Nullable Located<String> value, ProfilePath path, TextLocation parent) {
		if (value == null) {
			missing(path, parent);
			return null;
		}
		try {
			URI uri = new URI(value.value());
			String scheme = uri.getScheme();
			if (uri.isAbsolute() && uri.getHost() != null
					&& ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
				return uri;
			}
		}
		catch (URISyntaxException ex) {
			// 아래에서 알린다
		}
		problem(value, ProblemCode.INVALID_URL, "http · https 로 시작하는 절대 URL 이어야 한다: " + value.value());
		return null;
	}

	/** 문제가 있을 때 모델을 채우는 자리 값 — 문제 목록이 비지 않으므로 밖으로 나가지 않는다 */
	private static URI orInvalid(@Nullable URI uri) {
		return (uri != null) ? uri : INVALID_URL;
	}

	private Locale language(@Nullable Located<String> value, ProfilePath path, TextLocation parent) {
		if (value == null) {
			missing(path, parent);
			return Locale.ROOT;
		}
		Locale locale = Locale.forLanguageTag(value.value());
		if (!locale.getLanguage().isEmpty() && locale.toLanguageTag().equals(value.value())
				&& ISO_LANGUAGES.contains(locale.getLanguage())) {
			return locale;
		}
		problem(value, ProblemCode.INVALID_LANGUAGE, "언어는 정규형 BCP 47 태그(예: ko, ko-KR)여야 한다: " + value.value());
		return Locale.ROOT;
	}

	private void missing(ProfilePath path, TextLocation parent) {
		this.problems.add(new ProfileProblem(path, ProblemCode.MISSING_REQUIRED, "필수 항목이 없다", parent));
	}

	private void problem(Located<?> value, ProblemCode code, String message) {
		this.problems.add(new ProfileProblem(value.path(), code, message, value.location()));
	}

}
