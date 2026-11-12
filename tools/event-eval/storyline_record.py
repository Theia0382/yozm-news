"""스토리라인 평가의 결과 기록(JSON) — 형식은 운영 안내 docs/operations/storyline-evaluation.md 가 설명한다. 기록은
인스턴스에 묶인 데이터라 공개 저장소에 두지 않는다. 쓰기는 record.write_record(results/<측정 시각>.json, 덮지 않음)."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from datetime import datetime
from typing import Final

from labels import format_instant
from storyline_labels import StorylineSample
from storyline_metrics import (BOOTSTRAP_ROUNDS, BOOTSTRAP_SEED, FAR_PAIR_DAYS, MATCH_FLOOR, BCubedEstimate, BigScore,
                               MatchSummary, Rate, RelationScores, View)
from storyline_result import RunKind

STORYLINE_RECORD_FORMAT: Final = 2


@dataclass(frozen=True)
class ViewMetrics:
    """보기 하나의 겹치는 묶음 지표 — 확장 B-cubed 는 사건 가중(주) · 기사 가중(보조), 대응 · 먼 쌍은 사건 가중."""

    event: BCubedEstimate
    article: BCubedEstimate
    matches: MatchSummary
    far_pairs: Rate


def build_storyline_record(*, sample: StorylineSample, digests: Mapping[str, str], result_meta: Mapping[str, object],
                           run_kind: RunKind, counts: Mapping[str, object], views: Mapping[View, ViewMetrics],
                           relations: RelationScores, big: BigScore | None, notes: Mapping[str, str],
                           measured_at: datetime) -> dict:
    """big 이 None 이면(정답 큰 표시가 없는 표본) metrics.big 이 null 이다."""
    return {
        "format": STORYLINE_RECORD_FORMAT,
        "measuredAt": format_instant(measured_at.replace(microsecond=0)),
        "sample": {"start": sample.period.start.isoformat(), "end": sample.period.end.isoformat(),
                   "zone": sample.period.zone, "labelRule": sample.period.label_rule,
                   "dump": sample.dump.to_json(), "lineage": sample.lineage.to_json()},
        "files": dict(digests),
        "result": {"meta": dict(result_meta), "runKind": run_kind.value},
        "counts": dict(counts),
        "metrics": {
            "views": {view.value: _view(views[view]) for view in View},
            "relations": {"judged": relations.judged, "correct": relations.correct, "unmapped": relations.unmapped,
                          "sameGold": relations.same_gold, "precision": relations.precision, "gold": relations.gold,
                          "found": relations.found, "recall": relations.recall},
            "big": None if big is None else {
                "judged": big.judged, "correct": big.correct, "outside": big.outside, "precision": big.precision,
                "interval": None if big.low is None else [big.low, big.high]},
        },
        "settings": {"farPairDays": FAR_PAIR_DAYS, "matchFloor": MATCH_FLOOR, "bootstrapRounds": BOOTSTRAP_ROUNDS,
                     "bootstrapSeed": BOOTSTRAP_SEED},
        "notes": dict(notes),
    }


def _view(metrics: ViewMetrics) -> dict:
    mean, minimum = metrics.matches.mean(), metrics.matches.minimum()
    return {
        "bCubed": {"event": _bcubed(metrics.event), "article": _bcubed(metrics.article)},
        "matches": {"storylines": len(metrics.matches.scores),
                    "mean": None if mean is None else _triple(mean),
                    "min": None if minimum is None else _triple(minimum)},
        "farPairs": {"recall": metrics.far_pairs.rate, "pairs": metrics.far_pairs.pairs},
    }


def _bcubed(estimate: BCubedEstimate) -> dict:
    return {"precision": estimate.precision, "recall": estimate.recall, "f1": estimate.f1,
            "interval": {"precision": list(estimate.precision_interval), "recall": list(estimate.recall_interval),
                         "f1": list(estimate.f1_interval)}}


def _triple(values: tuple[float, float, float]) -> dict:
    return {"precision": values[0], "recall": values[1], "f1": values[2]}
