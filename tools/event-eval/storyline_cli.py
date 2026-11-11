"""스토리라인 평가 도구의 명령 — sheet · seeds · review · measure. 사용법은 운영 안내 docs/operations/storyline-evaluation.md."""

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
from typing import TextIO
from zoneinfo import ZoneInfo

from admin_api import AdminApi, ApiError
from cli import (EXIT_API, EXIT_MISMATCH, MeasureError, add_base_url_argument, add_data_argument, connect, parse_count,
                 parse_note, period_articles)
from labels import AMBIGUOUS_MARK, SAMPLE_FILE, LabelError, file_digest
from record import write_record
from storyline_labels import (AGAINST_MARK, BIG_FILE, LABELS_FILE, NONE_MARK, RELATIONS_FILE, SEEDS_FILE,
                              SHEET_ARTICLES_FILE, SHEET_FILE, STORYLINE_SEPARATOR, STORYLINES_FILE, Labels, Relation,
                              Seeds, Sheet, SheetArticle, StorylineSample, draw_seeds, new_storyline_sample,
                              read_labels, read_seeds, read_sheet, read_storyline_sample, write_label_templates,
                              write_seeds, write_sheet, write_storyline_sample)
from storyline_metrics import (Universe, View, Weighting, big_precision, build_universe, extended_bcubed,
                               far_pair_recall, gold_view, match_system, relation_scores, seed_storylines,
                               storyline_matches, system_on_sheet)
from storyline_record import ViewMetrics, build_storyline_record
from storyline_result import Result, RunKind, read_result, run_kind


def main(argv: Sequence[str] | None = None, *, environ: Mapping[str, str] = os.environ,
         stdout: TextIO = sys.stdout, stderr: TextIO = sys.stderr,
         clock: Callable[[], datetime] = lambda: datetime.now(UTC), open_url: Callable | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "sheet":
            return _sheet(args, connect(args, environ, open_url), stdout)
        if args.command == "seeds":
            return _seeds(args, stdout)
        if args.command == "review":
            return _review_against(args, stdout) if args.against is not None else _review(args, stdout)
        return _measure(args, clock, stdout)
    except (LabelError, MeasureError) as error:
        print(f"멈춤 — {error}", file=stderr)
        return EXIT_MISMATCH
    except ApiError as error:
        print(f"운영자 API 오류 — {error}", file=stderr)
        return EXIT_API


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="storyline_eval.py",
                                     description="스토리라인 평가 — 시트 · 씨앗 · 라벨 검토 · 측정")
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
    review.add_argument("--against", type=Path,
                        help="결과 파일 — 씨앗 스토리라인마다 대응된 시스템 스토리라인과 갈리는 사건을 보인다")
    measure = commands.add_parser("measure", help="결과 파일을 라벨과 견주어 지표를 재고 결과 기록을 쓴다")
    add_data_argument(measure)
    measure.add_argument("--result", required=True, type=Path, help="후보 방법의 결과 파일(JSON)")
    measure.add_argument("--note", action="append", default=[], type=parse_note, help="기록에 남길 메모 KEY=VALUE(되풀이)")
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
    members = _event_members(gold_view(labels, View.ALL))
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


def _review_against(args: argparse.Namespace, out: TextIO) -> int:
    """씨앗 스토리라인(모든 층 보기)마다 대응된 시스템 스토리라인과 견주어 "시스템만"(시스템이 함께 두었는데 정답은 아닌
    사건) · "정답만"(정답인데 대응된 시스템 스토리라인에 기사가 하나도 없는 사건)을 보인다."""
    comparison = _compare(args.data, args.against)
    universe, system, mapping, gold = comparison.universe, comparison.system, comparison.mapping, comparison.gold
    members = _event_members(gold)
    events = comparison.sheet.events()
    zone = ZoneInfo(comparison.sample.period.zone)
    print(f"대조 {args.against.name} — 실행 종류 {comparison.kind.value}, 시트 밖 기사 {comparison.outside}", file=out)

    def show(event_id: int) -> None:
        articles = events[event_id]
        print(f"    {event_id} · {articles[0].published_at.astimezone(zone):%m-%d %H:%M} · {len(articles)}건 · "
              f"{articles[0].title}", file=out)

    for storyline in seed_storylines(gold, comparison.seeds.events):
        matched = sorted(candidate for candidate, target in mapping.items() if target == storyline)
        together = {universe.event_of[article] for candidate in matched for article in system[candidate]}
        expected = set(members[storyline])
        print(f"== {storyline} {comparison.labels.names[storyline]} · 대응 {', '.join(matched) or '없음'}", file=out)
        for title, chosen in (("시스템만", together - expected), ("정답만", expected - together)):
            print(f"  {title} {len(chosen)}", file=out)
            for event_id in sorted(chosen, key=lambda item: (events[item][0].published_at, item)):
                show(event_id)
    return 0


def _measure(args: argparse.Namespace, clock: Callable[[], datetime], out: TextIO) -> int:
    """운영자 API 를 부르지 않는다 — 라벨 · 시트 · 씨앗 · 결과 파일만으로 잰다."""
    directory: Path = args.data
    comparison = _compare(directory, args.result)
    sample, sheet, seeds, labels = comparison.sample, comparison.sheet, comparison.seeds, comparison.labels
    result, kind, universe, system = comparison.result, comparison.kind, comparison.universe, comparison.system
    outside, mapping = comparison.outside, comparison.mapping
    golds = {view: comparison.gold if view is View.ALL else gold_view(labels, view) for view in View}
    views = {view: ViewMetrics(extended_bcubed(universe, gold, system, seeds.events, Weighting.EVENT),
                               extended_bcubed(universe, gold, system, seeds.events, Weighting.ARTICLE),
                               storyline_matches(universe, gold, system, seeds.events, Weighting.EVENT),
                               far_pair_recall(universe, gold, system, seeds.events, Weighting.EVENT))
             for view, gold in golds.items()}
    relations = relation_scores(labels.relations, result.relations, mapping)
    big = big_precision(sample.period.days(), labels.big, result.big, mapping)
    articles = [article for members in sheet.events().values() for article in members]
    counts = {
        "sheetEvents": len(universe.articles_of),
        "sheetArticles": len(articles),
        "seedEvents": len(seeds.events),
        "seedArticles": sum(len(universe.articles_of[event_id]) for event_id in seeds.events),
        "seedsWithoutStoryline": sum(1 for event_id in seeds.events if not labels.written[event_id]),
        "labeledEvents": len(labels.written),
        "memberEvents": sum(1 for written in labels.written.values() if written),
        "goldStorylines": {view.value: len({label for labels_of in gold.values() for label in labels_of})
                           for view, gold in golds.items()},
        "relations": len(labels.relations),
        "bigMarks": len(labels.big),
        "ambiguousEvents": sum(1 for note in labels.notes.values() if note.startswith(AMBIGUOUS_MARK)),
        "againstEvents": sum(1 for note in labels.notes.values() if note.startswith(AGAINST_MARK)),
        "systemStorylines": len(system),
        "matchedSystemStorylines": len(mapping),
        "resultArticlesOutsideSheet": outside,
        "bySource": dict(sorted(Counter(source for article in articles for source in article.sources).items())),
        "byDay": {day.isoformat(): number for day, number in sorted(
            Counter(sample.period.day_of(article.published_at) for article in articles).items())},
    }
    digests = {name: file_digest(directory / name) for name in (SHEET_FILE, SHEET_ARTICLES_FILE, SEEDS_FILE,
                                                                LABELS_FILE, STORYLINES_FILE, RELATIONS_FILE, BIG_FILE)}
    digests["result"] = file_digest(args.result)
    record = build_storyline_record(sample=sample, digests=digests, result_meta=result.meta, run_kind=kind,
                                    counts=counts, views=views, relations=relations, big=big, notes=dict(args.note),
                                    measured_at=clock())
    path = write_record(directory, record)
    print(f"기록 {path} — 실행 종류 {kind.value}", file=out)
    for view, metrics in views.items():
        estimate = metrics.event
        print(f"{view.value} 확장 B-cubed(사건 가중) P {estimate.precision:.4f} · R {estimate.recall:.4f} · "
              f"F1 {estimate.f1:.4f} [{estimate.f1_interval[0]:.4f}, {estimate.f1_interval[1]:.4f}]", file=out)
    if outside:
        print(f"주의 — 결과 파일의 시트 밖 기사 {outside}", file=out)
    return 0


@dataclass(frozen=True)
class _Comparison:
    """라벨 · 시트 · 씨앗과 결과 파일 하나를 견줄 준비 — measure 와 review --against 가 함께 쓴다. gold 는 모든 층 보기,
    mapping 은 그 보기로 정한 대응(match_system)."""

    sample: StorylineSample
    sheet: Sheet
    seeds: Seeds
    labels: Labels
    result: Result
    kind: RunKind
    universe: Universe
    system: dict[str, frozenset[int]]
    outside: int
    gold: dict[int, frozenset[str]]
    mapping: dict[str, str]


def _compare(directory: Path, result_path: Path) -> _Comparison:
    """라벨을 검증해 읽고, 결과 파일을 읽어 계보를 대조하고(다르면 LabelError), 결과를 시트 기사로 좁혀 대응을 정한다."""
    sample, sheet, seeds, labels = _load(directory)
    result = read_result(result_path)
    kind = run_kind(result, sample)
    universe = build_universe(sheet)
    system, outside = system_on_sheet(universe, result.storylines)
    gold = gold_view(labels, View.ALL)
    return _Comparison(sample, sheet, seeds, labels, result, kind, universe, system, outside, gold,
                       match_system(universe, gold, system))


def _event_members(gold: Mapping[int, frozenset[str]]) -> dict[str, list[int]]:
    """정답 표지 → 그 표지가 든 사건(보기 하나)."""
    members: dict[str, list[int]] = defaultdict(list)
    for event_id, labels in gold.items():
        for storyline in labels:
            members[storyline].append(event_id)
    return members


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
