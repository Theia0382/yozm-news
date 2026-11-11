"""인스턴스 디렉터리의 표본 · 라벨 파일 형식과 검증.

파일 형식과 라벨 기준은 운영 안내 docs/operations/event-evaluation.md 가 설명한다. 이 파일들은 인스턴스에 묶인 데이터라
공개 저장소에 두지 않는다.
"""

from __future__ import annotations

import csv
import hashlib
import json
import os
from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from datetime import UTC, date, datetime, timedelta
from io import StringIO
from pathlib import Path
from typing import Final
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

LABEL_RULE_VERSION: Final = 2
"""운영 안내 "라벨 기준"의 판 — 운영 안내의 판 표기와 같아야 한다(test_label_rule.py 가 대조한다)."""

EXCLUDED: Final = "-"
"""articles.csv 의 event 칸 — 뺀 기사."""

SAMPLE_FILE: Final = "sample.json"
ARTICLES_FILE: Final = "articles.csv"
EVENTS_FILE: Final = "events.csv"

ARTICLE_COLUMNS: Final = ("article_id", "published_at", "sources", "title", "event", "note")
EVENT_COLUMNS: Final = ("event", "day", "big", "name")
SOURCE_SEPARATOR: Final = ";"
AMBIGUOUS_MARK: Final = "?"

_MAX_SHOWN: Final = 20


class LabelError(Exception):
    """라벨 파일이나 표본 파일이 형식 · 규칙에 어긋난다. 어긋난 위치(줄 번호 · 기사 ID)를 모두 담는다."""

    def __init__(self, problems: Sequence[str]) -> None:
        self.problems = tuple(problems)
        super().__init__(format_problems(self.problems))


def format_problems(problems: Sequence[str]) -> str:
    """문제 목록을 사람이 읽는 글로 — 앞의 _MAX_SHOWN 건과 나머지 수."""
    shown = list(problems[:_MAX_SHOWN])
    rest = len(problems) - len(shown)
    if rest > 0:
        shown.append(f"… 그 밖 {rest}건")
    return "\n".join(shown)


@dataclass(frozen=True)
class Sample:
    """sample.json — 표본의 기간(시작 · 끝 포함, 날짜는 zone 의 날짜)과 라벨 기준의 판."""

    label_rule: int
    start: date
    end: date
    zone: str

    def days(self) -> list[date]:
        return [self.start + timedelta(days=offset) for offset in range((self.end - self.start).days + 1)]

    def day_of(self, instant: datetime) -> date:
        """시각을 표본 시간대의 날짜로 옮긴다."""
        return instant.astimezone(ZoneInfo(self.zone)).date()

    def to_json(self) -> dict:
        return {"labelRule": self.label_rule, "start": self.start.isoformat(), "end": self.end.isoformat(),
                "zone": self.zone}


def new_sample(start: date, end: date, zone: str, *, label_rule: int = LABEL_RULE_VERSION) -> Sample:
    """label_rule 판(기본은 사건 라벨 기준의 지금 판)으로 새 표본을 정한다. 시작이 끝보다 늦거나 시간대를 모르면 LabelError."""
    problems = []
    if start > end:
        problems.append(f"시작일 {start} 이 종료일 {end} 보다 늦다")
    try:
        ZoneInfo(zone)
    except (ZoneInfoNotFoundError, ValueError):
        problems.append(f"모르는 시간대: {zone}")
    if problems:
        raise LabelError(problems)
    return Sample(label_rule, start, end, zone)


@dataclass(frozen=True)
class SheetRow:
    """라벨 시트에 쓸 기사 한 줄 — event · note 는 빈 칸으로 쓴다."""

    article_id: int
    published_at: datetime
    sources: tuple[str, ...]
    title: str


@dataclass(frozen=True)
class ArticleLabel:
    """articles.csv 한 줄 가운데 측정이 읽는 칸."""

    article_id: int
    published_at: datetime
    sources: tuple[str, ...]
    event: str | None
    ambiguous: bool


@dataclass(frozen=True)
class BigMark:
    """events.csv 한 줄 — 정답 사건이 그 날짜에 큰 사건인가. name 은 라벨러가 적는 사람용 칸이다."""

    event: str
    day: date
    big: bool
    name: str = ""


def format_instant(instant: datetime) -> str:
    """UTC ISO-8601(Z). 소수 초가 있으면 남긴다 — 시트의 발행 시각은 운영자 API 의 값과 같아야 측정이 대조할 수 있다."""
    return instant.astimezone(UTC).isoformat().replace("+00:00", "Z")


def parse_instant(text: str) -> datetime:
    """ISO-8601 시각. 시간대가 없으면 ValueError."""
    value = datetime.fromisoformat(text.strip())
    if value.tzinfo is None:
        raise ValueError(f"시간대 없는 시각: {text}")
    return value.astimezone(UTC)


def read_sample(directory: Path, *, label_rule: int = LABEL_RULE_VERSION) -> Sample:
    """sample.json 의 기간 · 시간대 · 판을 읽는다. 판이 label_rule(기본은 사건 라벨 기준의 지금 판)과 다르면 LabelError."""
    path = directory / SAMPLE_FILE
    if not path.is_file():
        raise LabelError([f"{path} 가 없다 — sheet 로 먼저 만든다"])
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
        sample = Sample(int(raw["labelRule"]), date.fromisoformat(raw["start"]), date.fromisoformat(raw["end"]),
                        str(raw["zone"]))
        ZoneInfo(sample.zone)
    except (OSError, ValueError, KeyError, TypeError, ZoneInfoNotFoundError) as error:
        raise LabelError([f"{path}: 읽을 수 없다 — {error}"]) from error
    if sample.start > sample.end:
        raise LabelError([f"{path}: 시작일이 종료일보다 늦다"])
    if sample.label_rule != label_rule:
        raise LabelError([f"{path}: 라벨 기준 {sample.label_rule}판으로 만든 표본이다 — 이 도구는 {label_rule}판이다"])
    return sample


def write_sample(directory: Path, sample: Sample) -> None:
    """sample.json 을 쓴다. 이미 있으면 덮지 않고 LabelError."""
    write_new(directory / SAMPLE_FILE, json.dumps(sample.to_json(), ensure_ascii=False, indent=2) + "\n")


def write_sheet(path: Path, rows: Sequence[SheetRow]) -> None:
    """라벨 시트를 쓴다. 이미 있으면 라벨을 덮지 않도록 LabelError."""
    lines = csv_text([ARTICLE_COLUMNS] + [
        (str(row.article_id), format_instant(row.published_at), SOURCE_SEPARATOR.join(row.sources), row.title, "", "")
        for row in rows])
    write_new(path, lines)


def read_labels(path: Path, sample: Sample) -> list[ArticleLabel]:
    """articles.csv 를 읽어 검증한다 — 머리줄 · 기사 ID · 발행 시각 · 표본 날짜 · 빈 event · 중복 ID."""
    rows = read_rows(path, ARTICLE_COLUMNS)
    problems: list[str] = []
    labels: list[ArticleLabel] = []
    seen: dict[int, int] = {}
    days = set(sample.days())
    for line, row in rows:
        try:
            article_id = int(row["article_id"])
        except ValueError:
            problems.append(f"{path.name}:{line}: 기사 ID 가 정수가 아니다 — {row['article_id']!r}")
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
        if sample.day_of(published_at) not in days:
            problems.append(f"{path.name}:{line}: 기사 {article_id} 의 발행일 {sample.day_of(published_at)} 이 표본 밖이다")
            continue
        event = row["event"].strip()
        if not event:
            problems.append(f"{path.name}:{line}: 기사 {article_id} 의 event 가 비었다")
            continue
        sources = tuple(part.strip() for part in row["sources"].split(SOURCE_SEPARATOR) if part.strip())
        labels.append(ArticleLabel(article_id, published_at, sources, None if event == EXCLUDED else event,
                                   row["note"].strip().startswith(AMBIGUOUS_MARK)))
    if problems:
        raise LabelError(problems)
    if not labels:
        raise LabelError([f"{path.name}: 기사가 없다"])
    return labels


def read_titles(path: Path) -> dict[int, tuple[str, str]]:
    """검토 화면용 — 기사 ID 별 (제목, note). 측정은 이 칸을 읽지 않는다."""
    titles = {}
    for _, row in read_rows(path, ARTICLE_COLUMNS):
        try:
            titles[int(row["article_id"])] = (row["title"], row["note"].strip())
        except ValueError:
            continue
    return titles


def build_event_days(labels: Iterable[ArticleLabel], sample: Sample, existing: Sequence[BigMark]) -> list[BigMark]:
    """정답 사건 × 기사가 있는 날짜의 줄. 같은 줄의 기존 표시 · 이름은 남기고, 라벨에서 사라진 줄은 버린다."""
    kept = {(mark.event, mark.day): mark for mark in existing}
    pairs = sorted({(label.event, sample.day_of(label.published_at)) for label in labels if label.event is not None})
    return [kept.get(pair, BigMark(pair[0], pair[1], False)) for pair in pairs]


def read_marks(path: Path) -> list[BigMark]:
    """events.csv 를 읽는다 — 형식만 본다(라벨과의 대조는 read_big_marks)."""
    problems: list[str] = []
    marks: list[BigMark] = []
    seen: set[tuple[str, date]] = set()
    for line, row in read_rows(path, EVENT_COLUMNS):
        event = row["event"].strip()
        try:
            day = date.fromisoformat(row["day"].strip())
        except ValueError:
            problems.append(f"{path.name}:{line}: 날짜를 읽을 수 없다 — {row['day']!r}")
            continue
        big = row["big"].strip()
        if big not in ("0", "1"):
            problems.append(f"{path.name}:{line}: big 은 0 이나 1 이다 — {row['big']!r}")
            continue
        if (event, day) in seen:
            problems.append(f"{path.name}:{line}: ({event}, {day}) 줄이 둘이다")
            continue
        seen.add((event, day))
        marks.append(BigMark(event, day, big == "1", row["name"]))
    if problems:
        raise LabelError(problems)
    return marks


def write_marks(path: Path, marks: Sequence[BigMark]) -> None:
    """events.csv 를 쓴다(바꿔 쓰기 — 임시 파일을 쓴 뒤 옮긴다)."""
    replace_file(path, csv_text([EVENT_COLUMNS] + [
        (mark.event, mark.day.isoformat(), "1" if mark.big else "0", mark.name) for mark in marks]))


def read_big_marks(path: Path, labels: Sequence[ArticleLabel], sample: Sample) -> set[tuple[str, date]]:
    """big=1 인 (정답 사건, 날짜). events.csv 와 라벨이 맞지 않으면 LabelError — 라벨을 고친 뒤 events 를 다시 돌린다."""
    if not path.is_file():
        raise LabelError([f"{path} 가 없다 — events 로 먼저 만든다"])
    marks = read_marks(path)
    expected = {(mark.event, mark.day) for mark in build_event_days(labels, sample, [])}
    present = {(mark.event, mark.day) for mark in marks}
    problems = [f"{path.name}: ({event}, {day}) 는 라벨에 없다" for event, day in sorted(present - expected)]
    problems += [f"{path.name}: ({event}, {day}) 줄이 없다 — events 를 다시 돌린다" for event, day in
                 sorted(expected - present)]
    if problems:
        raise LabelError(problems)
    return {(mark.event, mark.day) for mark in marks if mark.big}


def file_digest(path: Path) -> str:
    """파일의 sha256 — 파일 전체를 메모리에 올리지 않고 읽는다(덤프 파일에도 쓴다)."""
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def read_rows(path: Path, columns: Sequence[str]) -> list[tuple[int, dict[str, str]]]:
    """머리줄이 columns 인 CSV 의 (줄 번호, 칸) 목록. 파일이 없거나 머리줄이 다르면 LabelError."""
    if not path.is_file():
        raise LabelError([f"{path} 가 없다"])
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        if tuple(reader.fieldnames or ()) != tuple(columns):
            raise LabelError([f"{path.name}: 머리줄이 {','.join(columns)} 가 아니다"])
        return [(index + 2, {key: (value or "") for key, value in row.items()}) for index, row in enumerate(reader)]


def csv_text(rows: Iterable[Sequence[str]]) -> str:
    """줄들을 CSV 글로 — 줄 끝은 "\\n"."""
    buffer = StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerows(rows)
    return buffer.getvalue()


def write_new(path: Path, text: str) -> None:
    """새 파일로 쓴다. 이미 있으면 덮지 않고 LabelError."""
    try:
        with path.open("x", encoding="utf-8", newline="") as handle:
            handle.write(text)
    except FileExistsError as error:
        raise LabelError([f"{path} 가 이미 있다 — 덮지 않는다"]) from error


def replace_file(path: Path, text: str) -> None:
    """파일을 바꿔 쓴다 — 임시 파일을 쓴 뒤 옮겨, 쓰다 멈춰도 앞의 내용이 반쯤 지워지지 않게 한다."""
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(text, encoding="utf-8", newline="")
    os.replace(temporary, path)
