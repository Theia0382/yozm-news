"""스토리라인 평가 도구의 명령 — sheet · seeds · review. 사용법은 운영 안내 docs/operations/storyline-evaluation.md."""

from __future__ import annotations

import argparse
import os
import random
import sys
from collections import defaultdict
from collections.abc import Callable, Mapping, Sequence
from datetime import UTC, date, datetime
from pathlib import Path
from typing import TextIO
from zoneinfo import ZoneInfo

from admin_api import AdminApi, ApiError
from cli import (EXIT_API, EXIT_MISMATCH, MeasureError, add_base_url_argument, add_data_argument, connect, parse_count,
                 period_articles)
from labels import AMBIGUOUS_MARK, SAMPLE_FILE, LabelError
from storyline_labels import (AGAINST_MARK, NONE_MARK, SEEDS_FILE, SHEET_ARTICLES_FILE, SHEET_FILE,
                              STORYLINE_SEPARATOR, Labels, Relation, Seeds, Sheet, SheetArticle, StorylineSample,
                              draw_seeds, new_storyline_sample, read_labels, read_seeds, read_sheet,
                              read_storyline_sample, write_label_templates, write_seeds, write_sheet,
                              write_storyline_sample)


def main(argv: Sequence[str] | None = None, *, environ: Mapping[str, str] = os.environ,
         stdout: TextIO = sys.stdout, stderr: TextIO = sys.stderr,
         clock: Callable[[], datetime] = lambda: datetime.now(UTC), open_url: Callable | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "sheet":
            return _sheet(args, connect(args, environ, open_url), stdout)
        if args.command == "seeds":
            return _seeds(args, stdout)
        return _review(args, stdout)
    except (LabelError, MeasureError) as error:
        print(f"멈춤 — {error}", file=stderr)
        return EXIT_MISMATCH
    except ApiError as error:
        print(f"운영자 API 오류 — {error}", file=stderr)
        return EXIT_API


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="storyline_eval.py",
                                     description="스토리라인 평가 — 시트 · 씨앗 · 라벨 검토")
    commands = parser.add_subparsers(dest="command", required=True)
    sheet = commands.add_parser("sheet", help="고정 덤프를 되살린 평가 인스턴스에서 기간의 사건 · 기사로 시트를 만든다")
    add_data_argument(sheet)
    sheet.add_argument("--from", dest="start", required=True, type=date.fromisoformat, help="시작일(포함, yyyy-MM-dd)")
    sheet.add_argument("--to", dest="end", required=True, type=date.fromisoformat, help="종료일(포함, yyyy-MM-dd)")
    sheet.add_argument("--zone", required=True, help="날짜를 해석할 시간대(IANA ID)")
    sheet.add_argument("--dump", required=True, type=Path, help="평가 인스턴스가 되살린 고정 덤프 파일")
    sheet.add_argument("--lineage", required=True, type=Path, help="고정 덤프를 만든 계보 덤프 파일")
    add_base_url_argument(sheet)
    seeds = commands.add_parser("seeds", help="시트에서 씨앗 사건을 뽑고 라벨 틀을 만든다(같은 씨앗 값으로 늘리기)")
    add_data_argument(seeds)
    seeds.add_argument("--count", required=True, type=parse_count, help="씨앗 사건 수(있으면 늘리기만)")
    seeds.add_argument("--seed", type=int, help="씨앗 값 — 없으면 있는 seeds.json 의 값, 그것도 없으면 정해 출력한다")
    review = commands.add_parser("review", help="정답 스토리라인별 구성 사건을 보인다")
    add_data_argument(review)
    return parser


def _sheet(args: argparse.Namespace, api: AdminApi, out: TextIO) -> int:
    directory: Path = args.data
    for name in (SAMPLE_FILE, SHEET_FILE, SHEET_ARTICLES_FILE):
        if (directory / name).exists():
            raise LabelError([f"{directory / name} 가 이미 있다 — 덮지 않는다"])
    sample = new_storyline_sample(args.start, args.end, args.zone, args.dump, args.lineage)
    articles = period_articles(api, sample.period)
    rows = [SheetArticle(event_id, article_id, article.published_at, article.sources, article.title)
            for article_id, (event_id, article) in articles.placed.items()]
    if not rows:
        raise LabelError([f"{sample.period.start} ~ {sample.period.end} 에 사건에 든 기사가 없다"])
    directory.mkdir(parents=True, exist_ok=True)
    write_storyline_sample(directory, sample)
    write_sheet(directory, rows)
    print(f"시트 {directory / SHEET_FILE}: 사건 {len({row.event_id for row in rows})} · 기사 {len(rows)}건"
          f"(기사 정보가 없어 뺀 기사 {articles.removed}건, 기간 밖이라 뺀 기사 {articles.outside}건)", file=out)
    return 0


def _seeds(args: argparse.Namespace, out: TextIO) -> int:
    directory: Path = args.data
    sample = read_storyline_sample(directory)
    sheet = read_sheet(directory, sample)
    existing = read_seeds(directory, sheet) if (directory / SEEDS_FILE).exists() else None
    if args.seed is not None:
        seed = args.seed
    elif existing is not None:
        seed = existing.seed
    else:
        seed = random.SystemRandom().randrange(1_000_000)
    seeds = draw_seeds(sheet, args.count, seed)
    write_seeds(directory, seeds, existing)
    added = write_label_templates(directory, seeds)
    print(f"씨앗 {seed}: 씨앗 사건 {len(seeds.events)}개(시트 사건 {len(sheet.events())}개), "
          f"labels.csv 에 더한 줄 {added}", file=out)
    return 0


def _review(args: argparse.Namespace, out: TextIO) -> int:
    directory: Path = args.data
    sample, sheet, seeds, labels = _load(directory)
    events = sheet.events()
    zone = ZoneInfo(sample.period.zone)
    seed_set = set(seeds.events)
    members: dict[str, list[int]] = defaultdict(list)
    for event_id in labels.written:
        for storyline in labels.closed(event_id):
            members[storyline].append(event_id)
    big_days: dict[str, list[date]] = defaultdict(list)
    for storyline, day in sorted(labels.big):
        big_days[storyline].append(day)

    def first(event_id: int) -> tuple[datetime, int]:
        return events[event_id][0].published_at, event_id

    def show(event_id: int, marker: str) -> None:
        articles = events[event_id]
        note = labels.notes.get(event_id, "")
        print(f"  {'*' if event_id in seed_set else ' '}{marker} {event_id} · "
              f"{articles[0].published_at.astimezone(zone):%m-%d %H:%M} · {len(articles)}건"
              + (f"  ({note})" if note else ""), file=out)
        for article in articles:
            print(f"      {article.published_at.astimezone(zone):%m-%d %H:%M} [{', '.join(article.sources)}] "
                  f"{article.title}", file=out)

    for storyline in sorted(labels.names):
        big = ", ".join(day.isoformat() for day in big_days[storyline]) or "없음"
        print(f"== {storyline} {labels.names[storyline]} · 사건 {len(members[storyline])} · 큰 날짜 {big}", file=out)
        for line in _relation_lines(labels, storyline):
            print(f"  {line}", file=out)
        for event_id in sorted(members[storyline], key=first):
            show(event_id, " " if storyline in labels.written[event_id] else "^")
    none_seeds = sorted((event_id for event_id in seeds.events if not labels.written[event_id]), key=first)
    print(f"== 없음으로 적은 씨앗 {len(none_seeds)}", file=out)
    for event_id in none_seeds:
        show(event_id, " ")
    marked = sorted((event_id for event_id, note in labels.notes.items()
                     if note.startswith((AMBIGUOUS_MARK, AGAINST_MARK))), key=first)
    print(f"== {AMBIGUOUS_MARK} · {AGAINST_MARK} 메모 줄 {len(marked)}", file=out)
    for event_id in marked:
        written = STORYLINE_SEPARATOR.join(sorted(labels.written[event_id])) or NONE_MARK
        print(f"  {event_id} [{written}] {labels.notes[event_id]}", file=out)
    return 0


def _load(directory: Path) -> tuple[StorylineSample, Sheet, Seeds, Labels]:
    sample = read_storyline_sample(directory)
    sheet = read_sheet(directory, sample)
    seeds = read_seeds(directory, sheet)
    return sample, sheet, seeds, read_labels(directory, sheet, seeds, sample)


def _relation_lines(labels: Labels, storyline: str) -> list[str]:
    """스토리라인 하나의 관계 — 포함(하위) · 상위 · 다음(이어지는 흐름) · 앞."""
    words = {(Relation.CONTAINS, True): "포함", (Relation.CONTAINS, False): "상위",
             (Relation.FOLLOWS, True): "다음", (Relation.FOLLOWS, False): "앞"}
    lines = []
    for source, target, kind in sorted(labels.relations, key=lambda item: (item[2].value, item[0], item[1])):
        if source == storyline:
            lines.append(f"{words[(kind, True)]}: {target}")
        elif target == storyline:
            lines.append(f"{words[(kind, False)]}: {source}")
    return lines
