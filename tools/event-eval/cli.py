"""사건 평가 도구의 명령 — sheet · review · measure. 사용법은 운영 안내 docs/operations/event-evaluation.md."""

from __future__ import annotations

import argparse
import os
import random
import sys
from collections import Counter, defaultdict
from collections.abc import Callable, Mapping, Sequence
from dataclasses import dataclass
from datetime import UTC, date, datetime
from pathlib import Path
from typing import Final, TextIO
from zoneinfo import ZoneInfo

from admin_api import TOKEN_ENV, AdminApi, ApiError, EventArticle, EventDetail
from labels import (ARTICLES_FILE, SAMPLE_FILE, ArticleLabel, LabelError, Sample, SheetRow, file_digest,
                    format_instant, format_problems, new_sample, read_labels, read_sample, read_titles, write_sample,
                    write_sheet)
from metrics import b_cubed
from record import build_record, write_record

DEFAULT_BASE_URL: Final = "http://127.0.0.1:8000"
"""배포 구성 proxy 의 기본 공개 주소."""

EXIT_MISMATCH: Final = 2
EXIT_API: Final = 3


class MeasureError(Exception):
    """라벨과 시스템 결과가 어긋난다(한 기사가 두 사건 · 발행 시각 불일치)."""


def main(argv: Sequence[str] | None = None, *, environ: Mapping[str, str] = os.environ,
         stdout: TextIO = sys.stdout, stderr: TextIO = sys.stderr,
         clock: Callable[[], datetime] = lambda: datetime.now(UTC), open_url: Callable | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "sheet":
            return _sheet(args, connect(args, environ, open_url), stdout)
        if args.command == "review":
            return _review(args, stdout)
        return _measure(args, connect(args, environ, open_url), clock, stdout)
    except (LabelError, MeasureError) as error:
        print(f"멈춤 — {error}", file=stderr)
        return EXIT_MISMATCH
    except ApiError as error:
        print(f"운영자 API 오류 — {error}", file=stderr)
        return EXIT_API


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="event_eval.py", description="사건 묶기 평가 — 라벨 시트 · 검토 · 측정")
    commands = parser.add_subparsers(dest="command", required=True)
    sheet = commands.add_parser("sheet", help="기간의 배정된 기사로 라벨 시트를 만든다")
    add_data_argument(sheet)
    sheet.add_argument("--from", dest="start", required=True, type=date.fromisoformat, help="시작일(포함, yyyy-MM-dd)")
    sheet.add_argument("--to", dest="end", required=True, type=date.fromisoformat, help="종료일(포함, yyyy-MM-dd)")
    sheet.add_argument("--zone", required=True, help="날짜를 해석할 시간대(IANA ID)")
    add_base_url_argument(sheet)
    review = commands.add_parser("review", help="정답 사건별 제목 목록을 보인다")
    add_data_argument(review)
    review.add_argument("--sample", type=parse_count, help="기사 2건 이상인 정답 사건을 이만큼 무작위로(+ 애매한 기사)")
    review.add_argument("--seed", type=int, help="--sample 의 씨앗 — 없으면 정해 출력한다")
    measure = commands.add_parser("measure", help="B-cubed 를 재고 결과 기록을 쓴다")
    add_data_argument(measure)
    measure.add_argument("--embedding-model", required=True, help="이 인스턴스의 임베딩 방식 · 모델(기록에 남긴다)")
    measure.add_argument("--note", action="append", default=[], type=parse_note, help="기록에 남길 메모 KEY=VALUE(되풀이)")
    add_base_url_argument(measure)
    return parser


def add_data_argument(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--data", required=True, type=Path, help="인스턴스 쪽 표본 디렉터리(공개 저장소 밖)")


def add_base_url_argument(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help=f"운영자 API 의 주소(기본 {DEFAULT_BASE_URL})")


def parse_count(text: str) -> int:
    try:
        value = int(text)
    except ValueError as error:
        raise argparse.ArgumentTypeError(f"정수가 아니다 — {text}") from error
    if value < 0:
        raise argparse.ArgumentTypeError(f"0 이상이다 — {text}")
    return value


def parse_note(text: str) -> tuple[str, str]:
    key, separator, value = text.partition("=")
    if not separator or not key:
        raise argparse.ArgumentTypeError(f"KEY=VALUE 꼴이 아니다 — {text}")
    return key, value


def connect(args: argparse.Namespace, environ: Mapping[str, str], open_url: Callable | None) -> AdminApi:
    token = environ.get(TOKEN_ENV, "")
    if not token:
        raise LabelError([f"환경 변수 {TOKEN_ENV} 에 운영자 토큰이 없다"])
    return AdminApi(args.base_url, token, open_url=open_url)


def collect(api: AdminApi, sample: Sample) -> dict[int, EventDetail]:
    """표본 기간의 사건 상세 — 합쳐진 사건(요청과 다른 ID 의 응답)이 이미 받은 사건이면 건너뛴다."""
    details: dict[int, EventDetail] = {}
    for event_id in api.event_ids(sample.start, sample.end, sample.zone):
        detail = api.event_articles(event_id)
        details.setdefault(detail.event_id, detail)
    return details


def assignments(details: Mapping[int, EventDetail]) -> dict[int, tuple[int, EventArticle]]:
    """기사 → (사건 ID, 그 사건의 기사). 한 기사가 두 사건에 나오면 MeasureError."""
    placed: dict[int, tuple[int, EventArticle]] = {}
    for detail in details.values():
        for article in detail.articles:
            if article.article_id in placed and placed[article.article_id][0] != detail.event_id:
                raise MeasureError(f"기사 {article.article_id} 가 사건 {placed[article.article_id][0]} · "
                                   f"{detail.event_id} 에 함께 나왔다 — 사건 묶기가 도는 중이면 대기가 빈 뒤 다시 잰다")
            placed[article.article_id] = (detail.event_id, article)
    return placed


@dataclass(frozen=True)
class PeriodArticles:
    """시트에 싣는 기사 — 표본 기간의 사건에 든 기사 가운데 기간 안이고 기사 정보가 있는 것."""

    placed: dict[int, tuple[int, EventArticle]]
    """기사 → (사건 ID, 그 사건의 기사)."""
    removed: int
    """기사 정보가 없어 뺀 기사 수."""
    outside: int
    """기간 밖이라 뺀 기사 수 — 사건 목록은 기간 안 기사가 있는 사건이고, 그 사건의 기간 밖 기사도 응답에 있다."""


def period_articles(api: AdminApi, sample: Sample) -> PeriodArticles:
    """표본 기간의 사건에 든 기사(collect · assignments)에서 기간 밖 기사와 기사 정보가 없는 기사를 뺀다."""
    days = set(sample.days())
    placed: dict[int, tuple[int, EventArticle]] = {}
    removed = outside = 0
    for article_id, (event_id, article) in assignments(collect(api, sample)).items():
        if sample.day_of(article.published_at) not in days:
            outside += 1
        elif article.title is None:
            removed += 1
        else:
            placed[article_id] = (event_id, article)
    return PeriodArticles(placed, removed, outside)


def _sheet(args: argparse.Namespace, api: AdminApi, out: TextIO) -> int:
    directory: Path = args.data
    sample = new_sample(args.start, args.end, args.zone)
    for name in (SAMPLE_FILE, ARTICLES_FILE):
        if (directory / name).exists():
            raise LabelError([f"{directory / name} 가 이미 있다 — 덮지 않는다"])
    articles = period_articles(api, sample)
    rows = sorted((SheetRow(article_id, article.published_at, article.sources, article.title)
                   for article_id, (_, article) in articles.placed.items()),
                  key=lambda row: (row.published_at, row.article_id))
    directory.mkdir(parents=True, exist_ok=True)
    write_sample(directory, sample)
    write_sheet(directory / ARTICLES_FILE, rows)
    print(f"시트 {directory / ARTICLES_FILE}: 기사 {len(rows)}건(기사 정보가 없어 뺀 기사 {articles.removed}건). "
          f"사건에 배정된 기사만 담는다", file=out)
    return 0


def _review(args: argparse.Namespace, out: TextIO) -> int:
    directory: Path = args.data
    sample = read_sample(directory)
    labels = read_labels(directory / ARTICLES_FILE, sample)
    titles = read_titles(directory / ARTICLES_FILE)
    members: dict[str, list[ArticleLabel]] = defaultdict(list)
    for label in labels:
        if label.event is not None:
            members[label.event].append(label)
    if args.sample is None:
        chosen = sorted(members, key=lambda event: (-len(members[event]), event))
    else:
        seed = args.seed if args.seed is not None else random.SystemRandom().randrange(1_000_000)
        print(f"씨앗 {seed}", file=out)
        multi = sorted(event for event, items in members.items() if len(items) >= 2)
        picked = random.Random(seed).sample(multi, min(args.sample, len(multi)))
        ambiguous = sorted({label.event for label in labels if label.ambiguous and label.event is not None})
        chosen = list(dict.fromkeys(picked + ambiguous))
    zone = sample.zone
    for event in chosen:
        items = sorted(members[event], key=lambda label: (label.published_at, label.article_id))
        print(f"== {event} · {len(items)}건", file=out)
        for label in items:
            title, note = titles.get(label.article_id, ("", ""))
            local = label.published_at.astimezone(_zone(zone)).strftime("%m-%d %H:%M")
            marks = f"  ({note})" if note else ""
            print(f"  {local} [{', '.join(label.sources)}] {label.article_id} {title}{marks}", file=out)
    excluded = [label for label in labels if label.event is None and (args.sample is None or label.ambiguous)]
    print(f"== 뺀 기사 {len(excluded)}건" if args.sample is None else f"== 뺀 기사 가운데 애매한 기사 {len(excluded)}건",
          file=out)
    for label in excluded:
        title, note = titles.get(label.article_id, ("", ""))
        print(f"  {label.article_id} {title}" + (f"  ({note})" if note else ""), file=out)
    return 0


def _measure(args: argparse.Namespace, api: AdminApi, clock: Callable[[], datetime], out: TextIO) -> int:
    directory: Path = args.data
    sample = read_sample(directory)
    labels = read_labels(directory / ARTICLES_FILE, sample)
    details = collect(api, sample)
    placed = assignments(details)
    problems = [f"기사 {label.article_id} 의 발행 시각이 라벨 {format_instant(label.published_at)} · "
                f"API {format_instant(placed[label.article_id][1].published_at)} 로 다르다 — 다른 인스턴스의 라벨일 수 있다"
                for label in labels
                if label.article_id in placed and placed[label.article_id][1].published_at != label.published_at]
    if problems:
        raise MeasureError(format_problems(problems))
    kept = [label for label in labels if label.event is not None]
    gold = {label.article_id: label.event for label in kept}
    system = {label.article_id: (placed[label.article_id][0] if label.article_id in placed else None) for label in kept}
    bcubed = b_cubed(gold, system)

    labeled_ids = {label.article_id for label in labels}
    days = set(sample.days())
    unlabeled_system = sum(1 for article_id, (_, article) in placed.items()
                           if article_id not in labeled_ids and sample.day_of(article.published_at) in days)
    closed = sum(1 for event_id in {event for event in system.values() if event is not None}
                 if details[event_id].status == "CLOSED")

    measured_at = clock()
    today = measured_at.astimezone(_zone(sample.zone)).date()
    criteria = api.assignment_criteria(today, sample.zone)
    models = api.judgement_models(set(gold), sample.start, max(today, sample.end), sample.zone)
    counts_record = {
        "articles": len(kept),
        "excluded": len(labels) - len(kept),
        "goldEvents": bcubed.gold_events,
        "bySource": dict(sorted(Counter(source for label in kept for source in label.sources).items())),
        "byDay": {day.isoformat(): number for day, number in sorted(
            Counter(sample.day_of(label.published_at) for label in kept).items())},
        "unlabeledSystemArticles": unlabeled_system,
        "closedSystemEvents": closed,
    }
    record = build_record(sample=sample, labels_digest=file_digest(directory / ARTICLES_FILE), counts=counts_record,
                          criteria=criteria, judgement_models=models, embedding_model=args.embedding_model,
                          notes=dict(args.note), bcubed=bcubed, measured_at=measured_at)
    path = write_record(directory, record)
    print(f"기록 {path}", file=out)
    print(f"B-cubed P {bcubed.precision:.4f} · R {bcubed.recall:.4f} · F1 {bcubed.f1:.4f} — 기사 {bcubed.articles} · "
          f"정답 사건 {bcubed.gold_events} · 시스템 사건 {bcubed.system_events} · 배정 없음 {bcubed.unassigned}", file=out)
    if unlabeled_system or closed or bcubed.unassigned:
        print(f"주의 — 라벨 밖 시스템 기사 {unlabeled_system} · 닫힌 시스템 사건 {closed} · 배정 없는 라벨 기사 "
              f"{bcubed.unassigned}", file=out)
    return 0


def _zone(name: str) -> ZoneInfo:
    return ZoneInfo(name)
