"""스토리라인 평가 지표 — 겹치는 묶음(확장 B-cubed · 정답 스토리라인별 대응 · 먼 쌍 재현율), 관계, 큰 판정 정밀도 · 재현율.

정의의 정본은 이 모듈이다(운영 안내 docs/operations/storyline-evaluation.md 가 문장으로 설명한다). 항목은 시트의
기사이고, 정답은 고정 덤프의 사건에 붙인 라벨을 그 사건의 시트 기사로 펼친 것이다. 시스템 결과는 결과 파일로 받기만
한다 — 잇기 규칙을 계산하지 않는다. 결과 파일의 스토리라인은 직접 가진 기사이고, 정답과 같은 규칙으로 CONTAINS 를 따라
위로 닫아 보기마다 좁힌다.
"""

from __future__ import annotations

import random
import statistics
from collections import defaultdict
from collections.abc import Iterable, Mapping
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from enum import Enum
from typing import Final

from metrics import wilson
from storyline_labels import Labels, Relation, Sheet, close_upward, contains_parents

FAR_PAIR_DAYS: Final = 5
"""먼 쌍의 발행 시각 차 하한(일, 포함) — 측정 전 목표."""
MATCH_FLOOR: Final = 0.5
"""대응의 하한 — 시스템 스토리라인 무게 가운데 정답과 겹치는 몫(포함) — 측정 전 목표."""
BOOTSTRAP_ROUNDS: Final = 1000
BOOTSTRAP_SEED: Final = 20261111
"""씨앗 사건 단위 부트스트랩의 횟수 · 씨앗 — 고정해 다시 돌려도 같은 구간이 나온다."""


class Weighting(Enum):
    EVENT = "event"
    """기사마다 1 ÷ 그 사건의 시트 기사 수 — 사건 하나가 한 무게(주 지표)."""
    ARTICLE = "article"
    """기사마다 1(보조)."""


class View(Enum):
    ALL = "ALL"
    """적은 표지와 CONTAINS 상위 전부(위로 닫힘)."""
    TOP = "TOP"
    """위로 닫은 집합에서 상위가 그 집합에 없는 표지."""
    LEAF = "LEAF"
    """위로 닫은 집합에서 하위가 그 집합에 없는 표지 — 사건마다 가장 좁은 소속."""


@dataclass(frozen=True)
class Universe:
    """계산 범위 — 시트의 기사와 그 기사가 든 덤프 사건."""

    event_of: Mapping[int, int]
    published: Mapping[int, datetime]
    articles_of: Mapping[int, tuple[int, ...]]

    def weight(self, article: int, weighting: Weighting) -> float:
        return 1.0 if weighting is Weighting.ARTICLE else 1.0 / len(self.articles_of[self.event_of[article]])


def build_universe(sheet: Sheet) -> Universe:
    events = sheet.events()
    return Universe({article.article_id: article.event_id for members in events.values() for article in members},
                    {article.article_id: article.published_at for members in events.values() for article in members},
                    {event_id: tuple(article.article_id for article in members)
                     for event_id, members in events.items()})


def system_on_sheet(universe: Universe,
                    storylines: Mapping[str, frozenset[int]]) -> tuple[dict[str, frozenset[int]], int]:
    """결과의 스토리라인을 시트 기사로 좁힌다(남는 기사가 없는 스토리라인은 뺀다). 시트 밖 기사(기간 밖 · 배정 안 됨) 수를
    함께 돌려준다."""
    narrowed = {storyline: frozenset(article for article in articles if article in universe.event_of)
                for storyline, articles in storylines.items()}
    outside = {article for articles in storylines.values() for article in articles if article not in universe.event_of}
    return {storyline: articles for storyline, articles in narrowed.items() if articles}, len(outside)


def gold_view(labels: Labels, view: View) -> dict[int, frozenset[str]]:
    """사건 → 그 보기의 정답 표지. labels.csv 에 줄이 있는 사건만(없음이면 빈 집합)."""
    return _view_of(labels.written, labels.relations, view)


def system_view(storylines: Mapping[str, frozenset[int]], relations: Iterable[tuple[str, str, Relation]],
                view: View) -> dict[str, frozenset[int]]:
    """결과의 스토리라인(직접 가진 기사)을 그 보기의 스토리라인 → 기사로. 기사가 직접 든 스토리라인을 CONTAINS 를 따라
    위로 닫고 정답과 같은 규칙으로 좁힌다 — 하위의 기사는 상위에도 든다. 그 보기에서 기사가 남지 않는 스토리라인은
    뺀다. CONTAINS 가 없으면 세 보기가 입력과 같다."""
    direct: dict[int, set[str]] = defaultdict(set)
    for storyline, articles in storylines.items():
        for article in articles:
            direct[article].add(storyline)
    members: dict[str, set[int]] = defaultdict(set)
    for article, chosen in _view_of(direct, relations, view).items():
        for storyline in chosen:
            members[storyline].add(article)
    return {storyline: frozenset(members[storyline]) for storyline in storylines if members[storyline]}


def _view_of(written: Mapping[int, Iterable[str]], relations: Iterable[tuple[str, str, Relation]],
             view: View) -> dict[int, frozenset[str]]:
    """항목 → 그 보기의 표지 — 정답(사건 → 정답 표지)과 시스템(기사 → 스토리라인)이 함께 쓰는 정본. 적은 표지를
    CONTAINS 를 따라 위로 닫고, 최상위는 그 집합에 상위가 없는 것, 말단은 그 집합에 하위가 없는 것이다."""
    relations = list(relations)
    parents = contains_parents(relations)
    children: dict[str, set[str]] = defaultdict(set)
    for label, above in parents.items():
        for parent in above:
            children[parent].add(label)
    result = {}
    for item, labels in written.items():
        closed = close_upward(labels, parents)
        if view is View.ALL:
            result[item] = closed
        elif view is View.TOP:
            result[item] = frozenset(label for label in closed if not parents.get(label, set()) & closed)
        else:
            result[item] = frozenset(label for label in closed if not children.get(label, set()) & closed)
    return result


@dataclass(frozen=True)
class BCubedEstimate:
    """확장 B-cubed — 씨앗 사건의 기사 평균. 구간은 씨앗 사건 단위 부트스트랩의 2.5 · 97.5 백분위."""

    precision: float
    recall: float
    f1: float
    precision_interval: tuple[float, float]
    recall_interval: tuple[float, float]
    f1_interval: tuple[float, float]


def extended_bcubed(universe: Universe, gold: Mapping[int, frozenset[str]], system: Mapping[str, frozenset[int]],
                    seeds: Iterable[int], weighting: Weighting) -> BCubedEstimate:
    """Amigó 외(2009)의 확장 B-cubed. 정답 · 시스템 표지가 없는 기사는 그 덤프 사건 하나의 "없음" 묶음에 든다."""
    labels_of = _gold_tokens(universe, gold)
    clusters_of = _system_tokens(universe, system)
    gold_members = _members(labels_of)
    system_members = _members(clusters_of)
    sums: dict[int, list[float]] = {}
    for event_id in dict.fromkeys(seeds):
        precision_sum = recall_sum = weight_sum = 0.0
        for article in universe.articles_of[event_id]:
            weight = universe.weight(article, weighting)
            precision = _item_score(article, clusters_of, labels_of, system_members, universe, weighting)
            recall = _item_score(article, labels_of, clusters_of, gold_members, universe, weighting)
            precision_sum += weight * precision
            recall_sum += weight * recall
            weight_sum += weight
        sums[event_id] = [precision_sum, recall_sum, weight_sum]
    if not sums:
        raise ValueError("씨앗 사건이 없다")
    precision, recall = _ratio(sums.values())
    rounds = []
    events = list(sums)
    rng = random.Random(BOOTSTRAP_SEED)
    for _ in range(BOOTSTRAP_ROUNDS):
        rounds.append(_ratio(sums[event_id] for event_id in rng.choices(events, k=len(events))))
    return BCubedEstimate(precision, recall, _f1(precision, recall),
                          _interval([value[0] for value in rounds]), _interval([value[1] for value in rounds]),
                          _interval([_f1(*value) for value in rounds]))


@dataclass(frozen=True)
class MatchScore:
    storyline: str
    system: str | None
    precision: float
    recall: float
    f1: float


@dataclass(frozen=True)
class MatchSummary:
    """정답 스토리라인별 대응 — 대상은 씨앗 스토리라인(그 보기에서 씨앗 사건의 정답 표지)."""

    scores: tuple[MatchScore, ...]

    def mean(self) -> tuple[float, float, float] | None:
        if not self.scores:
            return None
        return (statistics.fmean(score.precision for score in self.scores),
                statistics.fmean(score.recall for score in self.scores),
                statistics.fmean(score.f1 for score in self.scores))

    def minimum(self) -> tuple[float, float, float] | None:
        if not self.scores:
            return None
        return (min(score.precision for score in self.scores), min(score.recall for score in self.scores),
                min(score.f1 for score in self.scores))


def seed_storylines(gold: Mapping[int, frozenset[str]], seeds: Iterable[int]) -> list[str]:
    """씨앗 스토리라인 — 그 보기에서 씨앗 사건의 정답 표지(없음 제외), 표지 순."""
    return sorted({label for event_id in seeds for label in gold.get(event_id, frozenset())})


def storyline_matches(universe: Universe, gold: Mapping[int, frozenset[str]], system: Mapping[str, frozenset[int]],
                      seeds: Iterable[int], weighting: Weighting) -> MatchSummary:
    """씨앗 스토리라인 G 마다 무게 |S∩G| 가 가장 큰 시스템 S(같으면 표지 순 앞)로 정밀도 |S∩G| ÷ |S| · 재현율
    |S∩G| ÷ |G|. 겹치는 S 가 없으면 셋 다 0."""
    targets = seed_storylines(gold, seeds)
    members = _gold_articles(universe, gold)
    scores = []
    for storyline in targets:
        target = members[storyline]
        best, overlap = None, 0.0
        for candidate in sorted(system):
            common = _weight(universe, system[candidate] & target, weighting)
            if common > overlap:
                best, overlap = candidate, common
        if best is None:
            scores.append(MatchScore(storyline, None, 0.0, 0.0, 0.0))
            continue
        precision = overlap / _weight(universe, system[best], weighting)
        recall = overlap / _weight(universe, target, weighting)
        scores.append(MatchScore(storyline, best, precision, recall, _f1(precision, recall)))
    return MatchSummary(tuple(scores))


@dataclass(frozen=True)
class Rate:
    rate: float | None
    """쌍이 없으면 None."""
    pairs: int


def far_pair_recall(universe: Universe, gold: Mapping[int, frozenset[str]], system: Mapping[str, frozenset[int]],
                    seeds: Iterable[int], weighting: Weighting) -> Rate:
    """씨앗 기사 a 와 정답 표지를 함께하는 다른 덤프 사건의 기사 a′ 가운데 발행 시각 차가 FAR_PAIR_DAYS 일 이상인 쌍에서,
    시스템이 함께 둔(스토리라인을 함께하는) 쌍의 비율(w(a)·w(a′) 가중)."""
    labels_of = _gold_tokens(universe, gold)
    clusters_of = _system_tokens(universe, system)
    gold_members = _members(labels_of)
    gap = timedelta(days=FAR_PAIR_DAYS)
    together = total = 0.0
    pairs = 0
    for event_id in dict.fromkeys(seeds):
        for article in universe.articles_of[event_id]:
            partners = {other for label in labels_of[article] for other in gold_members[label]}
            for other in partners:
                if universe.event_of[other] == event_id:
                    continue
                if abs(universe.published[other] - universe.published[article]) < gap:
                    continue
                weight = universe.weight(article, weighting) * universe.weight(other, weighting)
                pairs += 1
                total += weight
                if clusters_of[article] & clusters_of[other]:
                    together += weight
    return Rate(together / total if pairs else None, pairs)


def match_system(universe: Universe, gold: Mapping[int, frozenset[str]],
                 system: Mapping[str, frozenset[int]]) -> dict[str, str]:
    """시스템 스토리라인 → 정답 스토리라인(대응된 것만). 정답은 모든 층 보기의 정의된 스토리라인 전부. 사건 가중 자카드가
    가장 큰 정답(같으면 표지 순 앞)이고, 겹침 ÷ 시스템 무게가 MATCH_FLOOR 이상일 때만 대응한다. 관계 · 큰 판정 · 대조
    검토가 이 대응 하나를 쓴다."""
    members = _gold_articles(universe, gold)
    mapping = {}
    for candidate in sorted(system):
        articles = system[candidate]
        best, best_jaccard, best_overlap = None, 0.0, 0.0
        for storyline in sorted(members):
            overlap = _weight(universe, articles & members[storyline], Weighting.EVENT)
            if overlap == 0:
                continue
            jaccard = overlap / _weight(universe, articles | members[storyline], Weighting.EVENT)
            if jaccard > best_jaccard:
                best, best_jaccard, best_overlap = storyline, jaccard, overlap
        if best is not None and best_overlap / _weight(universe, articles, Weighting.EVENT) >= MATCH_FLOOR:
            mapping[candidate] = best
    return mapping


@dataclass(frozen=True)
class RelationScores:
    judged: int
    """양 끝이 서로 다른 정답 스토리라인에 대응된 시스템 관계 수."""
    correct: int
    unmapped: int
    """끝이 대응되지 않아 분모에서 뺀 시스템 관계 수."""
    same_gold: int
    """양 끝이 같은 정답 스토리라인에 대응돼 분모에서 뺀 시스템 관계 수 — 정답 하나를 갈래로 나눠 이은 것이라 정답
    라벨로는 맞는지 가르지 못한다."""
    precision: float | None
    gold: int
    found: int
    recall: float | None
    """정답 관계가 없으면 None."""


def relation_scores(gold_relations: Iterable[tuple[str, str, Relation]],
                    system_relations: Iterable[tuple[str, str, Relation]],
                    mapping: Mapping[str, str]) -> RelationScores:
    gold = set(gold_relations)
    translated = []
    unmapped = same_gold = 0
    for source, target, kind in system_relations:
        if source not in mapping or target not in mapping:
            unmapped += 1
        elif mapping[source] == mapping[target]:
            same_gold += 1
        else:
            translated.append((mapping[source], mapping[target], kind))
    correct = sum(1 for relation in translated if relation in gold)
    found = len(gold & set(translated))
    return RelationScores(len(translated), correct, unmapped, same_gold,
                          correct / len(translated) if translated else None, len(gold), found,
                          found / len(gold) if gold else None)


@dataclass(frozen=True)
class BigScore:
    judged: int
    correct: int
    outside: int
    """표본 밖 날짜라 뺀 시스템 큰 표시 수."""
    precision: float | None
    low: float | None
    high: float | None
    found: int
    """시스템 큰 표시가 찾은 정답 큰 표시 수 — 서로 다른 (정답, 날짜)."""
    gold: int
    recall: float
    by_day: Mapping[date, tuple[int, int]]
    """표본의 날마다 (시스템 큰 표시 수, 정답 큰 표시 수) — 표시가 없는 날은 0."""


def big_precision(days: Iterable[date], gold_big: Iterable[tuple[str, date]], system_big: Iterable[tuple[str, date]],
                  mapping: Mapping[str, str]) -> BigScore | None:
    """시스템 큰 표시 (S, d) 마다 S 가 대응되고 (대응, d) 가 정답 큰 표시면 맞음, 그 밖은 틀림(정답 큰 표시는 날마다
    전수). 재현율은 맞은 시스템 표시가 가리킨 서로 다른 정답 큰 표시 수 ÷ 정답 큰 표시 수 — 표본 안 시스템 표시가 없으면
    0.0 이다. 정답 큰 표시가 없으면 재지 않는다(None)."""
    gold = set(gold_big)
    if not gold:
        return None
    system_by_day = {day: 0 for day in days}
    gold_by_day = dict(system_by_day)
    for _, day in gold:
        gold_by_day[day] += 1
    found: set[tuple[str, date]] = set()
    judged = correct = outside = 0
    for storyline, day in system_big:
        if day not in system_by_day:
            outside += 1
            continue
        judged += 1
        system_by_day[day] += 1
        if storyline in mapping and (mapping[storyline], day) in gold:
            correct += 1
            found.add((mapping[storyline], day))
    by_day = {day: (number, gold_by_day[day]) for day, number in system_by_day.items()}
    recall = len(found) / len(gold)
    if judged == 0:
        return BigScore(0, 0, outside, None, None, None, 0, len(gold), recall, by_day)
    low, high = wilson(correct, judged)
    return BigScore(judged, correct, outside, correct / judged, low, high, len(found), len(gold), recall, by_day)


def _gold_tokens(universe: Universe, gold: Mapping[int, frozenset[str]]) -> dict[int, frozenset[tuple[str, object]]]:
    tokens = {}
    for article, event_id in universe.event_of.items():
        labels = gold.get(event_id, frozenset())
        tokens[article] = frozenset(("gold", label) for label in labels) if labels else frozenset({("none", event_id)})
    return tokens


def _system_tokens(universe: Universe,
                   system: Mapping[str, frozenset[int]]) -> dict[int, frozenset[tuple[str, object]]]:
    found: dict[int, set[tuple[str, object]]] = defaultdict(set)
    for storyline, articles in system.items():
        for article in articles:
            found[article].add(("system", storyline))
    return {article: frozenset(found[article]) if found[article] else frozenset({("none", event_id)})
            for article, event_id in universe.event_of.items()}


def _members(tokens: Mapping[int, frozenset[tuple[str, object]]]) -> dict[tuple[str, object], set[int]]:
    members: dict[tuple[str, object], set[int]] = defaultdict(set)
    for article, labels in tokens.items():
        for label in labels:
            members[label].add(article)
    return members


def _item_score(article: int, own: Mapping[int, frozenset], other: Mapping[int, frozenset],
                own_members: Mapping[object, set[int]], universe: Universe, weighting: Weighting) -> float:
    """own 쪽 묶음을 함께하는 동반 항목 a′ 의 min(|own∩own′|, |other∩other′|) ÷ |own∩own′| 무게 평균 — own 이 시스템이면
    정밀도, 정답이면 재현율."""
    partners = {partner for label in own[article] for partner in own_members[label]}
    total = weight_sum = 0.0
    for partner in partners:
        shared = len(own[article] & own[partner])
        weight = universe.weight(partner, weighting)
        total += weight * min(shared, len(other[article] & other[partner])) / shared
        weight_sum += weight
    return total / weight_sum


def _gold_articles(universe: Universe, gold: Mapping[int, frozenset[str]]) -> dict[str, frozenset[int]]:
    members: dict[str, set[int]] = defaultdict(set)
    for event_id, labels in gold.items():
        for label in labels:
            members[label].update(universe.articles_of[event_id])
    return {label: frozenset(articles) for label, articles in members.items()}


def _weight(universe: Universe, articles: Iterable[int], weighting: Weighting) -> float:
    return sum(universe.weight(article, weighting) for article in articles)


def _ratio(sums: Iterable[list[float]]) -> tuple[float, float]:
    precision = recall = weight = 0.0
    for precision_sum, recall_sum, weight_sum in sums:
        precision += precision_sum
        recall += recall_sum
        weight += weight_sum
    return precision / weight, recall / weight


def _f1(precision: float, recall: float) -> float:
    return 0.0 if precision + recall == 0 else 2 * precision * recall / (precision + recall)


def _interval(values: list[float]) -> tuple[float, float]:
    cuts = statistics.quantiles(values, n=40, method="inclusive")
    return cuts[0], cuts[-1]
