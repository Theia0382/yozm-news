"""스토리라인 평가 지표. 기대값은 고정물에서 손으로 셈한 리터럴이다 — 프로덕션 산식을 다시 부르지 않는다.

공통 고정물(원장): 사건 e1(기사 a1 · a2), e2(a3), e3(a4). 정답 G = {e1, e2}, e3 는 없음. 씨앗 e1 · e3. 시스템 S = {a1, a2, a4}.
사건 가중 w: a1 · a2 = 0.5, a3 · a4 = 1.
- a1: 정밀도 = (0.5·1 + 0.5·1 + 1·0) ÷ 2 = 0.5, 재현율 = (0.5 + 0.5 + 0) ÷ 2 = 0.5(a3 은 시스템 없음 묶음 ("없음", e2))
- a4: 정밀도 = (0 + 0 + 1·1) ÷ 2 = 0.5, 재현율 = 1
- 전체(사건 가중): P = (0.5·0.5 + 0.5·0.5 + 1·0.5) ÷ 2 = 0.5, R = (0.25 + 0.25 + 1) ÷ 2 = 0.75, F1 = 0.6
- 사건 단위 대조: e1 정밀도 0.5 · 재현율 0.5, e3 정밀도 0.5 · 재현율 1 — 평균 P 0.5 · R 0.75 로 같다
기사 가중(w = 1): a1 · a2 정밀도 2/3 · 재현율 2/3, a4 정밀도 1/3 · 재현율 1 — P = 5/9, R = 7/9, F1 = 35/54.
"""

import unittest
from datetime import UTC, date, datetime, timedelta

from storyline_labels import Labels, Relation, Sheet, SheetArticle
from storyline_metrics import (View, Weighting, _interval, big_precision, build_universe, extended_bcubed,
                               far_pair_recall, gold_view, match_system, relation_scores, storyline_matches,
                               system_on_sheet, system_view)

START = datetime(2026, 11, 1, tzinfo=UTC)


def sheet_of(events):
    """{사건: [(기사, 시작 뒤 시간)]} — 시간이 없으면 0."""
    return Sheet(tuple(SheetArticle(event_id, article, START + offset, ("동아",), "t")
                       for event_id, members in events.items()
                       for article, offset in (item if isinstance(item, tuple) else (item, timedelta())
                                               for item in members)))


def labels_of(written, relations=(), big=()):
    names = {label for labels in written.values() for label in labels}
    names |= {end for source, target, _ in relations for end in (source, target)}
    return Labels({event_id: frozenset(labels) for event_id, labels in written.items()}, {},
                  {name: "" for name in names}, frozenset(relations), frozenset(big))


LEDGER = build_universe(sheet_of({1: [11, 12], 2: [13], 3: [14]}))
LEDGER_GOLD = {1: frozenset({"G"}), 2: frozenset({"G"}), 3: frozenset()}
LEDGER_SYSTEM = {"S": frozenset({11, 12, 14})}

# A ⊃ B. e1 · e2 는 B, e3 은 A 에 직접, e4 는 C, e5 · e6 은 없음. 기사는 사건마다 하나(10 + 사건)
LAYERED = labels_of({1: {"B"}, 2: {"B"}, 3: {"A"}, 4: {"C"}, 5: set(), 6: set()}, [("A", "B", Relation.CONTAINS)])
LAYERED_UNIVERSE = build_universe(sheet_of({event_id: [10 + event_id] for event_id in range(1, 7)}))


class ExtendedBCubedTest(unittest.TestCase):

    def test_ledger_event_weighting(self):
        estimate = extended_bcubed(LEDGER, LEDGER_GOLD, LEDGER_SYSTEM, [1, 3], Weighting.EVENT)
        self.assertAlmostEqual(estimate.precision, 0.5, places=12)
        self.assertAlmostEqual(estimate.recall, 0.75, places=12)
        self.assertAlmostEqual(estimate.f1, 0.6, places=12)

    def test_ledger_article_weighting(self):
        estimate = extended_bcubed(LEDGER, LEDGER_GOLD, LEDGER_SYSTEM, [1, 3], Weighting.ARTICLE)
        self.assertAlmostEqual(estimate.precision, 5 / 9, places=12)
        self.assertAlmostEqual(estimate.recall, 7 / 9, places=12)
        self.assertAlmostEqual(estimate.f1, 35 / 54, places=12)

    def test_unlabeled_event_is_its_own_none_cluster(self):
        # e3 의 줄이 없어도(라벨 파일에 없는 사건) 없음과 같다 — 대조: 결과가 위 원장과 같다
        gold = {1: frozenset({"G"}), 2: frozenset({"G"})}
        estimate = extended_bcubed(LEDGER, gold, LEDGER_SYSTEM, [1, 3], Weighting.EVENT)
        self.assertAlmostEqual(estimate.precision, 0.5, places=12)
        self.assertAlmostEqual(estimate.recall, 0.75, places=12)

    def test_overlapping_membership(self):
        # e1(a1) 은 G · H 둘, e2(a2) 는 G, e3(a3) 은 H. 씨앗 e1. 시스템 S1 = {a1, a2}, S2 = {a3}
        # a1 정밀도: a1 → min(1, 2) ÷ 1 = 1, a2 → min(1, 1) ÷ 1 = 1 — 1
        # a1 재현율: a1 → min(1, 2) ÷ 2 = 0.5, a2 → min(1, 1) ÷ 1 = 1, a3 → min(0, 1) ÷ 1 = 0 — 1.5 ÷ 3 = 0.5
        universe = build_universe(sheet_of({1: [1], 2: [2], 3: [3]}))
        gold = {1: frozenset({"G", "H"}), 2: frozenset({"G"}), 3: frozenset({"H"})}
        estimate = extended_bcubed(universe, gold, {"S1": frozenset({1, 2}), "S2": frozenset({3})}, [1],
                                   Weighting.EVENT)
        self.assertAlmostEqual(estimate.precision, 1.0, places=12)
        self.assertAlmostEqual(estimate.recall, 0.5, places=12)
        self.assertAlmostEqual(estimate.f1, 2 / 3, places=12)

    def test_bootstrap_is_reproducible_and_collapses_on_one_seed(self):
        first = extended_bcubed(LEDGER, LEDGER_GOLD, LEDGER_SYSTEM, [1, 3], Weighting.EVENT)
        self.assertEqual(first, extended_bcubed(LEDGER, LEDGER_GOLD, LEDGER_SYSTEM, [1, 3], Weighting.EVENT))
        # 씨앗 둘의 재현율이 0.5 · 1 로 달라 구간이 벌어진다 — 아래 단언의 대조 leg
        self.assertLess(first.recall_interval[0], first.recall_interval[1])
        # 씨앗 둘(재현율 0.5 · 1)을 복원 추출하면 표본 재현율은 0.5 · 0.75 · 1(확률 1/4 · 1/2 · 1/4) — 양 끝 2.5% 는 두 끝값
        self.assertEqual(first.recall_interval, (0.5, 1.0))
        single = extended_bcubed(LEDGER, LEDGER_GOLD, LEDGER_SYSTEM, [3], Weighting.EVENT)
        self.assertEqual((single.precision, single.recall), (0.5, 1.0))
        self.assertEqual(single.precision_interval, (0.5, 0.5))
        self.assertEqual(single.recall_interval, (1.0, 1.0))


class IntervalTest(unittest.TestCase):

    def test_interval_is_the_2_5_and_97_5_percentiles(self):
        # 0 ~ 40 의 선형 보간 백분위: 2.5% → 40 × 0.025 = 1, 97.5% → 40 × 0.975 = 39
        self.assertEqual(_interval([float(value) for value in range(41)]), (1.0, 39.0))


class ViewTest(unittest.TestCase):

    def test_three_views(self):
        self.assertEqual(gold_view(LAYERED, View.ALL), {1: {"A", "B"}, 2: {"A", "B"}, 3: {"A"}, 4: {"C"}, 5: set(),
                                                        6: set()})
        self.assertEqual(gold_view(LAYERED, View.TOP), {1: {"A"}, 2: {"A"}, 3: {"A"}, 4: {"C"}, 5: set(), 6: set()})
        # 상위에만 직접 든 e3 은 그 상위가 말단 소속이다
        self.assertEqual(gold_view(LAYERED, View.LEAF), {1: {"B"}, 2: {"B"}, 3: {"A"}, 4: {"C"}, 5: set(), 6: set()})


class SystemViewTest(unittest.TestCase):

    # P ⊃ Q ⊃ R, P ⊃ T. 직접 가진 기사: P = {1}, Q = {2}, R = {3}, T = {4, 2}(기사 2 는 Q 와 T 에 직접), U = {5}
    DIRECT = {"P": frozenset({1}), "Q": frozenset({2}), "R": frozenset({3}), "T": frozenset({4, 2}), "U": frozenset({5})}
    CONTAINS = [("P", "Q", Relation.CONTAINS), ("Q", "R", Relation.CONTAINS), ("P", "T", Relation.CONTAINS),
                ("U", "P", Relation.FOLLOWS)]

    def test_a_child_s_articles_are_in_every_ancestor(self):
        self.assertEqual(system_view(self.DIRECT, self.CONTAINS, View.ALL),
                         {"P": {1, 2, 3, 4}, "Q": {2, 3}, "R": {3}, "T": {4, 2}, "U": {5}})

    def test_top_keeps_only_the_storylines_without_a_parent(self):
        # 하위는 기사가 남지 않아 빠진다. 이어짐(U → P)은 닫지 않는다
        self.assertEqual(system_view(self.DIRECT, self.CONTAINS, View.TOP), {"P": {1, 2, 3, 4}, "U": {5}})

    def test_leaf_keeps_each_article_in_its_narrowest_storylines(self):
        # 기사 1 은 P 에만 직접이라 P 가 말단 소속이다. 기사 2 는 Q · T 둘이 말단이다
        self.assertEqual(system_view(self.DIRECT, self.CONTAINS, View.LEAF),
                         {"P": {1}, "Q": {2}, "R": {3}, "T": {4, 2}, "U": {5}})

    def test_a_child_with_two_parents_is_in_both(self):
        system = system_view({"A": frozenset({1}), "B": frozenset({2}), "C": frozenset({3})},
                             [("A", "C", Relation.CONTAINS), ("B", "C", Relation.CONTAINS)], View.ALL)
        self.assertEqual(system, {"A": {1, 3}, "B": {2, 3}, "C": {3}})

    def test_a_result_without_containment_is_the_same_in_every_view(self):
        direct = {"S": frozenset({11, 12}), "T": frozenset({12, 14})}
        for view in View:
            self.assertEqual(system_view(direct, [("S", "T", Relation.FOLLOWS)], view), direct, view)


class MatchTest(unittest.TestCase):

    def setUp(self):
        self.gold = gold_view(LAYERED, View.ALL)

    def match(self, articles):
        return match_system(LAYERED_UNIVERSE, self.gold, {"S": frozenset(articles)}).get("S")

    def test_jaccard_prefers_the_narrower_storyline(self):
        # S = {e1, e2}: B 자카드 2/2 = 1, A 자카드 2/3 — B. 겹침만 보면 A · B 가 같다
        self.assertEqual(self.match([11, 12]), "B")

    def test_floor_is_inclusive(self):
        # S = {e3, e4}: A 자카드 1/4, C 자카드 1/2 — C, 겹침 ÷ S = 1/2 = 하한
        self.assertEqual(self.match([13, 14]), "C")
        # S = {e4, e5, e6}: C 자카드 1/3, 겹침 ÷ S = 1/3 < 하한 — 대응 없음
        self.assertIsNone(self.match([14, 15, 16]))

    def test_matches_count_only_seed_storylines(self):
        # 씨앗 e1 — 모든 층 보기의 씨앗 스토리라인은 A · B. C 는 정의돼 있어도 씨앗 스토리라인이 아니다
        # B: S1 = {e1, e2} 겹침 2 — P 1 · R 1. A({e1, e2, e3}): S1 겹침 2 — P 1 · R 2/3 · F1 0.8
        summary = storyline_matches(LAYERED_UNIVERSE, self.gold, {"S1": frozenset({11, 12}), "S2": frozenset({14})},
                                    [1], Weighting.EVENT)
        self.assertEqual([(score.storyline, score.system) for score in summary.scores], [("A", "S1"), ("B", "S1")])
        mean, minimum = summary.mean(), summary.minimum()
        self.assertAlmostEqual(mean[0], 1.0, places=12)
        self.assertAlmostEqual(mean[1], 5 / 6, places=12)
        self.assertAlmostEqual(mean[2], 0.9, places=12)
        self.assertAlmostEqual(minimum[2], 0.8, places=12)

    def test_storyline_without_overlap_scores_zero(self):
        summary = storyline_matches(LAYERED_UNIVERSE, self.gold, {"S": frozenset({15})}, [4], Weighting.EVENT)
        self.assertEqual([(score.storyline, score.system, score.f1) for score in summary.scores], [("C", None, 0.0)])


class FarPairTest(unittest.TestCase):

    def test_far_pairs_are_between_events(self):
        # 모두 G. f1 = b1(0일) · b5(6일), f2 = b2(5일), f3 = b3(4일 23시간), f4 = b4(10일). 씨앗 f1. 시스템 S = {b1, b5, b2}
        # 쌍: b1–b2(5일 — 포함, 함께) · b1–b4(10일, 따로). b1–b3 은 5일 미만, b1–b5 는 같은 사건, b5 의 쌍은 모두 5일 미만
        # 사건 가중: b1 0.5, b2 · b4 1 — 0.5 ÷ (0.5 + 0.5) = 0.5
        universe = build_universe(sheet_of({1: [(1, timedelta()), (5, timedelta(days=6))], 2: [(2, timedelta(days=5))],
                                            3: [(3, timedelta(days=4, hours=23))], 4: [(4, timedelta(days=10))]}))
        gold = {event_id: frozenset({"G"}) for event_id in (1, 2, 3, 4)}
        rate = far_pair_recall(universe, gold, {"S": frozenset({1, 5, 2})}, [1], Weighting.EVENT)
        self.assertEqual(rate.pairs, 2)
        self.assertAlmostEqual(rate.rate, 0.5, places=12)

    def test_no_far_pair(self):
        rate = far_pair_recall(LEDGER, LEDGER_GOLD, LEDGER_SYSTEM, [1, 3], Weighting.EVENT)
        self.assertEqual((rate.rate, rate.pairs), (None, 0))


class RelationAndBigTest(unittest.TestCase):

    MAPPING = {"S1": "B", "S2": "C", "S3": "A"}
    GOLD_RELATIONS = {("A", "B", Relation.CONTAINS), ("B", "C", Relation.FOLLOWS)}

    def test_relations(self):
        scores = relation_scores(self.GOLD_RELATIONS, [("S3", "S1", Relation.CONTAINS),   # 맞음
                                                       ("S1", "S2", Relation.CONTAINS),   # 종류가 틀림
                                                       ("S1", "S9", Relation.FOLLOWS)],   # S9 대응 없음 — 뺀다
                                 self.MAPPING)
        self.assertEqual((scores.judged, scores.correct, scores.unmapped, scores.precision), (2, 1, 1, 0.5))
        self.assertEqual((scores.gold, scores.found, scores.recall), (2, 1, 0.5))

    def test_relation_between_two_parts_of_one_gold_storyline_is_left_out(self):
        # S1 · S4 가 모두 정답 B 에 대응된다 — 그 사이의 포함은 맞는지 가를 정답이 없어 판정에서 뺀다
        scores = relation_scores(self.GOLD_RELATIONS, [("S1", "S4", Relation.CONTAINS),   # 같은 정답 — 뺀다
                                                       ("S3", "S4", Relation.CONTAINS)],  # A ⊃ B — 맞음
                                 {**self.MAPPING, "S4": "B"})
        self.assertEqual((scores.judged, scores.correct, scores.precision), (1, 1, 1.0))
        self.assertEqual((scores.gold, scores.found, scores.recall), (2, 1, 0.5))

    def test_relations_left_out_are_counted(self):
        scores = relation_scores(self.GOLD_RELATIONS, [("S1", "S4", Relation.CONTAINS),
                                                       ("S4", "S1", Relation.FOLLOWS),
                                                       ("S1", "S9", Relation.FOLLOWS)],
                                 {**self.MAPPING, "S4": "B"})
        self.assertEqual((scores.same_gold, scores.unmapped, scores.judged, scores.precision), (2, 1, 0, None))

    def test_result_without_relations(self):
        scores = relation_scores(self.GOLD_RELATIONS, [], self.MAPPING)
        self.assertEqual((scores.judged, scores.precision, scores.recall), (0, None, 0.0))

    def test_big_precision(self):
        days = [date(2026, 11, 4), date(2026, 11, 5)]
        score = big_precision(days, {("A", date(2026, 11, 4))},
                              [("S3", date(2026, 11, 4)),    # A — 맞음
                               ("S1", date(2026, 11, 4)),    # B — 틀림
                               ("S9", date(2026, 11, 5)),    # 대응 없음 — 틀림
                               ("S3", date(2026, 11, 6))],   # 표본 밖 — 뺀다
                              self.MAPPING)
        self.assertEqual((score.judged, score.correct, score.outside), (3, 1, 1))
        self.assertAlmostEqual(score.precision, 1 / 3, places=12)
        # 95% Wilson 구간 1/3 — 산식을 따로 셈한 값
        self.assertAlmostEqual(score.low, 0.061492, places=6)
        self.assertAlmostEqual(score.high, 0.79234, places=6)

    def test_big_without_gold_marks_is_not_measured(self):
        self.assertIsNone(big_precision([date(2026, 11, 4)], set(), [("S3", date(2026, 11, 4))], self.MAPPING))


class SystemOnSheetTest(unittest.TestCase):

    def test_articles_outside_the_sheet_are_dropped_and_counted(self):
        system, outside = system_on_sheet(LEDGER, {"S": frozenset({11, 99}), "T": frozenset({98, 99})})
        self.assertEqual((system, outside), ({"S": frozenset({11})}, 2))

    def test_same_dump_result_equals_event_level_count(self):
        # 같은 덤프 결과(사건 e1 · e3 을 기사로 펼친 S)는 원장의 사건 단위 셈과 같은 값 — 원장 대조 줄
        system, _ = system_on_sheet(LEDGER, {"S": frozenset({11, 12, 14})})
        estimate = extended_bcubed(LEDGER, LEDGER_GOLD, system, [1, 3], Weighting.EVENT)
        self.assertAlmostEqual(estimate.precision, (0.5 + 0.5) / 2, places=12)
        self.assertAlmostEqual(estimate.recall, (0.5 + 1.0) / 2, places=12)


if __name__ == "__main__":
    unittest.main()
