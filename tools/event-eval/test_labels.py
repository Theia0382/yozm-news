"""표본 · 라벨 파일 형식과 검증."""

import tempfile
import unittest
from datetime import UTC, date, datetime
from pathlib import Path

from labels import (ArticleLabel, BigMark, LabelError, Sample, build_event_days, read_big_marks, read_labels,
                    read_marks, write_marks)

SAMPLE = Sample(1, date(2026, 11, 4), date(2026, 11, 5), "Asia/Seoul")
HEADER = "article_id,published_at,sources,title,event,note\n"


class LabelsTest(unittest.TestCase):

    def setUp(self):
        self._directory = tempfile.TemporaryDirectory()
        self.directory = Path(self._directory.name)

    def tearDown(self):
        self._directory.cleanup()

    def write(self, name, text):
        path = self.directory / name
        path.write_text(text, encoding="utf-8")
        return path

    def test_valid_sheet(self):
        path = self.write("articles.csv", HEADER
                          + "1,2026-11-03T15:00:00Z,동아;SBS,제목 하나,a,\n"
                          + "2,2026-11-05T14:59:59Z,경향,제목 둘,-,? 묶음 기사\n")
        labels = read_labels(path, SAMPLE)
        self.assertEqual(labels, [
            ArticleLabel(1, datetime(2026, 11, 3, 15, tzinfo=UTC), ("동아", "SBS"), "a", False),
            ArticleLabel(2, datetime(2026, 11, 5, 14, 59, 59, tzinfo=UTC), ("경향",), None, True)])

    def test_day_boundary_in_sample_zone(self):
        # 11-03 23:59 KST = 11-03 14:59Z 는 표본(11-04 ~ 05 KST) 밖, 11-04 00:00 KST = 11-03 15:00Z 는 안
        path = self.write("articles.csv", HEADER + "1,2026-11-03T14:59:00Z,동아,t,a,\n")
        with self.assertRaises(LabelError) as caught:
            read_labels(path, SAMPLE)
        self.assertIn("articles.csv:2: 기사 1 의 발행일 2026-11-03 이 표본 밖이다", str(caught.exception))
        path = self.write("articles.csv", HEADER + "1,2026-11-03T15:00:00Z,동아,t,a,\n")
        self.assertEqual(len(read_labels(path, SAMPLE)), 1)

    def test_problems_are_reported_with_lines(self):
        path = self.write("articles.csv", HEADER
                          + "1,2026-11-04T01:00:00Z,동아,t,,\n"
                          + "2,2026-11-04T01:00:00Z,동아,t,a,\n"
                          + "2,2026-11-04T01:00:00Z,동아,t,a,\n"
                          + "x,2026-11-04T01:00:00Z,동아,t,a,\n"
                          + "3,2026-11-04T01:00:00,동아,t,a,\n")
        with self.assertRaises(LabelError) as caught:
            read_labels(path, SAMPLE)
        self.assertEqual(caught.exception.problems, (
            "articles.csv:2: 기사 1 의 event 가 비었다",
            "articles.csv:4: 기사 2 가 3줄에도 있다",
            "articles.csv:5: 기사 ID 가 정수가 아니다 — 'x'",
            "articles.csv:6: 기사 3 의 발행 시각을 읽을 수 없다 — '2026-11-04T01:00:00'"))

    def test_wrong_header(self):
        path = self.write("articles.csv", "id,published_at\n1,2026-11-04T01:00:00Z\n")
        with self.assertRaises(LabelError):
            read_labels(path, SAMPLE)

    def test_event_days_keep_marks_and_drop_vanished_rows(self):
        labels = [
            ArticleLabel(1, datetime(2026, 11, 4, 1, tzinfo=UTC), (), "a", False),
            ArticleLabel(2, datetime(2026, 11, 4, 16, tzinfo=UTC), (), "a", False),  # 11-05 KST
            ArticleLabel(3, datetime(2026, 11, 4, 2, tzinfo=UTC), (), "b", False),
            ArticleLabel(4, datetime(2026, 11, 4, 3, tzinfo=UTC), (), None, False)]
        existing = [BigMark("a", date(2026, 11, 4), True, "이름"), BigMark("z", date(2026, 11, 4), True)]
        self.assertEqual(build_event_days(labels, SAMPLE, existing), [
            BigMark("a", date(2026, 11, 4), True, "이름"),
            BigMark("a", date(2026, 11, 5), False),
            BigMark("b", date(2026, 11, 4), False)])

    def test_big_marks_must_match_labels(self):
        labels = [ArticleLabel(1, datetime(2026, 11, 4, 1, tzinfo=UTC), (), "a", False)]
        path = self.directory / "events.csv"
        write_marks(path, [BigMark("a", date(2026, 11, 4), True)])
        self.assertEqual(read_big_marks(path, labels, SAMPLE), {("a", date(2026, 11, 4))})
        write_marks(path, [BigMark("a", date(2026, 11, 4), True), BigMark("q", date(2026, 11, 5), True)])
        with self.assertRaises(LabelError) as caught:
            read_big_marks(path, labels, SAMPLE)
        self.assertEqual(caught.exception.problems, ("events.csv: (q, 2026-11-05) 는 라벨에 없다",))
        write_marks(path, [])
        with self.assertRaises(LabelError) as caught:
            read_big_marks(path, labels, SAMPLE)
        self.assertEqual(caught.exception.problems, ("events.csv: (a, 2026-11-04) 줄이 없다 — events 를 다시 돌린다",))

    def test_marks_format(self):
        path = self.write("events.csv", "event,day,big,name\na,2026-11-04,2,\na,2026-11-04,1,\na,2026-11-04,0,\n")
        with self.assertRaises(LabelError) as caught:
            read_marks(path)
        self.assertEqual(caught.exception.problems, (
            "events.csv:2: big 은 0 이나 1 이다 — '2'",
            "events.csv:4: (a, 2026-11-04) 줄이 둘이다"))


if __name__ == "__main__":
    unittest.main()
