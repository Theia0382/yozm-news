# ADR-0091: 기본 로컬 모델은 multilingual-e5-small int8 을 DJL 토크나이저로 돌리고, 고정 리비전 · 해시로 빌드 때 받아 이미지에 담으며, ONNX Runtime 텔레메트리를 끄는 환경 변수가 없으면 모델을 열지 않는다

- 상태: 승인
- 날짜: 2026-11-07
- 근거: [이슈 #95](https://github.com/Theia0382/yozm-news/issues/95) — 한국어를 다루는 임베딩 모델 여덟을 같은 기사 표본으로 재고, ONNX Runtime · DJL tokenizers 를 root 가 아닌 컨테이너 · QEMU arm64 에뮬레이션에서 실측
- 관련: [ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)(결정 절 "기본 구현은 로컬 모델(ONNX Runtime)"의 모델 · 배포 — 보충, 후속 기록), [ADR-0090](0090-article-embedding-defaults-to-an-in-app-local-model-with-an-openai-compatible-api-by-setting.md)(기본은 로컬), [ADR-0022](0022-image-targets-per-side-dockerfile-without-run-in-runtime-stage.md)(실행 단계에 `RUN` 없음 — 그대로 지킨다), [ADR-0075](0075-article-vectors-in-a-dimensionless-vector-column-one-row-per-model-version.md)(모델 버전)

## 맥락

- 같은 기사 표본에서 multilingual-e5-small · bge-m3 · KURE-v1 의 품질 차이는 표본 오차 안이었다.
  - 무게는 e5-small(1.2억 파라미터, 384차원)이 bge-m3 · KURE-v1(약 5.7억, 1024차원)의 약 5분의 1 이다
  - e5-small 의 int8 양자화본(118 MB)은 fp32(470 MB)와 품질 차이가 표본 오차 안이었다
  - 라이선스는 셋 다 MIT 다. ko-sroberta-multitask 는 라이선스 표기가 없어 이미지에 담을 수 없다
- 실측(JVM, 4 스레드)에서 e5-small int8 은 기사당 약 13 ~ 15 ms, 프로세스 최대 RSS 약 0.9 ~ 1.0 GB 였다. 그 가운데 약 0.5 GB 가 토크나이저 적재분이다.
- e5 계열은 XLM-RoBERTa 의 SentencePiece Unigram 토크나이저(어휘 25만)를 쓴다.
  - DJL `ai.djl.huggingface:tokenizers` 는 HF tokenizers 의 Rust 네이티브라 모델의 `tokenizer.json` 을 그대로 읽는다. linux-aarch64 네이티브를 담는다
  - 순수 Java 구현은 정규화를 정확히 재현해야 하고, 어긋나면 품질이 조용히 깎인다
- ONNX Runtime Java jar 는 linux-x64 · linux-aarch64 네이티브를 담는다. root 가 아닌 컨테이너(uid 65534, 홈 없음)에서 네이티브를 `/tmp` 에 풀어 돌았고, arm64 는 QEMU 사용자 모드 에뮬레이션으로 결과까지 맞았다. 실기에서는 돌리지 못했다.
- ONNX Runtime 의 리눅스 네이티브는 기본으로 Microsoft 텔레메트리 주소에 연결을 시도한다. 끄는 API(`setTelemetry(false)`)로는 시도가 남았고, 환경 변수 `ORT_DISABLE_TELEMETRY=1` 로만 사라졌다(네트워크를 거르는 샌드박스의 차단 기록).
- 실행 단계 이미지에는 명령(`RUN`)을 두지 않는다([ADR-0022](0022-image-targets-per-side-dockerfile-without-run-in-runtime-stage.md)). 모델 파일은 아키텍처와 무관하다.
- e5 는 대칭 과제(기사끼리 비교)에 입력마다 `query: ` 를 붙이라고 안내한다.

## 결정

- **기본 로컬 모델은 `intfloat/multilingual-e5-small` 의 int8 양자화본(`onnx/model_qint8_avx512_vnni.onnx`)이다.** 입력마다 `query: ` 를 붙이고, 마지막 은닉 상태를 마스크로 평균 풀링한 뒤 L2 정규화한다.
- **토크나이저는 DJL `ai.djl.huggingface:tokenizers` 로 모델의 `tokenizer.json` 을 읽는다.**
- **모델 정보의 정본은 `backend/embedding-model.properties` 하나다.** 담는 것: 저장소 · 리비전 · 파일 경로와 sha256 · 모델 버전 · 차원 · 입력 앞 문자열. 빌드와 앱이 같은 파일을 읽는다. 파일 · 접두사가 바뀌면 모델 버전도 바꾼다.
- **모델 파일은 빌드 때 고정 리비전 · sha256 으로 받아 jar 의 별도 계층으로 이미지에 담는다.** 앱은 기동 때 해시를 다시 확인한다. 운영자가 모델 파일 경로를 바꾸는 설정은 두지 않는다.
- **ONNX Runtime 을 올리기 전에 `ORT_DISABLE_TELEMETRY=1` 을 확인하고, 없으면 모델을 열지 않는다.** 배포 이미지는 이 값을 갖는다.

## 결과

- 기본 구성의 임베딩은 실행 중 외부 접속 없이 돈다. 이미지가 약 118 MB(모델) · 약 75 MB(네이티브 jar) 커지고, 앱 프로세스의 메모리가 는다.
- 이미지 빌드와 백엔드 시험이 Hugging Face 에서 모델을 받는다(빌드 캐시에 둔다). 받지 못하면 빌드가 실패한다.
- 같은 모델 버전 문자열에 다른 모델 파일이 오지 않는다 — 해시가 다르면 빌드 · 기동이 실패한다.
- int8 행렬곱 결과는 CPU 에 따라 조금 다르다(같은 텍스트끼리 코사인 0.994 이상).
- 이미지 밖에서 띄우는 실행(시험 · IDE)도 텔레메트리 변수가 없으면 로컬 모델을 열지 못한다 — 빌드 도구의 시험 실행이 변수를 넣는다.
- 이미지를 게시하면 모델의 MIT 고지를 함께 담아야 한다.
- arm64 실기 실행은 확인되지 않았다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| multilingual-e5-small fp32 | 품질 차이가 표본 오차 안인데 파일 4배 · 메모리 약 0.35 ~ 0.45 GB 더 · 속도 절반이다 |
| bge-m3 · KURE-v1 | 품질을 가를 수 없는데 무게가 약 5배다. KURE-v1 은 공식 ONNX 가 없다 |
| 순수 Java Unigram 토크나이저 | 네이티브는 줄지만 정본 토크나이저와의 일치를 직접 지켜야 하고, 어긋나면 품질이 조용히 깎인다 |
| ONNX Runtime Extensions 의 토크나이저 연산 | linux-aarch64 네이티브가 없다 |
| 처음 기동 때 모델을 내려받는다 | 실행 중 외부 접속 · 쓰기 볼륨 · 받기 실패 처리가 생긴다 |
| 운영자가 모델 파일을 두고 경로를 설정한다 | 기본 구성으로 돌지 않고, 같은 모델 버전 문자열에 다른 파일이 올 수 있다 |
| 텔레메트리를 API 로 끈다 | 실측에서 연결 시도가 남았다 |
| 텔레메트리 변수가 없으면 경고만 남긴다 | 운영자가 모르는 외부 연결이 생긴다 |
