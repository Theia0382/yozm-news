"""평가 인스턴스의 스토리라인을 결과 파일 · 물은 쌍 파일로 — 운영 안내 docs/operations/storyline-evaluation.md "평가 인스턴스에서
내보내기" · "물은 쌍 파일".

운영자 API 를 읽는 일은 명령(storyline_cli.py export)이 하고, 여기서는 읽은 값을 두 파일의 모양으로 조립하기만 한다.
"""

from __future__ import annotations

import csv
import json
from collections.abc import Iterable, Mapping, Sequence
from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path
from typing import Final

from admin_api import AskedPair, RelationJudgement, StorylineDetail
from labels import LabelError, format_instant
from storyline_labels import StorylineSample
from storyline_result import RESULT_FORMAT

PAIR_COLUMNS: Final = ("low", "high", "smaller_events", "outcome", "asked_at", "judgement_id", "reading", "first",
                       "second", "none", "second_part_of_first", "first_part_of_second", "first_led_to_second",
                       "second_led_to_first", "first_one_matter", "second_one_matter")
"""물은 쌍 파일의 칸 — 답 다섯은 admin_api.ANSWERS 와 같은 순서다."""

READ: Final = "READ"


@dataclass(frozen=True)
class Export:
    result: dict[str, object]
    pairs: list[dict[str, str]]
    dropped: int
    """직접 기사가 하나도 남지 않아 내지 않은 스토리라인 수."""
    unread: int
    """확률이 없는 쌍 수 — 원본이 없거나 지워졌거나 읽지 못했다."""


def build_export(*, sample: StorylineSample, details: Sequence[StorylineDetail],
                 event_articles: Mapping[int, Iterable[int]], big: Mapping[date, Sequence[int]], method: str,
                 embedding_model: str, created_at: datetime, event_criteria: Mapping[str, object],
                 linking: Mapping[str, object], relation: Mapping[str, object], event_models: Iterable[str]) -> Export:
    """details 는 목록 순서의 스토리라인 상세(남는 스토리라인마다 하나), event_articles 는 직접 사건마다 배정 기사 ID, big 은 표본의
    날마다 그날의 큰 스토리라인 ID 다."""
    storylines: list[dict[str, object]] = []
    exported: set[int] = set()
    dropped = 0
    for detail in details:
        articles = sorted({article for event in detail.direct_events for article in event_articles.get(event, ())})
        if not articles:
            dropped += 1
            continue
        storylines.append({"id": str(detail.storyline_id), "articles": articles})
        exported.add(detail.storyline_id)
    kept = [detail for detail in details if detail.storyline_id in exported]
    relations: list[dict[str, str]] = []
    judgements: list[RelationJudgement] = []
    seen: set[tuple[int, int, str]] = set()
    for detail in kept:
        for link in detail.relations:
            key = (link.source, link.target, link.kind)
            if key in seen or link.source not in exported or link.target not in exported:
                continue
            seen.add(key)
            relations.append({"source": str(link.source), "target": str(link.target), "kind": link.kind})
            if link.judgement is not None:
                judgements.append(link.judgement)
    marks = [{"storyline": str(storyline), "day": day.isoformat()}
             for day in sorted(big) for storyline in big[day] if storyline in exported]
    pairs = _pairs(kept)
    # 판정 모델은 내보낸 관계 · 물은 쌍의 원본에 적힌 판이다 — 내보내지 않은 스토리라인과의 관계는 세지 않는다
    judgements += [pair.judgement for detail in kept for pair in detail.asked if pair.judgement is not None]
    models = set(event_models) | {judgement.model_version for judgement in judgements}
    result = {
        "format": RESULT_FORMAT,
        "meta": {
            "lineage": sample.lineage.sha256, "dump": sample.dump.sha256, "method": method,
            "createdAt": format_instant(created_at.replace(microsecond=0)), "eventCriteria": dict(event_criteria),
            "embeddingModel": embedding_model, "judgementModels": sorted(models),
            "linking": {"linking": dict(linking), "relation": dict(relation)},
        },
        "storylines": storylines,
        "relations": relations,
        "big": marks,
    }
    return Export(result, pairs, dropped, sum(1 for pair in pairs if pair["reading"] != READ))


def write_export(export: Export, result_path: Path, pairs_path: Path) -> None:
    """두 파일 가운데 하나라도 있으면 아무것도 쓰지 않고 멈춘다."""
    existing = [str(path) for path in (result_path, pairs_path) if path.exists()]
    if existing:
        raise LabelError([f"{path} 가 이미 있다 — 덮지 않는다" for path in existing])
    with result_path.open("x", encoding="utf-8") as handle:
        json.dump(export.result, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    with pairs_path.open("x", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=PAIR_COLUMNS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(export.pairs)


def _pairs(details: Sequence[StorylineDetail]) -> list[dict[str, str]]:
    """내보낸 스토리라인이 든 물은 쌍 — 쌍마다 한 줄(양쪽 상세에 같은 기록이 있다), (작은 쪽, 큰 쪽) 순."""
    rows: dict[tuple[int, int], dict[str, str]] = {}
    for detail in details:
        for pair in detail.asked:
            low, high = sorted((detail.storyline_id, pair.other))
            rows.setdefault((low, high), _pair_row(low, high, pair))
    return [rows[key] for key in sorted(rows)]


def _pair_row(low: int, high: int, pair: AskedPair) -> dict[str, str]:
    judgement = pair.judgement
    read = judgement is not None and judgement.reading == READ
    probabilities = judgement.probabilities if read and judgement.probabilities is not None else (None,) * 5
    row = {
        "low": str(low), "high": str(high), "smaller_events": str(pair.smaller_events), "outcome": pair.outcome,
        "asked_at": format_instant(pair.asked_at), "judgement_id": _text(pair.judgement_id),
        "reading": "" if judgement is None else judgement.reading,
        "first": "" if judgement is None else str(judgement.first),
        "second": "" if judgement is None else str(judgement.second),
        "first_one_matter": _text(judgement.first_one_matter) if read else "",
        "second_one_matter": _text(judgement.second_one_matter) if read else "",
    }
    for column, probability in zip(PAIR_COLUMNS[9:14], probabilities, strict=True):
        row[column] = _text(probability)
    return row


def _text(value: int | Decimal | None) -> str:
    return "" if value is None else str(value)
