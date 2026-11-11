"""스토리라인 결과 파일(JSON) — 후보 잇기 방법이 내는 결과의 형식과 검증. 형식은 운영 안내
docs/operations/storyline-evaluation.md 가 설명한다.

시스템의 판정(잇기 · 관계 · 큰 판정)은 결과 파일로 받기만 한다 — 이 도구는 그 규칙을 계산하지 않는다.
"""

from __future__ import annotations

import json
from collections.abc import Mapping
from dataclasses import dataclass
from datetime import date
from enum import Enum
from pathlib import Path
from typing import Final

from labels import LabelError, parse_instant
from storyline_labels import Relation, StorylineSample

RESULT_FORMAT: Final = 1

REQUIRED_META: Final = ("lineage", "dump", "method", "createdAt", "eventCriteria", "embeddingModel", "judgementModels",
                        "linking")
"""결과 파일 meta 의 필수 키 — 사건 묶기 기준 · 모델 판은 결과를 만든 쪽이 적는다(측정 때 읽으면 그때의 값이 된다)."""


class RunKind(Enum):
    SAME_DUMP = "SAME_DUMP"
    """표본의 고정 덤프 위에서 낸 결과 — 사건이 라벨과 같다."""
    OTHER_RUN = "OTHER_RUN"
    """같은 계보 덤프에서 되살린 다른 실행의 결과 — 기사 ID 로만 견준다."""


@dataclass(frozen=True)
class Result:
    meta: Mapping[str, object]
    storylines: Mapping[str, frozenset[int]]
    """스토리라인 ID → 기사 ID."""
    relations: frozenset[tuple[str, str, Relation]]
    big: frozenset[tuple[str, date]]


def read_result(path: Path) -> Result:
    """결과 파일을 읽어 검증한다 — 형식 판 · meta 필수 키 · 스토리라인 ID · 기사 ID · 관계 · 큰 표시. 어긋남을 모두 모아
    LabelError."""
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise LabelError([f"{path}: 읽을 수 없다 — {error}"]) from error
    name = path.name
    if not isinstance(raw, dict):
        raise LabelError([f"{name}: JSON 객체가 아니다"])
    if raw.get("format") != RESULT_FORMAT:
        raise LabelError([f"{name}: 형식 {raw.get('format')!r} 이다 — 이 도구는 {RESULT_FORMAT} 이다"])
    problems: list[str] = []
    meta = _meta(raw.get("meta"), name, problems)
    storylines = _storylines(raw.get("storylines"), name, problems)
    relations = _relations(raw.get("relations"), storylines, name, problems)
    big = _big(raw.get("big"), storylines, name, problems)
    if problems:
        raise LabelError(problems)
    return Result(meta, storylines, relations, big)


def run_kind(result: Result, sample: StorylineSample) -> RunKind:
    """계보 덤프가 다르면 LabelError — 기사 ID 가 다른 기사일 수 있다. 고정 덤프가 같으면 SAME_DUMP, 없거나 다르면
    OTHER_RUN."""
    if result.meta["lineage"] != sample.lineage.sha256:
        raise LabelError([f"결과 파일의 계보 덤프 {result.meta['lineage']} 가 표본의 계보 덤프 {sample.lineage.sha256} "
                          f"와 다르다 — 다른 계보의 결과는 기사 ID 가 다른 기사일 수 있다"])
    return RunKind.SAME_DUMP if result.meta["dump"] == sample.dump.sha256 else RunKind.OTHER_RUN


def _meta(value: object, name: str, problems: list[str]) -> dict[str, object]:
    if not isinstance(value, dict):
        problems.append(f"{name}: meta 가 객체가 아니다")
        return {}
    missing = [key for key in REQUIRED_META if key not in value]
    if missing:
        problems.append(f"{name}: meta 에 {', '.join(missing)} 가 없다")
        return dict(value)
    checks = {
        "lineage": isinstance(value["lineage"], str) and bool(value["lineage"]),
        "dump": value["dump"] is None or (isinstance(value["dump"], str) and bool(value["dump"])),
        "method": isinstance(value["method"], str) and bool(value["method"]),
        "eventCriteria": isinstance(value["eventCriteria"], dict),
        "embeddingModel": isinstance(value["embeddingModel"], str),
        "judgementModels": isinstance(value["judgementModels"], list)
                           and all(isinstance(model, str) for model in value["judgementModels"]),
        "linking": isinstance(value["linking"], dict),
    }
    problems += [f"{name}: meta.{key} 의 꼴이 맞지 않다 — {value[key]!r}" for key, ok in checks.items() if not ok]
    try:
        parse_instant(str(value["createdAt"]))
    except ValueError:
        problems.append(f"{name}: meta.createdAt 을 읽을 수 없다 — {value['createdAt']!r}")
    return dict(value)


def _storylines(value: object, name: str, problems: list[str]) -> dict[str, frozenset[int]]:
    if not isinstance(value, list):
        problems.append(f"{name}: storylines 가 배열이 아니다")
        return {}
    storylines: dict[str, frozenset[int]] = {}
    for index, item in enumerate(value):
        where = f"{name}: storylines[{index}]"
        identifier = item.get("id") if isinstance(item, dict) else None
        articles = item.get("articles") if isinstance(item, dict) else None
        if not isinstance(identifier, str) or not identifier:
            problems.append(f"{where}: id 가 빈 문자열이거나 문자열이 아니다")
            continue
        if identifier in storylines:
            problems.append(f"{where}: id {identifier} 가 둘이다")
            continue
        if not isinstance(articles, list) or not all(type(article) is int for article in articles):
            problems.append(f"{where}: articles 가 기사 ID(정수) 배열이 아니다")
            continue
        if not articles:
            problems.append(f"{where}: 스토리라인 {identifier} 에 기사가 없다")
            continue
        if len(set(articles)) != len(articles):
            problems.append(f"{where}: 스토리라인 {identifier} 에 같은 기사가 둘이다")
            continue
        storylines[identifier] = frozenset(articles)
    return storylines


def _relations(value: object, storylines: Mapping[str, frozenset[int]], name: str,
               problems: list[str]) -> frozenset[tuple[str, str, Relation]]:
    if not isinstance(value, list):
        problems.append(f"{name}: relations 가 배열이 아니다")
        return frozenset()
    relations: set[tuple[str, str, Relation]] = set()
    for index, item in enumerate(value):
        where = f"{name}: relations[{index}]"
        if not isinstance(item, dict):
            problems.append(f"{where}: 객체가 아니다")
            continue
        source, target, kind = item.get("source"), item.get("target"), item.get("kind")
        if not isinstance(kind, str) or kind not in Relation.__members__:
            problems.append(f"{where}: kind 는 {' 이나 '.join(Relation.__members__)} 다 — {kind!r}")
            continue
        unknown = [str(end) for end in (source, target) if not isinstance(end, str) or end not in storylines]
        if unknown:
            problems.append(f"{where}: 스토리라인 {', '.join(unknown)} 가 storylines 에 없다")
            continue
        if source == target:
            problems.append(f"{where}: 스토리라인 {source} 가 자기 자신과 이어졌다")
            continue
        relation = (source, target, Relation[kind])
        if relation in relations:
            problems.append(f"{where}: 같은 관계가 둘이다")
            continue
        relations.add(relation)
    return frozenset(relations)


def _big(value: object, storylines: Mapping[str, frozenset[int]], name: str,
         problems: list[str]) -> frozenset[tuple[str, date]]:
    if not isinstance(value, list):
        problems.append(f"{name}: big 이 배열이 아니다")
        return frozenset()
    marks: set[tuple[str, date]] = set()
    for index, item in enumerate(value):
        where = f"{name}: big[{index}]"
        storyline = item.get("storyline") if isinstance(item, dict) else None
        if not isinstance(storyline, str) or storyline not in storylines:
            problems.append(f"{where}: 스토리라인 {storyline!r} 가 storylines 에 없다")
            continue
        try:
            day = date.fromisoformat(str(item.get("day")))
        except ValueError:
            problems.append(f"{where}: 날짜를 읽을 수 없다 — {item.get('day')!r}")
            continue
        if (storyline, day) in marks:
            problems.append(f"{where}: ({storyline}, {day}) 가 둘이다")
            continue
        marks.add((storyline, day))
    return frozenset(marks)
