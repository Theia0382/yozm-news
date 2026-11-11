"""스토리라인 평가의 표본 · 시트 · 씨앗 · 라벨 파일 형식과 검증. 기대값은 고정물에서 손으로 셈한 리터럴이다."""

import json
import tempfile
import unittest
from datetime import UTC, date, datetime
from pathlib import Path

from labels import LabelError, Sample
from storyline_labels import (Labels, Relation, Seeds, Sheet, SheetArticle, Snapshot, StorylineSample, draw_seeds,
                              new_storyline_sample, read_labels, read_seeds, read_sheet, read_storyline_sample,
                              write_label_templates, write_seeds)

ZONE = "Asia/Seoul"
SAMPLE = StorylineSample(Sample(1, date(2026, 11, 4), date(2026, 11, 5), ZONE), Snapshot("fixed.dump", "f" * 64),
                         Snapshot("lineage.dump", "e" * 64))
SAMPLE_JSON = {"kind": "storyline", "labelRule": 1, "start": "2026-11-04", "end": "2026-11-05", "zone": ZONE,
               "dump": {"name": "fixed.dump", "sha256": "f" * 64},
               "lineage": {"name": "lineage.dump", "sha256": "e" * 64}}

# 사건 1 ~ 6 — 사건 i 의 기사는 100 + i(사건 1 은 101 · 111 두 건)
SHEET_ARTICLES = ("event_id,article_id,published_at,sources,title\n"
                  "1,101,2026-11-03T15:00:00Z,동아,하나\n"
                  "1,111,2026-11-04T01:00:00Z,SBS;경향,하나 둘\n"
                  "2,102,2026-11-04T02:00:00Z,동아,둘\n"
                  "3,103,2026-11-04T03:00:00Z,동아,셋\n"
                  "4,104,2026-11-05T01:00:00Z,동아,넷\n"
                  "5,105,2026-11-05T02:00:00Z,동아,다섯\n"
                  "6,106,2026-11-05T14:59:59Z,동아,여섯\n")
SHEET = ("event_id,first_published_at,last_published_at,articles,sources,title\n"
         "1,2026-11-03T15:00:00Z,2026-11-04T01:00:00Z,2,SBS;경향;동아,하나\n"
         "2,2026-11-04T02:00:00Z,2026-11-04T02:00:00Z,1,동아,둘\n"
         "3,2026-11-04T03:00:00Z,2026-11-04T03:00:00Z,1,동아,셋\n"
         "4,2026-11-05T01:00:00Z,2026-11-05T01:00:00Z,1,동아,넷\n"
         "5,2026-11-05T02:00:00Z,2026-11-05T02:00:00Z,1,동아,다섯\n"
         "6,2026-11-05T14:59:59Z,2026-11-05T14:59:59Z,1,동아,여섯\n")

# a ⊃ b, b → c. 사건 1 · 2 는 b(위로 닫으면 a 도), 3 은 a, 4 · 5 는 c, 6 은 없음. 씨앗 1 · 4 · 6
LABELS = "event_id,storylines,note\n1,b,\n4,c,\n6,-,? 여러 일\n2,b,\n3,a,\n5,c,! 대조로 더함\n"
STORYLINES = "storyline,name\na,큰 흐름\nb,갈래\nc,다른 흐름\n"
RELATIONS = "source,target,kind\na,b,CONTAINS\nb,c,FOLLOWS\n"
BIG = "storyline,day\na,2026-11-04\n"
SEEDS = Seeds(7, (1, 4, 6))


class StorylineLabelsTest(unittest.TestCase):

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.directory = Path(directory.name)

    def write(self, name, text):
        (self.directory / name).write_text(text, encoding="utf-8")

    def write_sheet_files(self):
        self.write("sheet-articles.csv", SHEET_ARTICLES)
        self.write("sheet.csv", SHEET)
        return read_sheet(self.directory, SAMPLE)

    def write_labels(self, labels=LABELS, storylines=STORYLINES, relations=RELATIONS, big=BIG):
        self.write("labels.csv", labels)
        self.write("storylines.csv", storylines)
        self.write("relations.csv", relations)
        self.write("big.csv", big)

    def labels_error(self, **files):
        sheet = self.write_sheet_files()
        self.write_labels(**files)
        with self.assertRaises(LabelError) as caught:
            read_labels(self.directory, sheet, SEEDS, SAMPLE)
        return str(caught.exception)

    # 표본

    def test_sample_round_trip_and_kind(self):
        self.write("sample.json", json.dumps(SAMPLE_JSON))
        self.assertEqual(read_storyline_sample(self.directory), SAMPLE)
        # 사건 평가의 표본(kind 없음)은 판이 같아도 멈춘다 — 판 번호가 우연히 달라 멈추는 것에 기대지 않는다
        self.write("sample.json",
                   json.dumps({"labelRule": 1, "start": "2026-11-04", "end": "2026-11-05", "zone": ZONE}))
        with self.assertRaises(LabelError) as caught:
            read_storyline_sample(self.directory)
        self.assertIn("스토리라인 평가의 표본이 아니다(kind None)", str(caught.exception))
        # 사건 평가의 지금 판(2) 표본도 판 어긋남이 아니라 kind 로 알린다
        self.write("sample.json",
                   json.dumps({"labelRule": 2, "start": "2026-11-04", "end": "2026-11-05", "zone": ZONE}))
        with self.assertRaises(LabelError) as caught:
            read_storyline_sample(self.directory)
        self.assertIn("스토리라인 평가의 표본이 아니다(kind None)", str(caught.exception))
        self.assertNotIn("판으로 만든 표본이다", str(caught.exception))
        # 대조 — 파일이 없으면 kind 가 아니라 labels.read_sample 의 안내로 멈춘다
        (self.directory / "sample.json").unlink()
        with self.assertRaises(LabelError) as caught:
            read_storyline_sample(self.directory)
        self.assertIn("sample.json 가 없다 — sheet 로 먼저 만든다", str(caught.exception))

    def test_sample_of_another_label_rule_stops(self):
        self.write("sample.json", json.dumps({**SAMPLE_JSON, "labelRule": 2}))
        with self.assertRaises(LabelError) as caught:
            read_storyline_sample(self.directory)
        self.assertIn("라벨 기준 2판으로 만든 표본이다 — 이 도구는 1판이다", str(caught.exception))

    def test_new_sample_digests_dump_files(self):
        (self.directory / "fixed.dump").write_bytes(b"fixed-dump-bytes\n")
        (self.directory / "lineage.dump").write_bytes(b"lineage-dump-bytes\n")
        sample = new_storyline_sample(date(2026, 11, 4), date(2026, 11, 5), ZONE, self.directory / "fixed.dump",
                                      self.directory / "lineage.dump")
        # sha256 은 sha256sum 으로 셈한 값
        self.assertEqual(sample, StorylineSample(
            Sample(1, date(2026, 11, 4), date(2026, 11, 5), ZONE),
            Snapshot("fixed.dump", "d23a270e3d8c876b29f7d4a11b8ddcc0c883a6abf35a161963bef36be50e7871"),
            Snapshot("lineage.dump", "b4dc6e0605759e13b6890bdcf71f094ce548e1e33902fdb998e4bc0a2b7307a0")))
        with self.assertRaises(LabelError) as caught:
            new_storyline_sample(date(2026, 11, 4), date(2026, 11, 5), ZONE, self.directory / "none.dump",
                                 self.directory / "lineage.dump")
        self.assertIn("none.dump 가 없다", str(caught.exception))

    # 시트

    def test_read_sheet(self):
        sheet = self.write_sheet_files()
        events = sheet.events()
        self.assertEqual(list(events), [1, 2, 3, 4, 5, 6])
        self.assertEqual(events[1], (
            SheetArticle(1, 101, datetime(2026, 11, 3, 15, tzinfo=UTC), ("동아",), "하나"),
            SheetArticle(1, 111, datetime(2026, 11, 4, 1, tzinfo=UTC), ("SBS", "경향"), "하나 둘")))

    def test_sheet_files_must_agree(self):
        self.write("sheet-articles.csv", SHEET_ARTICLES)
        self.write("sheet.csv", SHEET.replace("1,2026-11-03T15:00:00Z,2026-11-04T01:00:00Z,2,",
                                              "1,2026-11-03T15:00:00Z,2026-11-04T01:00:00Z,3,"))
        with self.assertRaises(LabelError) as caught:
            read_sheet(self.directory, SAMPLE)
        self.assertIn("사건 · 기사 수가 다르다 — 사건 1", str(caught.exception))

    def test_sheet_article_outside_the_period_or_twice_stops(self):
        # 11-03 23:59:59 KST 는 표본(11-04 ~ 05 KST) 밖
        self.write("sheet-articles.csv",
                   SHEET_ARTICLES.replace("1,101,2026-11-03T15:00:00Z", "1,101,2026-11-03T14:59:59Z")
                   + "2,102,2026-11-04T02:00:00Z,동아,둘\n")
        self.write("sheet.csv", SHEET)
        with self.assertRaises(LabelError) as caught:
            read_sheet(self.directory, SAMPLE)
        self.assertIn("sheet-articles.csv:2: 기사 101 의 발행일 2026-11-03 이 표본 밖이다", str(caught.exception))
        self.assertIn("sheet-articles.csv:9: 기사 102 가 4줄에도 있다", str(caught.exception))

    # 씨앗

    def sheet_of(self, count):
        return Sheet(tuple(SheetArticle(event_id, 100 + event_id, datetime(2026, 11, 4, tzinfo=UTC), ("동아",), "t")
                           for event_id in range(1, count + 1)))

    def test_draw_seeds_shuffles_sorted_events(self):
        # random.Random(7).shuffle([1, …, 8]) 를 인터프리터에서 직접 돌린 순서: [7, 8, 3, 5, 1, 4, 2, 6]
        self.assertEqual(draw_seeds(self.sheet_of(8), 3, 7), Seeds(7, (7, 8, 3)))
        # 늘려도 앞부분이 같다
        self.assertEqual(draw_seeds(self.sheet_of(8), 5, 7), Seeds(7, (7, 8, 3, 5, 1)))
        self.assertNotEqual(draw_seeds(self.sheet_of(8), 3, 8).events, (7, 8, 3))  # 대조 — 씨앗 값이 다르면 다르다

    def test_draw_seeds_count_bounds(self):
        self.assertEqual(len(draw_seeds(self.sheet_of(8), 8, 7).events), 8)
        for count in (0, 9):
            with self.assertRaises(LabelError):
                draw_seeds(self.sheet_of(8), count, 7)

    def test_write_seeds_only_grows_with_the_same_seed(self):
        write_seeds(self.directory, Seeds(7, (7, 8)), None)
        sheet = self.sheet_of(8)
        existing = read_seeds(self.directory, sheet)
        self.assertEqual(existing, Seeds(7, (7, 8)))
        for seeds, message in ((Seeds(8, (7, 8, 3)), "다른 씨앗 값 8"), (Seeds(7, (7, 8)), "늘리기만"),
                               (Seeds(7, (7,)), "늘리기만"), (Seeds(7, (8, 7, 3)), "늘리기만")):
            with self.assertRaises(LabelError) as caught:
                write_seeds(self.directory, seeds, existing)
            self.assertIn(message, str(caught.exception))
        write_seeds(self.directory, Seeds(7, (7, 8, 3)), existing)
        self.assertEqual(json.loads((self.directory / "seeds.json").read_text(encoding="utf-8")),
                         {"seed": 7, "events": [7, 8, 3]})

    def test_seeds_must_be_in_the_sheet(self):
        self.write("seeds.json", '{"seed": 7, "events": [7, 9]}')
        with self.assertRaises(LabelError) as caught:
            read_seeds(self.directory, self.sheet_of(8))
        self.assertIn("씨앗 사건 9 가 시트에 없다", str(caught.exception))
        self.write("seeds.json", '{"seed": 7, "events": []}')
        with self.assertRaises(LabelError) as caught:
            read_seeds(self.directory, self.sheet_of(8))
        self.assertIn("seeds.json: 씨앗 사건이 없다", str(caught.exception))

    def test_label_templates_add_only_new_seed_rows(self):
        self.assertEqual(write_label_templates(self.directory, Seeds(7, (7, 8))), 2)
        files = {name: (self.directory / name).read_text(encoding="utf-8")
                 for name in ("labels.csv", "storylines.csv", "relations.csv", "big.csv")}
        self.assertEqual(files, {"labels.csv": "event_id,storylines,note\n7,,\n8,,\n",
                                 "storylines.csv": "storyline,name\n", "relations.csv": "source,target,kind\n",
                                 "big.csv": "storyline,day\n"})
        # 라벨러가 적은 줄 · 더한 줄과 다른 파일은 그대로 두고 새 씨앗 줄만 뒤에 더한다
        self.write("labels.csv", "event_id,storylines,note\n7,x,메모\n8,,\n2,y,\n")
        self.write("storylines.csv", "storyline,name\nx,이름\n")
        self.assertEqual(write_label_templates(self.directory, Seeds(7, (7, 8, 3))), 1)
        self.assertEqual((self.directory / "labels.csv").read_text(encoding="utf-8"),
                         "event_id,storylines,note\n7,x,메모\n8,,\n2,y,\n3,,\n")
        self.assertEqual((self.directory / "storylines.csv").read_text(encoding="utf-8"), "storyline,name\nx,이름\n")

    # 라벨

    def test_read_labels(self):
        sheet = self.write_sheet_files()
        self.write_labels()
        labels = read_labels(self.directory, sheet, SEEDS, SAMPLE)
        self.assertEqual(labels, Labels(
            written={1: frozenset({"b"}), 4: frozenset({"c"}), 6: frozenset(), 2: frozenset({"b"}),
                     3: frozenset({"a"}), 5: frozenset({"c"})},
            notes={1: "", 4: "", 6: "? 여러 일", 2: "", 3: "", 5: "! 대조로 더함"},
            names={"a": "큰 흐름", "b": "갈래", "c": "다른 흐름"},
            relations=frozenset({("a", "b", Relation.CONTAINS), ("b", "c", Relation.FOLLOWS)}),
            big=frozenset({("a", date(2026, 11, 4))})))
        # 위로 닫힘 — b 의 사건은 a 에도 들고, FOLLOWS 는 닫지 않는다
        self.assertEqual(labels.closed(1), frozenset({"a", "b"}))
        self.assertEqual(labels.closed(4), frozenset({"c"}))
        self.assertEqual(labels.closed(6), frozenset())

    def test_closed_climbs_every_level(self):
        labels = Labels({1: frozenset({"c"})}, {}, {"a": "", "b": "", "c": ""},
                        frozenset({("a", "b", Relation.CONTAINS), ("b", "c", Relation.CONTAINS)}), frozenset())
        self.assertEqual(labels.closed(1), frozenset({"a", "b", "c"}))
        self.assertEqual(labels.closed(2), frozenset())

    def test_label_problems_stop(self):
        cases = {
            "empty": (dict(labels=LABELS.replace("2,b,", "2,,")), "labels.csv:5: 사건 2 의 storylines 가 비었다"),
            "unknown": (dict(labels=LABELS.replace("2,b,", "2,b;z,")), "사건 2 의 표지 z 가 storylines.csv 에 없다"),
            "none mixed": (dict(labels=LABELS.replace("6,-,", "6,-;c,")), "사건 6 에 '-' 와 표지를 함께 적었다"),
            "doubled": (dict(labels=LABELS.replace("2,b,", "2,b;b,")), "사건 2 의 표지가 비었거나 겹친다"),
            "not in sheet": (dict(labels=LABELS + "9,c,\n"), "labels.csv:8: 사건 9 가 시트에 없다"),
            "twice": (dict(labels=LABELS + "2,b,\n"), "labels.csv:8: 사건 2 가 5줄에도 있다"),
            "seed row": (dict(labels=LABELS.replace("4,c,\n", "")), "씨앗 사건 4 의 줄이 없다"),
            "self": (dict(relations=RELATIONS + "c,c,FOLLOWS\n"), "스토리라인 c 가 자기 자신과 이어졌다"),
            "pair": (dict(relations=RELATIONS + "c,b,CONTAINS\n"), "relations.csv:4: c · b 의 관계가 3줄에도 있다"),
            "kind": (dict(relations=RELATIONS.replace("FOLLOWS", "NEXT")), "kind 는 CONTAINS 이나 FOLLOWS 다"),
            "relation end": (dict(relations=RELATIONS + "a,z,FOLLOWS\n"), "표지 z 가 storylines.csv 에 없다"),
            "big day": (dict(big=BIG + "c,2026-11-06\n"), "big.csv:3: 날짜 2026-11-06 이 표본 밖이다"),
            "big storyline": (dict(big=BIG + "z,2026-11-05\n"), "big.csv:3: 표지 'z' 가 storylines.csv 에 없다"),
            "storyline mark": (dict(storylines=STORYLINES + "-,없음\n"), "storylines.csv:5: 표지 '-' 는 쓸 수 없다"),
            "storyline separator": (dict(storylines=STORYLINES + "d;e,묶음\n"),
                                    "storylines.csv:5: 표지 'd;e' 는 쓸 수 없다"),
            "storyline twice": (dict(storylines=STORYLINES + "a,또\n"), "storylines.csv:5: 표지 a 가 둘이다"),
            "big twice": (dict(big=BIG + "a,2026-11-04\n"), "big.csv:3: (a, 2026-11-04) 줄이 둘이다"),
            "one member": (dict(storylines=STORYLINES + "d,외톨이\n", labels=LABELS.replace("5,c,", "5,c;d,")),
                           "스토리라인 d 의 구성 사건이 1개다 — 둘 이상이어야 한다"),
        }
        for name, (files, message) in cases.items():
            with self.subTest(name):
                self.assertIn(message, self.labels_error(**files))

    def test_cycle_names_the_loop(self):
        message = self.labels_error(relations="source,target,kind\na,b,CONTAINS\nb,c,CONTAINS\nc,a,CONTAINS\n")
        self.assertIn("a ⊃ b ⊃ c ⊃ a", message)

    def test_containment_counts_toward_the_two_member_floor(self):
        # a 에 직접 적힌 사건은 3 하나뿐이지만 b 의 사건 1 · 2 가 위로 닫혀 a 는 셋이다 — 대조: a ⊃ b 를 빼면 a 는 하나
        sheet = self.write_sheet_files()
        self.write_labels()
        self.assertEqual(len([event for event in (1, 2, 3, 4, 5, 6)
                              if "a" in read_labels(self.directory, sheet, SEEDS, SAMPLE).closed(event)]), 3)
        self.assertIn("스토리라인 a 의 구성 사건이 1개다",
                      self.labels_error(relations="source,target,kind\nb,c,FOLLOWS\n"))


if __name__ == "__main__":
    unittest.main()
