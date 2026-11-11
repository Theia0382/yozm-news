"""스토리라인 평가 디렉터리의 표본 · 시트 · 씨앗 · 라벨 파일 형식과 검증.

파일 형식과 라벨 기준은 운영 안내 docs/operations/storyline-evaluation.md 가 설명한다. 이 파일들은 인스턴스에 묶인
데이터라 공개 저장소에 두지 않는다. 라벨은 고정 덤프의 사건에 붙인다 — 사건 ID 는 그 덤프에서만 뜻이 있다.
"""

from __future__ import annotations

import json
import random
from collections import defaultdict
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from datetime import date, datetime
from enum import Enum
from pathlib import Path
from typing import Final

from labels import (SAMPLE_FILE, SOURCE_SEPARATOR, LabelError, Sample, csv_text, file_digest,
                    format_instant, new_sample, parse_instant, read_rows, read_sample, replace_file, write_new)

STORYLINE_LABEL_RULE_VERSION: Final = 1
"""운영 안내 "라벨 기준"의 판 — 운영 안내의 판 표기와 같아야 한다(test_label_rule.py 가 대조한다)."""

KIND: Final = "storyline"
"""sample.json 의 kind — 사건 평가의 표본과 섞이지 않게 한다."""

SHEET_FILE: Final = "sheet.csv"
SHEET_ARTICLES_FILE: Final = "sheet-articles.csv"
SEEDS_FILE: Final = "seeds.json"
LABELS_FILE: Final = "labels.csv"
STORYLINES_FILE: Final = "storylines.csv"
RELATIONS_FILE: Final = "relations.csv"
BIG_FILE: Final = "big.csv"

SHEET_COLUMNS: Final = ("event_id", "first_published_at", "last_published_at", "articles", "sources", "title")
SHEET_ARTICLE_COLUMNS: Final = ("event_id", "article_id", "published_at", "sources", "title")
LABEL_COLUMNS: Final = ("event_id", "storylines", "note")
STORYLINE_COLUMNS: Final = ("storyline", "name")
RELATION_COLUMNS: Final = ("source", "target", "kind")
BIG_COLUMNS: Final = ("storyline", "day")

STORYLINE_SEPARATOR: Final = ";"
"""labels.csv 의 storylines 칸 — 표지를 잇는 구분자."""

NONE_MARK: Final = "-"
"""labels.csv 의 storylines 칸 — 어느 스토리라인에도 들지 않는 사건(없음). 빈 칸은 아직 붙이지 않은 줄이다."""

AGAINST_MARK: Final = "!"
"""labels.csv 의 note 가 이 표시로 시작하면 대조 검토(review --against)로 고친 줄이다. "?"(AMBIGUOUS_MARK)는 섞인 사건."""


class Relation(Enum):
    CONTAINS = "CONTAINS"
    """source 가 target 을 포함한다 — target 의 구성 사건은 source 에도 든다."""
    FOLLOWS = "FOLLOWS"
    """target 이 source 에 이어진다."""


@dataclass(frozen=True)
class Snapshot:
    """덤프 파일의 이름과 sha256."""

    name: str
    sha256: str

    def to_json(self) -> dict:
        return {"name": self.name, "sha256": self.sha256}


@dataclass(frozen=True)
class StorylineSample:
    """스토리라인 평가의 sample.json — 기간 · 시간대 · 판(period.label_rule 은 STORYLINE_LABEL_RULE_VERSION),
    시트를 만든 고정 덤프와 그 덤프를 만든 계보 덤프."""

    period: Sample
    dump: Snapshot
    lineage: Snapshot

    def to_json(self) -> dict:
        return {"kind": KIND, **self.period.to_json(), "dump": self.dump.to_json(), "lineage": self.lineage.to_json()}


@dataclass(frozen=True)
class SheetArticle:
    """시트의 기사 한 줄 — 고정 덤프에서 그 기사가 든 사건."""

    event_id: int
    article_id: int
    published_at: datetime
    sources: tuple[str, ...]
    title: str


@dataclass(frozen=True)
class Sheet:
    """시트의 기사 전부 — 기간 안 기사만 담고, 기사가 없는 사건은 없다."""

    articles: tuple[SheetArticle, ...]

    def events(self) -> dict[int, tuple[SheetArticle, ...]]:
        """사건 ID 순, 사건 안의 기사는 발행 시각 · 기사 ID 순."""
        grouped: dict[int, list[SheetArticle]] = defaultdict(list)
        for article in self.articles:
            grouped[article.event_id].append(article)
        return {event_id: tuple(sorted(grouped[event_id], key=lambda item: (item.published_at, item.article_id)))
                for event_id in sorted(grouped)}


@dataclass(frozen=True)
class Seeds:
    """씨앗 사건 — 뽑은 순서 그대로."""

    seed: int
    events: tuple[int, ...]


@dataclass(frozen=True)
class Labels:
    """라벨러가 적은 정답. written 은 labels.csv 에 줄이 있는 사건만 담는다(없음이면 빈 집합)."""

    written: Mapping[int, frozenset[str]]
    notes: Mapping[int, str]
    names: Mapping[str, str]
    relations: frozenset[tuple[str, str, Relation]]
    big: frozenset[tuple[str, date]]

    def closed(self, event_id: int) -> frozenset[str]:
        """적은 표지와, CONTAINS 를 거슬러 오른 상위 전부(위로 닫힘). 줄이 없는 사건은 빈 집합."""
        parents: dict[str, set[str]] = defaultdict(set)
        for source, target, kind in self.relations:
            if kind is Relation.CONTAINS:
                parents[target].add(source)
        result: set[str] = set()
        pending = list(self.written.get(event_id, frozenset()))
        while pending:
            storyline = pending.pop()
            if storyline not in result:
                result.add(storyline)
                pending.extend(parents[storyline])
        return frozenset(result)


def new_storyline_sample(start: date, end: date, zone: str, dump: Path, lineage: Path) -> StorylineSample:
    """새 표본 — 기간 · 시간대는 labels.new_sample 이 검증하고, 두 덤프 파일의 sha256 을 센다."""
    period = new_sample(start, end, zone, label_rule=STORYLINE_LABEL_RULE_VERSION)
    problems = [f"덤프 파일 {path} 가 없다" for path in (dump, lineage) if not path.is_file()]
    if problems:
        raise LabelError(problems)
    return StorylineSample(period, Snapshot(dump.name, file_digest(dump)), Snapshot(lineage.name, file_digest(lineage)))


def read_storyline_sample(directory: Path) -> StorylineSample:
    """kind 를 먼저 본다 — 사건 평가의 표본이면 판과 상관없이 멈춘다. 기간 · 시간대 · 판은 labels.read_sample 이 읽고(없거나
    읽을 수 없는 파일도 그쪽이 알린다), 두 덤프 표지를 본다."""
    path = directory / SAMPLE_FILE
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        raw = None
    if isinstance(raw, dict) and raw.get("kind") != KIND:
        raise LabelError([f"{path}: 스토리라인 평가의 표본이 아니다(kind {raw.get('kind')!r}) — "
                          "사건 평가의 표본은 event_eval.py 로 잰다"])
    period = read_sample(directory, label_rule=STORYLINE_LABEL_RULE_VERSION)
    try:
        snapshots = [Snapshot(str(raw[key]["name"]), str(raw[key]["sha256"])) for key in ("dump", "lineage")]
    except (KeyError, TypeError) as error:
        raise LabelError([f"{path}: 읽을 수 없다 — {error}"]) from error
    return StorylineSample(period, *snapshots)


def write_storyline_sample(directory: Path, sample: StorylineSample) -> None:
    """sample.json 을 쓴다. 이미 있으면 덮지 않고 LabelError."""
    write_new(directory / SAMPLE_FILE, json.dumps(sample.to_json(), ensure_ascii=False, indent=2) + "\n")


def write_sheet(directory: Path, rows: Sequence[SheetArticle]) -> None:
    """sheet.csv(사건마다 한 줄 — 첫 기사 시각 순) · sheet-articles.csv(같은 사건 순, 사건 안은 발행 시각 순)를 쓴다.
    이미 있으면 덮지 않고 LabelError."""
    events = Sheet(tuple(rows)).events()
    order = sorted(events, key=lambda event_id: (events[event_id][0].published_at, event_id))
    summary = [SHEET_COLUMNS]
    for event_id in order:
        members = events[event_id]
        sources = sorted({source for article in members for source in article.sources})
        summary.append((str(event_id), format_instant(members[0].published_at),
                        format_instant(members[-1].published_at), str(len(members)), SOURCE_SEPARATOR.join(sources),
                        members[0].title))
    articles = [SHEET_ARTICLE_COLUMNS] + [
        (str(article.event_id), str(article.article_id), format_instant(article.published_at),
         SOURCE_SEPARATOR.join(article.sources), article.title)
        for event_id in order for article in events[event_id]]
    write_new(directory / SHEET_FILE, csv_text(summary))
    write_new(directory / SHEET_ARTICLES_FILE, csv_text(articles))


def read_sheet(directory: Path, sample: StorylineSample) -> Sheet:
    """두 시트 파일을 읽어 검증한다 — 칸 · 정수 · 시각 · 표본 날짜 · 기사 중복, 두 파일의 사건 · 기사 수 일치."""
    path = directory / SHEET_ARTICLES_FILE
    problems: list[str] = []
    articles: list[SheetArticle] = []
    seen: dict[int, int] = {}
    days = set(sample.period.days())
    for line, row in read_rows(path, SHEET_ARTICLE_COLUMNS):
        try:
            event_id, article_id = int(row["event_id"]), int(row["article_id"])
        except ValueError:
            problems.append(f"{path.name}:{line}: 사건 ID · 기사 ID 가 정수가 아니다")
            continue
        if article_id in seen:
            problems.append(f"{path.name}:{line}: 기사 {article_id} 가 {seen[article_id]}줄에도 있다")
            continue
        seen[article_id] = line
        try:
            published_at = parse_instant(row["published_at"])
        except ValueError:
            problems.append(f"{path.name}:{line}: 기사 {article_id} 의 발행 시각을 읽을 수 없다 — {row['published_at']!r}")
            continue
        if sample.period.day_of(published_at) not in days:
            problems.append(f"{path.name}:{line}: 기사 {article_id} 의 발행일 {sample.period.day_of(published_at)} 이 표본 밖이다")
            continue
        sources = tuple(part.strip() for part in row["sources"].split(SOURCE_SEPARATOR) if part.strip())
        articles.append(SheetArticle(event_id, article_id, published_at, sources, row["title"]))
    summary: dict[int, int] = {}
    summary_path = directory / SHEET_FILE
    for line, row in read_rows(summary_path, SHEET_COLUMNS):
        try:
            event_id, count = int(row["event_id"]), int(row["articles"])
        except ValueError:
            problems.append(f"{summary_path.name}:{line}: 사건 ID · 기사 수가 정수가 아니다")
            continue
        if event_id in summary:
            problems.append(f"{summary_path.name}:{line}: 사건 {event_id} 줄이 둘이다")
            continue
        summary[event_id] = count
    if problems:
        raise LabelError(problems)
    sheet = Sheet(tuple(articles))
    counts = {event_id: len(members) for event_id, members in sheet.events().items()}
    if counts != summary:
        differing = sorted(event_id for event_id in counts.keys() | summary.keys()
                           if counts.get(event_id) != summary.get(event_id))
        raise LabelError([f"{SHEET_FILE} 와 {SHEET_ARTICLES_FILE} 의 사건 · 기사 수가 다르다 — 사건 "
                          + ", ".join(str(event_id) for event_id in differing)])
    if not counts:
        raise LabelError([f"{path.name}: 기사가 없다"])
    return sheet


def draw_seeds(sheet: Sheet, count: int, seed: int) -> Seeds:
    """시트의 사건 ID 를 오름차순으로 세워 random.Random(seed) 로 섞고 앞의 count 개를 고른다 — 같은 씨앗이면 count 를
    늘려도 앞부분이 같다."""
    events = sorted(sheet.events())
    if not 1 <= count <= len(events):
        raise LabelError([f"씨앗 수 {count} 는 1 이상 시트의 사건 수 {len(events)} 이하다"])
    random.Random(seed).shuffle(events)
    return Seeds(seed, tuple(events[:count]))


def write_seeds(directory: Path, seeds: Seeds, existing: Seeds | None) -> None:
    """seeds.json 을 쓴다. 이미 있으면 같은 씨앗 값으로 수를 늘릴 때만(앞부분이 같다) 바꿔 쓰고, 아니면 LabelError."""
    path = directory / SEEDS_FILE
    text = json.dumps({"seed": seeds.seed, "events": list(seeds.events)}, indent=2) + "\n"
    if existing is None:
        write_new(path, text)
        return
    if existing.seed != seeds.seed:
        raise LabelError([f"{path}: 씨앗 값이 {existing.seed} 다 — 다른 씨앗 값 {seeds.seed} 으로 바꾸지 않는다"])
    if len(seeds.events) <= len(existing.events) or seeds.events[:len(existing.events)] != existing.events:
        raise LabelError([f"{path}: 씨앗이 {len(existing.events)}개다 — 같은 씨앗 값으로 늘리기만 한다"])
    replace_file(path, text)


def read_seeds(directory: Path, sheet: Sheet) -> Seeds:
    """seeds.json 을 읽는다 — 씨앗 사건이 시트에 있고 중복이 없다."""
    path = directory / SEEDS_FILE
    if not path.is_file():
        raise LabelError([f"{path} 가 없다 — seeds 로 먼저 만든다"])
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
        seeds = Seeds(int(raw["seed"]), tuple(int(event_id) for event_id in raw["events"]))
    except (OSError, ValueError, KeyError, TypeError) as error:
        raise LabelError([f"{path}: 읽을 수 없다 — {error}"]) from error
    events = sheet.events()
    problems = [f"{path.name}: 씨앗 사건 {event_id} 가 시트에 없다" for event_id in seeds.events if event_id not in events]
    if not seeds.events:
        problems.append(f"{path.name}: 씨앗 사건이 없다")
    if len(set(seeds.events)) != len(seeds.events):
        problems.append(f"{path.name}: 씨앗 사건이 겹친다")
    if problems:
        raise LabelError(problems)
    return seeds


def write_label_templates(directory: Path, seeds: Seeds) -> int:
    """라벨 틀 — labels.csv 에 줄이 없는 씨앗 사건의 줄(빈 storylines)을 뒤에 더하고, 나머지 셋은 없을 때만 머리줄로
    만든다. 있는 줄 · 파일은 건드리지 않는다. 더한 줄 수를 돌려준다."""
    path = directory / LABELS_FILE
    rows = [tuple(row[column] for column in LABEL_COLUMNS) for _, row in read_rows(path, LABEL_COLUMNS)] \
        if path.exists() else []
    present = {row[0].strip() for row in rows}
    added = [(str(event_id), "", "") for event_id in seeds.events if str(event_id) not in present]
    if added or not path.exists():
        replace_file(path, csv_text([LABEL_COLUMNS, *rows, *added]))
    for name, columns in ((STORYLINES_FILE, STORYLINE_COLUMNS), (RELATIONS_FILE, RELATION_COLUMNS),
                          (BIG_FILE, BIG_COLUMNS)):
        if not (directory / name).exists():
            write_new(directory / name, csv_text([columns]))
    return len(added)


def read_labels(directory: Path, sheet: Sheet, seeds: Seeds, sample: StorylineSample) -> Labels:
    """네 라벨 파일을 읽어 검증한다. 어긋남을 모두 모아 LabelError."""
    problems: list[str] = []
    names = _read_storylines(directory, problems)
    written, notes = _read_label_rows(directory, sheet, names, problems)
    problems += [f"{LABELS_FILE}: 씨앗 사건 {event_id} 의 줄이 없다" for event_id in seeds.events if event_id not in written]
    relations = _read_relations(directory, names, problems)
    big = _read_big(directory, names, sample, problems)
    if problems:
        raise LabelError(problems)
    cycle = _contains_cycle(relations)
    if cycle:
        raise LabelError([f"{RELATIONS_FILE}: CONTAINS 가 돌아 제자리로 온다 — " + " ⊃ ".join(cycle)])
    labels = Labels(written, notes, names, relations, big)
    members: dict[str, int] = defaultdict(int)
    for event_id in written:
        for storyline in labels.closed(event_id):
            members[storyline] += 1
    small = [f"{STORYLINES_FILE}: 스토리라인 {storyline} 의 구성 사건이 {members[storyline]}개다 — 둘 이상이어야 한다"
             for storyline in sorted(names) if members[storyline] < 2]
    if small:
        raise LabelError(small)
    return labels


def _read_storylines(directory: Path, problems: list[str]) -> dict[str, str]:
    names: dict[str, str] = {}
    for line, row in read_rows(directory / STORYLINES_FILE, STORYLINE_COLUMNS):
        storyline = row["storyline"].strip()
        if not storyline or storyline == NONE_MARK or STORYLINE_SEPARATOR in storyline:
            problems.append(f"{STORYLINES_FILE}:{line}: 표지 {storyline!r} 는 쓸 수 없다 — 비었거나 "
                            f"{NONE_MARK!r} 이거나 {STORYLINE_SEPARATOR!r} 를 담았다")
            continue
        if storyline in names:
            problems.append(f"{STORYLINES_FILE}:{line}: 표지 {storyline} 가 둘이다")
            continue
        names[storyline] = row["name"].strip()
    return names


def _read_label_rows(directory: Path, sheet: Sheet, names: Mapping[str, str],
                     problems: list[str]) -> tuple[dict[int, frozenset[str]], dict[int, str]]:
    events = sheet.events()
    written: dict[int, frozenset[str]] = {}
    notes: dict[int, str] = {}
    lines: dict[int, int] = {}
    for line, row in read_rows(directory / LABELS_FILE, LABEL_COLUMNS):
        where = f"{LABELS_FILE}:{line}"
        try:
            event_id = int(row["event_id"])
        except ValueError:
            problems.append(f"{where}: 사건 ID 가 정수가 아니다 — {row['event_id']!r}")
            continue
        if event_id not in events:
            problems.append(f"{where}: 사건 {event_id} 가 시트에 없다")
            continue
        if event_id in lines:
            problems.append(f"{where}: 사건 {event_id} 가 {lines[event_id]}줄에도 있다")
            continue
        lines[event_id] = line
        text = row["storylines"].strip()
        if not text:
            problems.append(f"{where}: 사건 {event_id} 의 storylines 가 비었다 — 아직 붙이지 않았다")
            continue
        parts = [part.strip() for part in text.split(STORYLINE_SEPARATOR)]
        if NONE_MARK in parts and len(parts) > 1:
            problems.append(f"{where}: 사건 {event_id} 에 {NONE_MARK!r} 와 표지를 함께 적었다")
            continue
        if len(set(parts)) != len(parts) or "" in parts:
            problems.append(f"{where}: 사건 {event_id} 의 표지가 비었거나 겹친다 — {text!r}")
            continue
        unknown = [part for part in parts if part != NONE_MARK and part not in names]
        if unknown:
            problems.append(f"{where}: 사건 {event_id} 의 표지 {', '.join(unknown)} 가 {STORYLINES_FILE} 에 없다")
            continue
        written[event_id] = frozenset() if parts == [NONE_MARK] else frozenset(parts)
        notes[event_id] = row["note"].strip()
    return written, notes


def _read_relations(directory: Path, names: Mapping[str, str],
                    problems: list[str]) -> frozenset[tuple[str, str, Relation]]:
    relations: set[tuple[str, str, Relation]] = set()
    pairs: dict[frozenset[str], int] = {}
    for line, row in read_rows(directory / RELATIONS_FILE, RELATION_COLUMNS):
        where = f"{RELATIONS_FILE}:{line}"
        source, target, kind = row["source"].strip(), row["target"].strip(), row["kind"].strip()
        if kind not in Relation.__members__:
            problems.append(f"{where}: kind 는 {' 이나 '.join(Relation.__members__)} 다 — {kind!r}")
            continue
        unknown = [storyline for storyline in (source, target) if storyline not in names]
        if unknown:
            problems.append(f"{where}: 표지 {', '.join(unknown)} 가 {STORYLINES_FILE} 에 없다")
            continue
        if source == target:
            problems.append(f"{where}: 스토리라인 {source} 가 자기 자신과 이어졌다")
            continue
        pair = frozenset((source, target))
        if pair in pairs:
            problems.append(f"{where}: {source} · {target} 의 관계가 {pairs[pair]}줄에도 있다 — 두 스토리라인의 관계는 하나다")
            continue
        pairs[pair] = line
        relations.add((source, target, Relation[kind]))
    return frozenset(relations)


def _read_big(directory: Path, names: Mapping[str, str], sample: StorylineSample,
              problems: list[str]) -> frozenset[tuple[str, date]]:
    marks: set[tuple[str, date]] = set()
    days = set(sample.period.days())
    for line, row in read_rows(directory / BIG_FILE, BIG_COLUMNS):
        where = f"{BIG_FILE}:{line}"
        storyline = row["storyline"].strip()
        if storyline not in names:
            problems.append(f"{where}: 표지 {storyline!r} 가 {STORYLINES_FILE} 에 없다")
            continue
        try:
            day = date.fromisoformat(row["day"].strip())
        except ValueError:
            problems.append(f"{where}: 날짜를 읽을 수 없다 — {row['day']!r}")
            continue
        if day not in days:
            problems.append(f"{where}: 날짜 {day} 이 표본 밖이다")
            continue
        if (storyline, day) in marks:
            problems.append(f"{where}: ({storyline}, {day}) 줄이 둘이다")
            continue
        marks.add((storyline, day))
    return frozenset(marks)


def _contains_cycle(relations: frozenset[tuple[str, str, Relation]]) -> list[str]:
    """CONTAINS 가 도는 길(처음 표지로 끝난다) — 없으면 빈 목록."""
    children: dict[str, list[str]] = defaultdict(list)
    for source, target, kind in sorted(relations, key=lambda item: (item[0], item[1])):
        if kind is Relation.CONTAINS:
            children[source].append(target)
    done: set[str] = set()

    def visit(storyline: str, path: list[str]) -> list[str]:
        if storyline in path:
            return path[path.index(storyline):] + [storyline]
        if storyline in done:
            return []
        for child in children[storyline]:
            found = visit(child, path + [storyline])
            if found:
                return found
        done.add(storyline)
        return []

    for start in sorted(children):
        found = visit(start, [])
        if found:
            return found
    return []

