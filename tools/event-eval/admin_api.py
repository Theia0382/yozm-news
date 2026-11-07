"""운영자 API 읽기 — 사건 · 큰 사건 · 판정 기록.

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
    "BigEventListResponse": ("criteria", "events"),
    "BigEventPreviewResponse": ("proposed", "proposedEvents"),
    "BigEventRowResponse": ("id",),
    "BigEventCriteriaResponse": ("minSources", "minImportance"),
    "EventJudgementListResponse": ("criteria", "judgements", "next"),
    "EventJudgementRowResponse": ("articleId", "modelVersion"),
    "JudgementCursorResponse": ("judgedAt", "id"),
    "AssignmentCriteriaResponse": ("windowSeconds", "upper", "lower", "store", "probability"),
}
"""스키마 이름 → 도구가 읽는 필드."""

_EVENTS: Final = "/api/admin/events"
_BIG_EVENTS: Final = "/api/admin/big-events"
_BIG_PREVIEWS: Final = "/api/admin/big-event-criteria-previews"
_JUDGEMENTS: Final = "/api/admin/event-judgements"


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
class BigCriteria:
    min_sources: int
    min_importance: Decimal
    proposed: bool


@dataclass(frozen=True)
class AssignmentCriteria:
    window_seconds: int
    upper: Decimal
    lower: Decimal
    store: Decimal
    probability: Decimal


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

    def big_events(self, day: date, zone: str,
                   criteria: tuple[int, Decimal] | None) -> tuple[BigCriteria, list[int]]:
        """그 날짜 하루의 큰 사건 ID — criteria 가 없으면 지금 기준, 있으면 그 제안 기준의 미리보기."""
        if criteria is None:
            page = self._get(_BIG_EVENTS, {"from": day.isoformat(), "to": day.isoformat(), "zone": zone})
            used = _big_criteria(field(page, "BigEventListResponse", "criteria"), proposed=False)
            rows = field(page, "BigEventListResponse", "events")
        else:
            page = self._post(_BIG_PREVIEWS, {"from": day.isoformat(), "to": day.isoformat(), "zone": zone,
                                              "minSources": criteria[0], "minImportance": criteria[1]})
            used = _big_criteria(field(page, "BigEventPreviewResponse", "proposed"), proposed=True)
            rows = field(page, "BigEventPreviewResponse", "proposedEvents")
        return used, [int(field(row, "BigEventRowResponse", "id")) for row in rows]

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

    def _get(self, path: str, query: Mapping[str, str]) -> object:
        url = self._base + path + ("?" + urllib.parse.urlencode(query) if query else "")
        return self._send(path, urllib.request.Request(url, method="GET"))

    def _post(self, path: str, body: Mapping[str, object]) -> object:
        data = json.dumps(body, default=_json_default).encode("utf-8")
        request = urllib.request.Request(self._base + path, data=data, method="POST")
        request.add_header("Content-Type", "application/json")
        return self._send(path, request)

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


def _big_criteria(value: object, *, proposed: bool) -> BigCriteria:
    return BigCriteria(int(field(value, "BigEventCriteriaResponse", "minSources")),
                       _decimal(field(value, "BigEventCriteriaResponse", "minImportance")), proposed)


def _decimal(value: object) -> Decimal:
    return value if isinstance(value, Decimal) else Decimal(str(value))


def _json_default(value: object) -> object:
    if isinstance(value, Decimal):
        return float(value)
    raise TypeError(f"JSON 으로 쓸 수 없다: {type(value).__name__}")
