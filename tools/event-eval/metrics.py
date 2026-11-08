"""묶음 정확도(B-cubed)와 큰 사건 판정 정밀도.

시스템의 판정(배정 · 병합 · 큰 사건)은 운영자 API 가 낸 결과를 받기만 한다 — 판정 규칙은 앱의 event 모듈이 정본이고 이
모듈은 그 규칙을 계산하지 않는다.
"""

from __future__ import annotations

import math
from collections import Counter, defaultdict
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from datetime import date
from typing import Final

WILSON_Z: Final = 1.959963984540054
"""95% 구간의 표준 정규 분위수."""


@dataclass(frozen=True)
class BCubed:
    """B-cubed 정밀도 · 재현율 · F1 과 묶음 수.

    system_events 는 라벨 기사가 속한 시스템 묶음 수다 — 배정 없는 기사는 저마다 한 묶음으로 센다.
    단독 묶음 수는 라벨 기사 안에서 센 크기 1 인 묶음 수다.
    """

    precision: float
    recall: float
    f1: float
    articles: int
    gold_events: int
    system_events: int
    unassigned: int
    gold_singletons: int
    system_singletons: int


def b_cubed(gold: Mapping[int, str], system: Mapping[int, int | None]) -> BCubed:
    """gold — 뺀 기사를 제외한 기사 → 정답 표지. system — 같은 기사 → 시스템 사건 ID(None 은 배정 없음 — 그 기사 혼자인
    묶음이고, 배정 없는 기사끼리 묶지 않는다). 키 집합이 다르거나 비었으면 ValueError."""
    if set(gold) != set(system):
        raise ValueError("정답과 시스템의 기사 집합이 다르다")
    if not gold:
        raise ValueError("기사가 없다")
    system_keys: dict[int, object] = {
        article: (("event", event) if event is not None else ("unassigned", article)) for article, event in system.items()}
    gold_members: dict[str, set[int]] = defaultdict(set)
    system_members: dict[object, set[int]] = defaultdict(set)
    for article, label in gold.items():
        gold_members[label].add(article)
        system_members[system_keys[article]].add(article)
    precision_sum = 0.0
    recall_sum = 0.0
    for article, label in gold.items():
        gold_cluster = gold_members[label]
        system_cluster = system_members[system_keys[article]]
        both = len(gold_cluster & system_cluster)
        precision_sum += both / len(system_cluster)
        recall_sum += both / len(gold_cluster)
    count = len(gold)
    precision = precision_sum / count
    recall = recall_sum / count
    return BCubed(
        precision=precision,
        recall=recall,
        f1=2 * precision * recall / (precision + recall),
        articles=count,
        gold_events=len(gold_members),
        system_events=len(system_members),
        unassigned=sum(1 for event in system.values() if event is None),
        gold_singletons=sum(1 for members in gold_members.values() if len(members) == 1),
        system_singletons=sum(1 for members in system_members.values() if len(members) == 1),
    )


@dataclass(frozen=True)
class BigVerdict:
    """시스템이 그 날짜에 큰 사건으로 낸 사건 하나와, 그 사건의 그 날짜 라벨 기사(뺀 기사 제외)의 정답 표지별 수."""

    day: date
    event_id: int
    labeled: Mapping[str, int]


@dataclass(frozen=True)
class BigPrecision:
    """judged 는 시스템 큰 사건 수, unlabeled 는 그 가운데 라벨 기사가 없어 분모에서 뺀 수, tied 는 최다 정답 사건이 둘
    이상이라 틀림으로 센 수다. 분모가 0 이면 정밀도 · 구간이 없다."""

    judged: int
    correct: int
    tied: int
    unlabeled: int
    precision: float | None
    low: float | None
    high: float | None


def big_precision(verdicts: Sequence[BigVerdict], gold_big: set[tuple[str, date]]) -> BigPrecision:
    """맞음 = 라벨 기사의 최다 정답 사건이 하나이고 그 사건이 그 날짜의 정답 큰 사건이다."""
    correct = tied = unlabeled = 0
    for verdict in verdicts:
        counts = Counter({label: number for label, number in verdict.labeled.items() if number > 0})
        if not counts:
            unlabeled += 1
            continue
        top = max(counts.values())
        leaders = [label for label, number in counts.items() if number == top]
        if len(leaders) > 1:
            tied += 1
        elif (leaders[0], verdict.day) in gold_big:
            correct += 1
    total = len(verdicts) - unlabeled
    if total == 0:
        return BigPrecision(len(verdicts), correct, tied, unlabeled, None, None, None)
    low, high = wilson(correct, total)
    return BigPrecision(len(verdicts), correct, tied, unlabeled, correct / total, low, high)


def wilson(successes: int, total: int) -> tuple[float, float]:
    """비율의 95% Wilson 점수 구간."""
    if total <= 0 or not 0 <= successes <= total:
        raise ValueError(f"구간을 셀 수 없다 — {successes}/{total}")
    z2 = WILSON_Z * WILSON_Z
    rate = successes / total
    denominator = 1 + z2 / total
    center = (rate + z2 / (2 * total)) / denominator
    half = WILSON_Z * math.sqrt(rate * (1 - rate) / total + z2 / (4 * total * total)) / denominator
    return max(0.0, center - half), min(1.0, center + half)
