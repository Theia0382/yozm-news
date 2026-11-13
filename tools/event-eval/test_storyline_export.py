"""스토리라인 결과 파일 · 물은 쌍 파일 내보내기 끝까지 — 사건 평가 테스트의 가짜 운영자 API 에 스토리라인 경로를 더해 돌린다. 기대값은
고정물에서 손으로 적은 리터럴이다."""

import io
import json
import os
import tempfile
import unittest
import urllib.parse
from datetime import date
from pathlib import Path
from unittest import mock

from storyline_cli import main
from storyline_labels import new_storyline_sample, read_storyline_sample, write_storyline_sample
from storyline_result import RunKind, read_result, run_kind
from test_cli import CLOCK, TOKEN, ZONE, FakeAdmin
from test_storyline_cli import DUMP_SHA256, LINEAGE_SHA256


def judgement(judgement_id, first, second, model, reading="READ"):
    read = reading == "READ"
    return {"id": judgement_id, "firstId": first, "secondId": second, "modelVersion": model, "reading": reading,
            "none": 0.1 if read else None, "secondPartOfFirst": 0.7 if read else None,
            "firstPartOfSecond": 0.05 if read else None, "firstLedToSecond": 0.1 if read else None,
            "secondLedToFirst": 0.05 if read else None, "firstOneMatter": 0.8 if read else None,
            "secondOneMatter": 0.6 if read else None}


def asked(other, outcome, smaller, judgement_id, found):
    return {"otherId": other, "smallerEvents": smaller, "outcome": outcome, "askedAt": "2026-11-06T00:00:00Z",
            "judgementId": judgement_id, "judgement": found}


CONTAINS = {"kind": "CONTAINS", "sourceId": 20, "targetId": 21, "judgement": judgement(501, 20, 21, "jev-r1")}

# 스토리라인 20 은 사건 10(직접) · 11(하위 21 에서), 21 은 사건 11, 22 는 기사 없는 사건 14 만 — 내지 않는다. 목록의 23 은 그새 20 에
# 합쳐졌다(상세가 20 을 낸다). 21 → 40 은 내보내지 않은 스토리라인과의 이어짐이라 뺀다. 물은 쌍 (21, 31) 은 원본의 앞이 31 · 뒤가 21
DETAILS = {
    20: {"id": 20, "events": [{"id": 10, "direct": True}, {"id": 11, "direct": False}], "relations": [CONTAINS],
         "asked": [asked(21, "LINKED", 3, 501, judgement(501, 20, 21, "jev-r1")),
                   asked(30, "NOTHING_TO_ASK", 4, None, None)]},
    21: {"id": 21, "events": [{"id": 11, "direct": True}],
         "relations": [CONTAINS, {"kind": "FOLLOWS", "sourceId": 21, "targetId": 40,
                                  "judgement": judgement(504, 21, 40, "jev-r9")}],
         "asked": [asked(20, "LINKED", 3, 501, judgement(501, 20, 21, "jev-r1")),
                   asked(22, "LOW_PROBABILITY", 3, 502, None),
                   asked(31, "CYCLE", 5, 503, judgement(503, 31, 21, "jev-r3", "OTHER_EVALUATOR"))]},
    22: {"id": 22, "events": [{"id": 14, "direct": True}], "relations": [], "asked": []},
}
DETAILS[23] = DETAILS[20]

LINKING = {"windowSeconds": 259200, "threshold": 0.62}
RELATION = {"probability": 0.6, "oneMatter": 0.2, "minEvents": 3, "candidateScore": 0.88, "windowSeconds": 1209600}

RESULT = {
    "format": 1,
    "meta": {
        "lineage": LINEAGE_SHA256, "dump": DUMP_SHA256, "method": "instance", "createdAt": "2026-11-07T03:00:00Z",
        "eventCriteria": {"windowSeconds": 259200, "upper": 0.8, "lower": 0.55, "store": 0.4, "probability": 0.5},
        "embeddingModel": "onnx e5-small",
        # 사건 판정 기록에서 내보낸 기사(101 · 104)의 판 + 내보낸 관계 · 물은 쌍 원본의 판 — 빼낸 21 → 40 의 jev-r9 는 들지 않는다
        "judgementModels": ["jev-a", "jev-b", "jev-r1", "jev-r3"],
        "linking": {"linking": LINKING, "relation": RELATION},
    },
    "storylines": [{"id": "20", "articles": [101, 102]}, {"id": "21", "articles": [103, 104, 199]}],
    "relations": [{"source": "20", "target": "21", "kind": "CONTAINS"}],
    "big": [{"storyline": "20", "day": "2026-11-04"}],
}

PAIRS = ("low,high,smaller_events,outcome,asked_at,judgement_id,reading,first,second,none,second_part_of_first,"
         "first_part_of_second,first_led_to_second,second_led_to_first,first_one_matter,second_one_matter\n"
         "20,21,3,LINKED,2026-11-06T00:00:00Z,501,READ,20,21,0.1,0.7,0.05,0.1,0.05,0.8,0.6\n"
         "20,30,4,NOTHING_TO_ASK,2026-11-06T00:00:00Z,,,,,,,,,,,\n"
         "21,22,3,LOW_PROBABILITY,2026-11-06T00:00:00Z,502,,,,,,,,,,\n"
         "21,31,5,CYCLE,2026-11-06T00:00:00Z,503,OTHER_EVALUATOR,31,21,,,,,,,\n")


class StorylineAdmin(FakeAdmin):
    """FakeAdmin 에 스토리라인 목록 · 상세 · 큰 스토리라인을 더한다."""

    def __init__(self):
        super().__init__()
        self.events[14] = ("OPEN", [])
        self.listing = [[20, 21], [22, 23]]

    def _serve(self, request, body):
        url = urllib.parse.urlsplit(request.path)
        if not url.path.startswith(("/api/admin/storylines", "/api/admin/big-storylines")):
            return super()._serve(request, body)
        query = dict(urllib.parse.parse_qsl(url.query))
        self.requests.append((request.command, url.path, query, request.headers.get("Authorization")))
        if request.headers.get("Authorization") != f"Bearer {TOKEN}":
            return self._send(request, 401, {"title": "Unauthorized"})
        if url.path == "/api/admin/storylines":
            index = 1 if "afterId" in query else 0
            following = {"lastArticleAt": "2026-11-05T00:00:00Z", "id": 21} if index == 0 else None
            return self._send(request, 200, {"storylines": [{"id": one} for one in self.listing[index]],
                                             "next": following, "linkingCriteria": LINKING,
                                             "relationCriteria": RELATION})
        if url.path == "/api/admin/big-storylines":
            big = {"2026-11-04": [20, 22], "2026-11-05": []}[query["from"]]
            return self._send(request, 200, {"storylines": [{"id": one} for one in big]})
        return self._send(request, 200, DETAILS[int(url.path.rsplit("/", 1)[1])])


class StorylineExportTest(unittest.TestCase):

    def setUp(self):
        proxies = {key: "" for key in ("http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY", "all_proxy",
                                       "ALL_PROXY")}
        patcher = mock.patch.dict(os.environ, {**proxies, "no_proxy": "*", "NO_PROXY": "*"})
        patcher.start()
        self.addCleanup(patcher.stop)
        self.fake = StorylineAdmin()
        self.addCleanup(self.fake.close)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        self.data = root / "sample"
        self.data.mkdir()
        (root / "fixed.dump").write_bytes(b"fixed-dump-bytes\n")
        (root / "lineage.dump").write_bytes(b"lineage-dump-bytes\n")
        write_storyline_sample(self.data, new_storyline_sample(date(2026, 11, 4), date(2026, 11, 5), ZONE,
                                                               root / "fixed.dump", root / "lineage.dump"))
        self.result = root / "instance.json"
        self.pairs = root / "instance-pairs.csv"

    def export(self, token=TOKEN):
        out, err = io.StringIO(), io.StringIO()
        code = main(["export", "--data", str(self.data), "--result", str(self.result), "--pairs", str(self.pairs),
                     "--method", "instance", "--embedding-model", "onnx e5-small", "--base-url", self.fake.url],
                    environ={"YOZMNEWS_ADMIN_TOKEN": token} if token else {}, stdout=out, stderr=err,
                    clock=lambda: CLOCK)
        return code, out.getvalue(), err.getvalue()

    def test_writes_the_result_and_the_asked_pairs_of_the_instance(self):
        code, out, err = self.export()

        self.assertEqual((code, err), (0, ""), err)
        self.assertEqual(json.loads(self.result.read_text(encoding="utf-8")), RESULT)
        self.assertEqual(self.pairs.read_text(encoding="utf-8"), PAIRS)
        self.assertEqual(out, f"결과 {self.result}: 스토리라인 2 · 관계 1 · 큰 표시 1(직접 기사가 없어 내지 않은 스토리라인 1)\n"
                              f"물은 쌍 {self.pairs}: 4쌍(확률 없는 쌍 3)\n")
        # 측정이 받는 결과 파일이다 — 같은 덤프 위의 결과
        self.assertEqual(run_kind(read_result(self.result), read_storyline_sample(self.data)), RunKind.SAME_DUMP)
        paths = [path for _, path, _, _ in self.fake.requests]
        # 직접 사건의 기사만 읽고(사건 11 은 21 의 직접 사건), 날마다 큰 스토리라인을 한 번씩
        self.assertEqual([path for path in paths if path.startswith("/api/admin/events/")],
                         ["/api/admin/events/10", "/api/admin/events/11", "/api/admin/events/14"])
        self.assertEqual([query["from"] for _, path, query, _ in self.fake.requests
                          if path == "/api/admin/big-storylines"], ["2026-11-04", "2026-11-05"])

    def test_does_not_overwrite_and_needs_the_token(self):
        self.pairs.write_text("old\n", encoding="utf-8")
        code, _, err = self.export()
        self.assertEqual(code, 2)
        self.assertIn("instance-pairs.csv 가 이미 있다 — 덮지 않는다", err)
        self.assertFalse(self.result.exists())
        self.assertEqual(self.fake.requests, [])
        self.pairs.unlink()
        code, _, err = self.export(token=None)
        self.assertEqual(code, 2)
        self.assertIn("YOZMNEWS_ADMIN_TOKEN", err)
        self.assertFalse(self.result.exists())

    def test_a_storyline_listed_twice_stops_the_export(self):
        self.fake.listing = [[20, 21], [21]]
        code, _, err = self.export()
        self.assertEqual(code, 3)
        self.assertIn("스토리라인 21 가 목록에 두 번 나왔다", err)
        self.assertFalse(self.result.exists())
        self.assertFalse(self.pairs.exists())


if __name__ == "__main__":
    unittest.main()
