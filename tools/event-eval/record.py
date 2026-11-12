"""결과 기록(JSON) — 형식은 운영 안내 docs/operations/event-evaluation.md 가 설명한다. 기록은 인스턴스에 묶인 데이터라
공개 저장소에 두지 않는다."""

from __future__ import annotations

import json
from collections.abc import Mapping
from datetime import datetime
from decimal import Decimal
from pathlib import Path
from typing import Final

from admin_api import AssignmentCriteria
from labels import LabelError, Sample, format_instant, parse_instant
from metrics import BCubed

RECORD_FORMAT: Final = 3
RESULTS_DIRECTORY: Final = "results"


def build_record(*, sample: Sample, labels_digest: str, counts: Mapping[str, object], criteria: AssignmentCriteria,
                 judgement_models: set[str], embedding_model: str, notes: Mapping[str, str], bcubed: BCubed,
                 measured_at: datetime) -> dict:
    return {
        "format": RECORD_FORMAT,
        "measuredAt": format_instant(measured_at.replace(microsecond=0)),
        "sample": {"start": sample.start.isoformat(), "end": sample.end.isoformat(), "zone": sample.zone,
                   "labelRule": sample.label_rule},
        "labels": {"articlesSha256": labels_digest},
        "counts": dict(counts),
        "settings": {
            "assignment": {"windowSeconds": criteria.window_seconds, "upper": _number(criteria.upper),
                           "lower": _number(criteria.lower), "store": _number(criteria.store),
                           "probability": _number(criteria.probability)},
            "judgementModels": sorted(judgement_models),
            "embeddingModel": embedding_model,
            "notes": dict(notes),
        },
        "bCubed": {"precision": bcubed.precision, "recall": bcubed.recall, "f1": bcubed.f1,
                   "systemEvents": bcubed.system_events, "unassigned": bcubed.unassigned,
                   "goldSingletons": bcubed.gold_singletons, "systemSingletons": bcubed.system_singletons},
    }


def write_record(directory: Path, record: Mapping[str, object]) -> Path:
    """results/<측정 시각 UTC yyyyMMdd-HHmmss>.json 에 쓴다. 이미 있으면 덮지 않는다."""
    measured_at = parse_instant(str(record["measuredAt"]))
    results = directory / RESULTS_DIRECTORY
    results.mkdir(exist_ok=True)
    path = results / f"{measured_at:%Y%m%d-%H%M%S}.json"
    try:
        with path.open("x", encoding="utf-8") as handle:
            json.dump(record, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
    except FileExistsError as error:
        raise LabelError([f"{path} 가 이미 있다 — 덮지 않는다"]) from error
    return path


def _number(value: Decimal) -> int | float:
    return int(value) if value == value.to_integral_value() and "." not in str(value) else float(value)
