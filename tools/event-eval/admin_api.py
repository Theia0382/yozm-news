"""운영자 API 읽기 — 사건 · 판정 기록 · 스토리라인 · 큰 스토리라인.

응답 계약의 정본은 저장소의 api/openapi.json 이다. 이 모듈이 읽는 필드는 USED_FIELDS 가 모두 적고(test_contract.py 가
명세와 대조한다), 응답 필드는 field() 로만 읽는다.
"""

from __future__ import annotations

import http.client
import json
import urllib.error
import urllib.parse
import urllib.request
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from decimal import Decimal
from typing import Final

from labels import parse_instant

TOKEN_ENV: Final = "YOZMNEWS_ADMIN_TOKEN"
"""운영자 토큰을 받는 환경 변수 — 배포 구성이 앱에 넘기는 이름과 같다."""

USED_FIELDS: Final[Mapping[str, tuple[str, ...]]] = {
    "EventListResponse": ("events", "next"),
    "EventRowResponse": ("id",),
    "EventCursorResponse": ("lastArticleAt", "id"),
    "EventDetailResponse": ("id", "status", "articles"),
    "EventArticleResponse": ("articleId", "article", "publishedAt"),
    "ArticleSummaryResponse": ("title", "sources"),
    "EventJudgementListResponse": ("criteria", "judgements", "next"),
    "EventJudgementRowResponse": ("articleId", "modelVersion"),
    "JudgementCursorResponse": ("judgedAt", "id"),
    "AssignmentCriteriaResponse": ("windowSeconds", "upper", "lower", "store", "probability"),
    "StorylineListResponse": ("storylines", "next", "linkingCriteria", "relationCriteria"),
    "StorylineRowResponse": ("id",),
    "StorylineCursorResponse": ("lastArticleAt", "id"),
    "LinkingCriteriaResponse": ("windowSeconds", "threshold"),
    "RelationCriteriaResponse": ("probability", "oneMatter", "minEvents", "candidateScore", "windowSeconds"),
    "StorylineDetailResponse": ("id", "events", "relations", "asked"),
    "StorylineEventResponse": ("id", "direct"),
    "StorylineRelationResponse": ("kind", "sourceId", "targetId", "judgement"),
    "StorylineAskedResponse": ("otherId", "smallerEvents", "outcome", "askedAt", "judgementId", "judgement"),
    "RelationJudgementResponse": ("id", "firstId", "secondId", "modelVersion", "reading", "none", "secondPartOfFirst",
                                  "firstPartOfSecond", "firstLedToSecond", "secondLedToFirst", "firstOneMatter",
                                  "secondOneMatter"),
    "BigStorylineListResponse": ("storylines",),
    "BigStorylineRowResponse": ("id",),
}
"""스키마 이름 → 도구가 읽는 필드."""

_EVENTS: Final = "/api/admin/events"
_JUDGEMENTS: Final = "/api/admin/event-judgements"
_STORYLINES: Final = "/api/admin/storylines"
_BIG_STORYLINES: Final = "/api/admin/big-storylines"

ANSWERS: Final = ("none", "secondPartOfFirst", "firstPartOfSecond", "firstLedToSecond", "secondLedToFirst")
"""관계 판정의 답 다섯 — 응답 칸 이름, 이 순서로 확률을 싣는다."""


class ApiError(Exception):
    """운영자 API 를 읽지 못했다. 상태 코드 · 경로와 (4xx 문제 응답이면) 그 설명만 싣는다 — 토큰과 응답 본문은 싣지 않는다."""

    def __init__(self, path: str, status: int | None, detail: str) -> None:
        self.path = path
        self.status = status
        super().__init__(f"{path} — {detail}" if status is None else f"{path} — {status}: {detail}")


def field(value: object, schema: str, name: str) -> object:
    """응답 객체의 필드 하나. USED_FIELDS 에 없는 필드를 읽으려 하면 LookupError(코드 결함), 응답에 없으면 ApiError."""
    if name not in USED_FIELDS.get(schema, ()):
        raise LookupError(f"{schema}.{name} 는 USED_FIELDS 에 없다")
    if not isinstance(value, dict) or name not in value:
        raise ApiError(schema, None, f"응답에 {name} 가 없다")
    return value[name]


@dataclass(frozen=True)
class EventArticle:
    article_id: int
    published_at: datetime
    title: str | None
    """기사 정보가 없으면(그새 지운 기사) None."""
    sources: tuple[str, ...]


@dataclass(frozen=True)
class EventDetail:
    event_id: int
    status: str
    articles: tuple[EventArticle, ...]


@dataclass(frozen=True)
class AssignmentCriteria:
    window_seconds: int
    upper: Decimal
    lower: Decimal
    store: Decimal
    probability: Decimal


@dataclass(frozen=True)
class StorylineList:
    """기간의 스토리라인 ID(목록 순서)와 지금 잇기 · 관계 기준(응답 그대로 — 결과 파일 meta.linking)."""

    ids: tuple[int, ...]
    linking: dict[str, object]
    relation: dict[str, object]


@dataclass(frozen=True)
class RelationJudgement:
    """관계 판정 원본 — 확률의 방향은 first · second 기준. 읽지 못했으면(reading 이 READ 가 아님) 확률이 None."""

    judgement_id: int
    first: int
    second: int
    model_version: str
    reading: str
    probabilities: tuple[Decimal, ...] | None
    """ANSWERS 순서의 답 다섯."""
    first_one_matter: Decimal | None
    second_one_matter: Decimal | None


@dataclass(frozen=True)
class StorylineRelation:
    kind: str
    source: int
    target: int
    judgement: RelationJudgement | None


@dataclass(frozen=True)
class AskedPair:
    """물은 쌍 하나 — 상대와 마지막으로 물은 결말. 원본 ID 가 없으면 원본을 남기지 않는 결말, 있는데 원본이 없으면 지워진 원본."""

    other: int
    smaller_events: int
    outcome: str
    asked_at: datetime
    judgement_id: int | None
    judgement: RelationJudgement | None


@dataclass(frozen=True)
class StorylineDetail:
    """스토리라인 하나 — 남는 스토리라인 ID, 직접 가진 사건, 관계, 물은 쌍."""

    storyline_id: int
    direct_events: tuple[int, ...]
    relations: tuple[StorylineRelation, ...]
    asked: tuple[AskedPair, ...]


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """리디렉션을 따르지 않는다 — 따르면 Authorization 이 리디렉션 대상(다른 호스트 포함)에 실린다."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):  # urllib 의 확장 지점
        return None


def default_opener() -> Callable:
    return urllib.request.build_opener(_NoRedirect()).open


class AdminApi:
    def __init__(self, base_url: str, token: str, *, open_url: Callable | None = None, timeout: float = 30) -> None:
        if not token:
            raise ValueError("운영자 토큰이 비었다")
        self._base = base_url.rstrip("/")
        self._token = token
        self._open = open_url or default_opener()
        self._timeout = timeout

    def event_ids(self, start: date, end: date, zone: str) -> list[int]:
        """기간 안 기사가 있는 사건 ID — 목록을 커서로 끝까지 읽는다. 같은 ID 가 두 번 오면 ApiError."""
        ids: list[int] = []
        seen: set[int] = set()
        cursor: dict[str, str] = {}
        while True:
            page = self._get(_EVENTS, {"from": start.isoformat(), "to": end.isoformat(), "zone": zone, **cursor})
            for row in field(page, "EventListResponse", "events"):
                event_id = int(field(row, "EventRowResponse", "id"))
                if event_id in seen:
                    raise ApiError(_EVENTS, None, f"사건 {event_id} 가 목록에 두 번 나왔다 — 사건 묶기가 도는 중이면 끝난 뒤 다시 잰다")
                seen.add(event_id)
                ids.append(event_id)
            following = field(page, "EventListResponse", "next")
            if following is None:
                return ids
            cursor = {"afterLastArticleAt": str(field(following, "EventCursorResponse", "lastArticleAt")),
                      "afterId": str(field(following, "EventCursorResponse", "id"))}

    def event_articles(self, event_id: int) -> EventDetail:
        """사건 하나의 배정 기사. 합쳐진 사건이면 응답은 남는 사건이다(event_id 가 요청과 다르다)."""
        detail = self._get(f"{_EVENTS}/{event_id}", {})
        articles = []
        for row in field(detail, "EventDetailResponse", "articles"):
            summary = field(row, "EventArticleResponse", "article")
            articles.append(EventArticle(
                article_id=int(field(row, "EventArticleResponse", "articleId")),
                published_at=parse_instant(str(field(row, "EventArticleResponse", "publishedAt"))),
                title=None if summary is None else str(field(summary, "ArticleSummaryResponse", "title")),
                sources=() if summary is None else tuple(
                    str(source) for source in field(summary, "ArticleSummaryResponse", "sources"))))
        return EventDetail(int(field(detail, "EventDetailResponse", "id")),
                           str(field(detail, "EventDetailResponse", "status")), tuple(articles))

    def assignment_criteria(self, day: date, zone: str) -> AssignmentCriteria:
        """지금 앱의 사건 배정 기준 — 판정 기록 응답이 함께 낸다(기간과 무관한 지금 값)."""
        page = self._get(_JUDGEMENTS, {"from": day.isoformat(), "to": day.isoformat(), "zone": zone})
        criteria = field(page, "EventJudgementListResponse", "criteria")
        schema = "AssignmentCriteriaResponse"
        return AssignmentCriteria(int(field(criteria, schema, "windowSeconds")),
                                  _decimal(field(criteria, schema, "upper")),
                                  _decimal(field(criteria, schema, "lower")),
                                  _decimal(field(criteria, schema, "store")),
                                  _decimal(field(criteria, schema, "probability")))

    def judgement_models(self, article_ids: set[int], start: date, end: date, zone: str) -> set[str]:
        """start ~ end 를 하루씩(판정 시각 기준 — 기간 상한에 걸리지 않게) 판정 기록을 끝까지 읽어, article_ids 의 행에 적힌
        판정 모델 판을 모은다."""
        models: set[str] = set()
        for offset in range((end - start).days + 1):
            day = start + timedelta(days=offset)
            cursor: dict[str, str] = {}
            passed: set[tuple[str, ...]] = set()
            while True:
                page = self._get(_JUDGEMENTS, {"from": day.isoformat(), "to": day.isoformat(), "zone": zone, **cursor})
                for row in field(page, "EventJudgementListResponse", "judgements"):
                    if int(field(row, "EventJudgementRowResponse", "articleId")) in article_ids:
                        models.add(str(field(row, "EventJudgementRowResponse", "modelVersion")))
                following = field(page, "EventJudgementListResponse", "next")
                if following is None:
                    break
                cursor = {"afterJudgedAt": str(field(following, "JudgementCursorResponse", "judgedAt")),
                          "afterId": str(field(following, "JudgementCursorResponse", "id"))}
                if tuple(cursor.values()) in passed:
                    raise ApiError(_JUDGEMENTS, None, f"{day} 판정 기록의 다음 쪽 커서가 나아가지 않는다")
                passed.add(tuple(cursor.values()))
        return models

    def storylines(self, start: date, end: date, zone: str) -> StorylineList:
        """기간의 스토리라인 — 목록을 커서로 끝까지 읽는다. 같은 ID 가 두 번 오면 ApiError. 기준은 첫 쪽의 값이다."""
        ids: list[int] = []
        seen: set[int] = set()
        cursor: dict[str, str] = {}
        first: object = None
        while True:
            page = self._get(_STORYLINES, {"from": start.isoformat(), "to": end.isoformat(), "zone": zone, **cursor})
            first = page if first is None else first
            for row in field(page, "StorylineListResponse", "storylines"):
                storyline_id = int(field(row, "StorylineRowResponse", "id"))
                if storyline_id in seen:
                    raise ApiError(_STORYLINES, None,
                                   f"스토리라인 {storyline_id} 가 목록에 두 번 나왔다 — 잇기가 도는 중이면 끝난 뒤 다시 낸다")
                seen.add(storyline_id)
                ids.append(storyline_id)
            following = field(page, "StorylineListResponse", "next")
            if following is None:
                break
            cursor = {"afterLastArticleAt": str(field(following, "StorylineCursorResponse", "lastArticleAt")),
                      "afterId": str(field(following, "StorylineCursorResponse", "id"))}
        linking = field(first, "StorylineListResponse", "linkingCriteria")
        relation = field(first, "StorylineListResponse", "relationCriteria")
        return StorylineList(
            tuple(ids),
            {name: json_number(field(linking, "LinkingCriteriaResponse", name))
             for name in USED_FIELDS["LinkingCriteriaResponse"]},
            {name: json_number(field(relation, "RelationCriteriaResponse", name))
             for name in USED_FIELDS["RelationCriteriaResponse"]})

    def storyline_detail(self, storyline_id: int) -> StorylineDetail:
        """스토리라인 하나 — 합쳐진 스토리라인이면 응답은 남는 스토리라인이다(storyline_id 가 요청과 다르다)."""
        detail = self._get(f"{_STORYLINES}/{storyline_id}", {})
        schema = "StorylineDetailResponse"
        direct = tuple(int(field(event, "StorylineEventResponse", "id"))
                       for event in field(detail, schema, "events")
                       if field(event, "StorylineEventResponse", "direct") is True)
        relations = tuple(StorylineRelation(
            str(field(relation, "StorylineRelationResponse", "kind")),
            int(field(relation, "StorylineRelationResponse", "sourceId")),
            int(field(relation, "StorylineRelationResponse", "targetId")),
            _judgement(field(relation, "StorylineRelationResponse", "judgement")))
            for relation in field(detail, schema, "relations"))
        asked = []
        for pair in field(detail, schema, "asked"):
            judgement_id = field(pair, "StorylineAskedResponse", "judgementId")
            asked.append(AskedPair(int(field(pair, "StorylineAskedResponse", "otherId")),
                                   int(field(pair, "StorylineAskedResponse", "smallerEvents")),
                                   str(field(pair, "StorylineAskedResponse", "outcome")),
                                   parse_instant(str(field(pair, "StorylineAskedResponse", "askedAt"))),
                                   None if judgement_id is None else int(judgement_id),
                                   _judgement(field(pair, "StorylineAskedResponse", "judgement"))))
        return StorylineDetail(int(field(detail, schema, "id")), direct, relations, tuple(asked))

    def big_storylines(self, day: date, zone: str) -> list[int]:
        """그날 하루의 큰 스토리라인 ID — 지금 큰 판정 기준."""
        page = self._get(_BIG_STORYLINES, {"from": day.isoformat(), "to": day.isoformat(), "zone": zone})
        return [int(field(row, "BigStorylineRowResponse", "id"))
                for row in field(page, "BigStorylineListResponse", "storylines")]

    def _get(self, path: str, query: Mapping[str, str]) -> object:
        url = self._base + path + ("?" + urllib.parse.urlencode(query) if query else "")
        return self._send(path, urllib.request.Request(url, method="GET"))

    def _send(self, path: str, request: urllib.request.Request) -> object:
        request.add_header("Authorization", f"Bearer {self._token}")
        request.add_header("Accept", "application/json")
        try:
            with self._open(request, timeout=self._timeout) as response:
                return json.loads(response.read().decode("utf-8"), parse_float=Decimal)
        except urllib.error.HTTPError as error:
            with error:
                detail = _problem_detail(error)
            raise ApiError(path, error.code, detail) from None
        except urllib.error.URLError as error:
            raise ApiError(path, None, f"연결 실패 — {error.reason}") from None
        except (OSError, http.client.HTTPException) as error:
            raise ApiError(path, None, f"응답을 끝까지 받지 못했다 — {type(error).__name__}") from None
        except (ValueError, UnicodeDecodeError) as error:
            raise ApiError(path, None, f"JSON 이 아닌 응답 — {type(error).__name__}") from None


def _problem_detail(error: urllib.error.HTTPError) -> str:
    if error.code in (401, 403):
        return "운영자 토큰을 확인한다"
    if 300 <= error.code < 400:
        return "리디렉션을 따르지 않는다 — --base-url 을 확인한다"
    if 400 <= error.code < 500 and "json" in (error.headers.get("Content-Type") or ""):
        try:
            detail = json.loads(error.read().decode("utf-8")).get("detail")
        except (ValueError, UnicodeDecodeError, AttributeError):
            detail = None
        if isinstance(detail, str) and detail:
            return detail
    return error.reason if isinstance(error.reason, str) else "요청이 실패했다"


def _decimal(value: object) -> Decimal:
    return value if isinstance(value, Decimal) else Decimal(str(value))


def _judgement(value: object) -> RelationJudgement | None:
    """판정 원본 — None(원본 없음)이면 None."""
    if value is None:
        return None
    schema = "RelationJudgementResponse"
    reading = str(field(value, schema, "reading"))
    read = reading == "READ"

    def probability(name: str) -> Decimal | None:
        found = field(value, schema, name)
        return None if found is None else _decimal(found)

    return RelationJudgement(int(field(value, schema, "id")), int(field(value, schema, "firstId")),
                             int(field(value, schema, "secondId")), str(field(value, schema, "modelVersion")), reading,
                             tuple(_decimal(field(value, schema, name)) for name in ANSWERS) if read else None,
                             probability("firstOneMatter"), probability("secondOneMatter"))


def json_number(value: object) -> int | float:
    """응답의 수를 기록 · 결과 파일에 다시 쓸 꼴로 — 정수로 온 값은 int, 소수로 온 값은 float."""
    number = _decimal(value)
    return int(number) if number == number.to_integral_value() and "." not in str(number) else float(number)
