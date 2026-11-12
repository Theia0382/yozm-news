"""지표 — 오라클은 손으로 셈한 리터럴이다."""

import unittest
from datetime import date

from metrics import b_cubed, wilson

GOLD = {1: "a", 2: "a", 3: "a", 4: "b"}
D1 = date(2026, 11, 4)
D2 = date(2026, 11, 5)


class BCubedTest(unittest.TestCase):

    def test_split_and_merge(self):
        # 시스템 {1,2} · {3,4}: 정밀도 (1 + 1 + 1/2 + 1/2)/4 = 3/4, 재현율 (2/3 + 2/3 + 1/3 + 1)/4 = 2/3, F1 = 12/17
        result = b_cubed(GOLD, {1: 10, 2: 10, 3: 11, 4: 11})
        self.assertAlmostEqual(result.precision, 0.75, places=12)
        self.assertAlmostEqual(result.recall, 2 / 3, places=12)
        self.assertAlmostEqual(result.f1, 12 / 17, places=12)
        self.assertEqual((result.articles, result.gold_events, result.system_events), (4, 2, 2))
        self.assertEqual((result.unassigned, result.gold_singletons, result.system_singletons), (0, 1, 0))

    def test_identical_partition_is_perfect(self):
        result = b_cubed(GOLD, {1: 10, 2: 10, 3: 10, 4: 11})
        self.assertEqual((result.precision, result.recall, result.f1), (1.0, 1.0, 1.0))

    def test_unassigned_article_is_its_own_cluster(self):
        result = b_cubed(GOLD, {1: 10, 2: 10, 3: 10, 4: None})
        self.assertEqual((result.precision, result.recall, result.f1), (1.0, 1.0, 1.0))
        self.assertEqual((result.unassigned, result.system_singletons, result.system_events), (1, 1, 2))

    def test_unassigned_articles_are_not_grouped_together(self):
        # 3 · 4 가 함께 배정 없음 — 한 묶음으로 세면 3 · 4 의 정밀도가 1/2 이라 P = 3/4 가 된다
        result = b_cubed(GOLD, {1: 10, 2: 10, 3: None, 4: None})
        self.assertEqual(result.precision, 1.0)
        self.assertAlmostEqual(result.recall, 2 / 3, places=12)
        self.assertEqual((result.unassigned, result.system_singletons, result.system_events), (2, 2, 3))

    def test_mismatched_or_empty_input(self):
        with self.assertRaises(ValueError):
            b_cubed(GOLD, {1: 10, 2: 10, 3: 10})
        with self.assertRaises(ValueError):
            b_cubed({}, {})


class WilsonTest(unittest.TestCase):

    def test_bounds(self):
        low, high = wilson(0, 5)
        self.assertEqual(low, 0.0)
        self.assertAlmostEqual(high, 0.434482, places=6)
        low, high = wilson(5, 5)
        self.assertAlmostEqual(low, 0.565518, places=6)
        self.assertEqual(high, 1.0)

    def test_invalid(self):
        for successes, total in ((0, 0), (3, 2), (-1, 2)):
            with self.subTest(successes=successes, total=total), self.assertRaises(ValueError):
                wilson(successes, total)


if __name__ == "__main__":
    unittest.main()
