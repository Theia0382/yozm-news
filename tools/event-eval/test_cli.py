"""명령 끝까지 — 표준 라이브러리 HTTP 서버로 만든 가짜 운영자 API 에 대해 돌린다. 기대값은 고정물에서 손으로 셈한 리터럴이다."""

import contextlib
import csv
import io
import json
import os
import tempfile
import threading
import unittest
import urllib.parse
from datetime import UTC, datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest import mock

from cli import main

TOKEN = "test-token-6b1f0e"
ZONE = "Asia/Seoul"

ARTICLES = {
    101: ("2026-11-04T01:00:00Z", ["동아"], "첫 사건 기사 하나"),
    102: ("2026-11-04T02:00:00Z", ["경향"], "첫 사건 기사 둘"),
    103: ("2026-11-04T03:00:00Z", ["SBS"], "첫 사건 기사 셋"),
    104: ("2026-11-05T01:00:00Z", ["JTBC"], "둘째 사건 기사"),
    105: ("2026-11-05T02:00:00Z", ["동아"], "오늘의 주요 뉴스"),
    106: ("2026-11-05T03:00:00Z", ["SBS"], "라벨 밖 기사"),
    107: ("2026-11-05T04:00:00Z", None, None),  # 그새 지운 기사 — 기사 정보 없음
    199: ("2026-11-03T14:59:00Z", ["SBS"], "표본 앞날 기사"),  # 11-03 23:59 KST — 표본 밖
}
TITLES = [title for _, _, title in ARTICLES.values() if title]

LABELS = ("article_id,published_at,sources,title,event,note\n"
          "101,2026-11-04T01:00:00Z,동아,첫 사건 기사 하나,a,\n"
          "102,2026-11-04T02:00:00Z,경향,첫 사건 기사 둘,a,\n"
          "103,2026-11-04T03:00:00Z,SBS,첫 사건 기사 셋,a,\n"
          "104,2026-11-05T01:00:00Z,JTBC,둘째 사건 기사,b,\n"
          "105,2026-11-05T02:00:00Z,동아,오늘의 주요 뉴스,-,\n")
LABELS_SHA256 = "b04be93d39d4bef86f0347a17bab1451cd8f730b94f41620ebf73870ed2d6ce0"
SAMPLE = '{"labelRule": 2, "start": "2026-11-04", "end": "2026-11-05", "zone": "Asia/Seoul"}'

SHEET = ("article_id,published_at,sources,title,event,note\n"
         "101,2026-11-04T01:00:00Z,동아,첫 사건 기사 하나,,\n"
         "102,2026-11-04T02:00:00Z,경향,첫 사건 기사 둘,,\n"
         "103,2026-11-04T03:00:00Z,SBS,첫 사건 기사 셋,,\n"
         "104,2026-11-05T01:00:00Z,JTBC,둘째 사건 기사,,\n"
         "105,2026-11-05T02:00:00Z,동아,오늘의 주요 뉴스,,\n"
         "106,2026-11-05T03:00:00Z,SBS,라벨 밖 기사,,\n")

CLOCK = datetime(2026, 11, 7, 3, 0, tzinfo=UTC)  # 11-07 12:00 KST


class FakeAdmin:
    """운영자 API 가운데 도구가 부르는 경로만 흉내 낸다. 받은 요청을 기록한다."""

    def __init__(self):
        self.events = {10: ("OPEN", [101, 102]), 11: ("CLOSED", [103, 104, 199]), 12: ("OPEN", [105, 106, 107])}
        self.merged = {13: 10}
        self.redirect_to = None
        self.requests = []
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), self._handler())
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.url = f"http://127.0.0.1:{self.server.server_address[1]}"

    def close(self):
        self.server.shutdown()
        self.server.server_close()

    def _handler(self):
        fake = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                fake._serve(self, None)

            def do_POST(self):
                fake._serve(self, json.loads(self.rfile.read(int(self.headers["Content-Length"]))))

        return Handler

    def _serve(self, request, body):
        url = urllib.parse.urlsplit(request.path)
        query = dict(urllib.parse.parse_qsl(url.query))
        self.requests.append((request.command, url.path, query, request.headers.get("Authorization")))
        if self.redirect_to is not None:
            return self._send(request, 302, None, {"Location": self.redirect_to + request.path})
        if request.headers.get("Authorization") != f"Bearer {TOKEN}":
            return self._send(request, 401, {"title": "Unauthorized"})
        if body is not None:
            query = body
        if query.get("from") == "2026-10-01":
            return self._send(request, 400, {"title": "Bad Request", "detail": "기간은 31일까지다"},
                              content_type="application/problem+json")
        if url.path == "/api/admin/events":
            if "afterId" not in query:
                return self._send(request, 200, {"events": [{"id": 12}, {"id": 11}],
                                                 "next": {"lastArticleAt": "2026-11-05T04:00:00Z", "id": 11}})
            return self._send(request, 200, {"events": [{"id": 10}, {"id": 13}], "next": None})
        if url.path.startswith("/api/admin/events/"):
            event_id = self.merged.get(int(url.path.rsplit("/", 1)[1]), int(url.path.rsplit("/", 1)[1]))
            status, members = self.events[event_id]
            return self._send(request, 200, {"id": event_id, "status": status, "articles": [
                {"articleId": article, "publishedAt": ARTICLES[article][0],
                 "article": None if ARTICLES[article][2] is None else {"title": ARTICLES[article][2],
                                                                       "sources": ARTICLES[article][1]}}
                for article in members]})
        if url.path == "/api/admin/event-judgements":
            criteria = {"windowSeconds": 259200, "upper": 0.80, "lower": 0.55, "store": 0.40, "probability": 0.50}
            pages = {"2026-11-04": [[(101, "jev-a")]], "2026-11-05": [[(999, "jev-other")]],
                     "2026-11-06": [[(999, "jev-other")], [(104, "jev-b")]], "2026-11-07": [[]]}[query["from"]]
            index = 1 if "afterId" in query else 0
            following = {"judgedAt": "2026-11-06T05:00:00Z", "id": 77} if index + 1 < len(pages) else None
            return self._send(request, 200, {"criteria": criteria, "next": following, "judgements": [
                {"articleId": article, "modelVersion": model} for article, model in pages[index]]})
        return self._send(request, 404, {"title": "Not Found"})

    @staticmethod
    def _send(request, status, payload, headers=None, content_type="application/json"):
        data = b"" if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request.send_response(status)
        request.send_header("Content-Type", content_type)
        request.send_header("Content-Length", str(len(data)))
        for key, value in (headers or {}).items():
            request.send_header(key, value)
        request.end_headers()
        request.wfile.write(data)


class CliTest(unittest.TestCase):

    def setUp(self):
        proxies = {key: "" for key in ("http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY", "all_proxy",
                                       "ALL_PROXY")}
        patcher = mock.patch.dict(os.environ, {**proxies, "no_proxy": "*", "NO_PROXY": "*"})
        patcher.start()
        self.addCleanup(patcher.stop)
        self.fake = FakeAdmin()
        self.addCleanup(self.fake.close)
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.data = Path(directory.name) / "sample"

    def run_tool(self, *args, token=TOKEN, base_url=None, clock=CLOCK):
        out, err = io.StringIO(), io.StringIO()
        code = main([*args, "--data", str(self.data), *(["--base-url", base_url or self.fake.url]
                                                       if args[0] in ("sheet", "measure") else [])],
                    environ={"YOZMNEWS_ADMIN_TOKEN": token} if token else {}, stdout=out, stderr=err,
                    clock=lambda: clock)
        return code, out.getvalue(), err.getvalue()

    def write_fixture(self, labels=LABELS):
        self.data.mkdir()
        (self.data / "sample.json").write_text(SAMPLE, encoding="utf-8")
        (self.data / "articles.csv").write_text(labels, encoding="utf-8")

    def test_sheet_writes_assigned_articles_of_the_period(self):
        code, out, err = self.run_tool("sheet", "--from", "2026-11-04", "--to", "2026-11-05", "--zone", ZONE)
        self.assertEqual((code, err), (0, ""), err)
        # 시트 전체를 리터럴과 견준다 — 사건 ID 칸이 없고, 표본 밖 199 · 지운 107 이 없고, 합쳐진 13 의 기사는 한 번만 있다
        self.assertEqual((self.data / "articles.csv").read_text(encoding="utf-8"), SHEET)
        self.assertEqual(json.loads((self.data / "sample.json").read_text(encoding="utf-8")),
                         {"labelRule": 2, "start": "2026-11-04", "end": "2026-11-05", "zone": ZONE})
        self.assertIn("기사 6건(기사 정보가 없어 뺀 기사 1건)", out)
        code, _, err = self.run_tool("sheet", "--from", "2026-11-04", "--to", "2026-11-05", "--zone", ZONE)
        self.assertEqual(code, 2)
        self.assertIn("이미 있다", err)

    def test_review(self):
        self.write_fixture()
        code, out, _ = self.run_tool("review")
        self.assertEqual(code, 0)
        self.assertIn("== a · 3건\n", out)
        self.assertIn("== b · 1건\n", out)
        self.assertIn("첫 사건 기사 하나", out)  # 검토 화면에는 제목이 있다 — 아래 measure 검사의 대조 leg
        self.assertIn("== 뺀 기사 1건", out)

    def test_review_sample_includes_ambiguous_excluded_articles(self):
        self.write_fixture(LABELS.replace("오늘의 주요 뉴스,-,", "오늘의 주요 뉴스,-,? 여러 일을 모은 기사")
                           .replace("첫 사건 기사 하나,a,", "첫 사건 기사 하나,a,? 갈래가 애매하다"))
        code, out, _ = self.run_tool("review", "--sample", "0", "--seed", "1")
        self.assertEqual(code, 0)
        self.assertIn("씨앗 1", out)
        # 뺀 기사에 단 ? 도 검토 대상이다
        self.assertIn("105 오늘의 주요 뉴스  (? 여러 일을 모은 기사)", out)
        # 대조 — 애매한 기사가 든 a 는 나오고, 표본 0 · 애매한 기사가 없는 b 는 나오지 않는다
        self.assertIn("첫 사건 기사 하나", out)
        self.assertNotIn("둘째 사건 기사", out)

    def test_review_rejects_negative_sample(self):
        self.write_fixture()
        with self.assertRaises(SystemExit) as caught, contextlib.redirect_stderr(io.StringIO()):
            self.run_tool("review", "--sample", "-1")
        self.assertEqual(caught.exception.code, 2)

    def label_sheet_and_measure(self):
        """sheet 가 쓴 시트에 라벨을 붙이고(101 ~ 103 은 a, 104 는 b, 나머지는 뺀다) measure 까지 돌린다."""
        code, _, err = self.run_tool("sheet", "--from", "2026-11-04", "--to", "2026-11-05", "--zone", ZONE)
        self.assertEqual((code, err), (0, ""), err)
        path = self.data / "articles.csv"
        with path.open(encoding="utf-8", newline="") as handle:
            rows = list(csv.reader(handle))
        for row in rows[1:]:
            row[4] = {"101": "a", "102": "a", "103": "a", "104": "b"}.get(row[0], "-")
        with path.open("w", encoding="utf-8", newline="") as handle:
            csv.writer(handle, lineterminator="\n").writerows(rows)
        code, _, err = self.run_tool("measure", "--embedding-model", "m")
        self.assertEqual((code, err), (0, ""), err)
        return path.read_text(encoding="utf-8")

    def test_sheet_feeds_measure(self):
        # 대조 leg — 발행 시각이 초 단위
        self.assertIn("\n101,2026-11-04T01:00:00Z,", self.label_sheet_and_measure())

    def test_sub_second_published_at_survives_the_sheet(self):
        # 발행 시각을 읽지 못한 기사는 받은 시각(소수 초)을 발행 시각으로 갖는다 — 시트가 소수 초를 버리면 측정이 어긋남으로 멈춘다
        with mock.patch.dict(ARTICLES, {101: ("2026-11-04T01:00:00.123456Z", ["동아"], "첫 사건 기사 하나")}):
            self.assertIn("\n101,2026-11-04T01:00:00.123456Z,", self.label_sheet_and_measure())

    def test_measure_record(self):
        self.write_fixture()
        code, out, err = self.run_tool("measure", "--embedding-model", "onnx/e5-small", "--note", "app=abc123")
        self.assertEqual((code, err), (0, ""), err)
        record = json.loads((self.data / "results" / "20261107-030000.json").read_text(encoding="utf-8"))
        self.assertEqual(record["format"], 3)
        self.assertEqual(record["measuredAt"], "2026-11-07T03:00:00Z")
        self.assertEqual(record["sample"], {"start": "2026-11-04", "end": "2026-11-05", "zone": ZONE, "labelRule": 2})
        self.assertEqual(record["labels"], {"articlesSha256": LABELS_SHA256})
        self.assertEqual(record["counts"], {
            "articles": 4, "excluded": 1, "goldEvents": 2,
            "bySource": {"JTBC": 1, "SBS": 1, "경향": 1, "동아": 1},
            "byDay": {"2026-11-04": 3, "2026-11-05": 1},
            "unlabeledSystemArticles": 2, "closedSystemEvents": 1})
        self.assertEqual(record["settings"], {
            "assignment": {"windowSeconds": 259200, "upper": 0.8, "lower": 0.55, "store": 0.4, "probability": 0.5},
            "judgementModels": ["jev-a", "jev-b"],
            "embeddingModel": "onnx/e5-small", "notes": {"app": "abc123"}})
        bcubed = record["bCubed"]
        self.assertAlmostEqual(bcubed["precision"], 3 / 4, places=12)
        self.assertAlmostEqual(bcubed["recall"], 2 / 3, places=12)
        self.assertAlmostEqual(bcubed["f1"], 12 / 17, places=12)
        self.assertEqual((bcubed["systemEvents"], bcubed["unassigned"], bcubed["goldSingletons"],
                          bcubed["systemSingletons"]), (2, 0, 1, 0))
        # 큰 판정은 재지 않는다 — 기록에 그 칸이 없고, 큰 판정 엔드포인트를 부르지 않는다
        self.assertEqual(sorted(record), ["bCubed", "counts", "format", "labels", "measuredAt", "sample", "settings"])
        paths = {request[1] for request in self.fake.requests}
        self.assertIn("/api/admin/events", paths)
        self.assertEqual({path for path in paths if "big" in path}, set())
        self.assertNotIn("큰 ", out)
        # 측정은 기사 텍스트를 내지 않고, 토큰을 출력 · 기록에 싣지 않는다(AC4 · 비밀 값)
        text = out + (self.data / "results" / "20261107-030000.json").read_text(encoding="utf-8")
        for title in TITLES:
            self.assertNotIn(title, text)
        self.assertNotIn(TOKEN, text)

    def test_removed_commands_are_rejected(self):
        self.write_fixture()
        for arguments in (("events",), ("measure", "--embedding-model", "m", "--big-criteria", "2:2.5")):
            with self.assertRaises(SystemExit) as caught, contextlib.redirect_stderr(io.StringIO()) as usage:
                self.run_tool(*arguments)
            self.assertEqual(caught.exception.code, 2)
            self.assertIn("events" if arguments == ("events",) else "--big-criteria", usage.getvalue())
        # 대조 — 같은 표본으로 남은 명령은 돈다
        self.assertEqual(self.run_tool("review")[0], 0)
        self.assertEqual(self.fake.requests, [])

    def test_article_in_two_events_stops(self):
        self.write_fixture()
        self.fake.events[12] = ("OPEN", [105, 106, 101])
        code, _, err = self.run_tool("measure", "--embedding-model", "m")
        self.assertEqual(code, 2)
        self.assertIn("기사 101 가 사건", err)
        self.assertFalse((self.data / "results").exists())

    def test_published_at_mismatch_stops(self):
        self.write_fixture(LABELS.replace("101,2026-11-04T01:00:00Z", "101,2026-11-04T01:00:01Z"))
        code, _, err = self.run_tool("measure", "--embedding-model", "m")
        self.assertEqual(code, 2)
        self.assertIn("기사 101 의 발행 시각이", err)

    def test_sample_of_another_label_rule_stops(self):
        # 다른 판의 라벨 기준으로 만든 표본은 재지 않는다 — 판이 다른 결과를 견주지 않게
        self.write_fixture()
        (self.data / "sample.json").write_text(SAMPLE.replace('"labelRule": 2', '"labelRule": 1'), encoding="utf-8")
        code, _, err = self.run_tool("measure", "--embedding-model", "m")
        self.assertEqual(code, 2)
        self.assertEqual(err, f"멈춤 — {self.data / 'sample.json'}: 라벨 기준 1판으로 만든 표본이다 — 이 도구는 2판이다\n")
        self.assertFalse((self.data / "results").exists())
        self.assertEqual(self.fake.requests, [])

    def test_wrong_token(self):
        self.write_fixture()
        code, _, err = self.run_tool("measure", "--embedding-model", "m", token="wrong-token-value")
        self.assertEqual(code, 3)
        self.assertIn("401", err)
        self.assertIn("운영자 토큰을 확인한다", err)
        self.assertNotIn("wrong-token-value", err)

    def test_missing_token(self):
        self.write_fixture()
        code, _, err = self.run_tool("measure", "--embedding-model", "m", token=None)
        self.assertEqual(code, 2)
        self.assertIn("YOZMNEWS_ADMIN_TOKEN", err)
        self.assertEqual(self.fake.requests, [])

    def test_server_period_limit_is_reported(self):
        code, _, err = self.run_tool("sheet", "--from", "2026-10-01", "--to", "2026-11-05", "--zone", ZONE)
        self.assertEqual(code, 3)
        self.assertIn("기간은 31일까지다", err)
        self.assertNotIn(TOKEN, err)
        self.assertFalse(self.data.exists())

    def test_redirect_is_not_followed(self):
        other = FakeAdmin()
        self.addCleanup(other.close)
        # 대조 leg — 두 번째 서버에 직접 보낸 요청은 기록된다
        code, _, _ = self.run_tool("sheet", "--from", "2026-11-04", "--to", "2026-11-05", "--zone", ZONE,
                                   base_url=other.url)
        self.assertEqual(code, 0)
        self.assertGreater(len(other.requests), 0)
        other.requests.clear()
        self.fake.redirect_to = other.url
        self.data = self.data.with_name("second")
        code, _, err = self.run_tool("sheet", "--from", "2026-11-04", "--to", "2026-11-05", "--zone", ZONE)
        self.assertEqual(code, 3)
        self.assertIn("302", err)
        self.assertEqual(other.requests, [])


if __name__ == "__main__":
    unittest.main()
