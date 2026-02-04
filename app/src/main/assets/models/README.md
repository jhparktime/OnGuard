# ONNX LLM 모델 다운로드 안내

## 필요한 파일

| 파일명 | 크기 | 용도 |
|--------|------|------|
| `model_q4f16.onnx` | 118MB | Android 모바일용 경량 LLM 스캠 탐지 |

**중요:** 이 ONNX 모델은 온디바이스 LLM(예: SmolLM2 계열)을 위해 최적화된 **양자화(q4 + fp16)** 버전입니다.

## 다운로드 방법

### Hugging Face에서 다운로드 (예시)

1. SmolLM2 계열 ONNX 저장소(예: `SmolLM2-135M-Instruct` ONNX 브랜치) 접속
2. 로그인 후 라이선스 동의
3. **`model_q4f16.onnx`** 파일 다운로드 (경량 양자화 버전)
4. 이 폴더 (`app/src/main/assets/models/`)에 복사

### 파일 배치 확인

```
app/src/main/assets/models/
├── README.md                          # 이 파일
└── model_q4f16.onnx                  # ONNX LLM 모델 (경량, 직접 다운로드)
```

## 저사양 기기 (S10e, 8GB RAM 등)

앱은 ONNX Runtime을 사용하여 경량 LLM을 로드하며,  
메모리 부족 등으로 초기화에 실패하면 LLM은 비활성화되고 Rule-based 탐지만 사용됩니다 (앱은 정상 동작).

## 모델 없이 실행하기

**모델 파일이 없어도 앱은 정상 작동합니다!**

- LLM 기능은 자동으로 비활성화됨
- Rule-based 탐지만 사용됨 (KeywordMatcher + UrlAnalyzer)
- 앱 시작 시 로그에 경고만 표시됨

## 왜 Git에 포함되지 않나요?

| 이유 | 설명 |
|------|------|
| 파일 크기 | 118MB - Git LFS로 관리 필요 |
| Clone 속도 | 저장소 clone 시간 대폭 증가 |
| 라이선스 | Google Gemma 약관에 따라 개별 동의 필요 |

## 참고 링크

- [ONNX Runtime](https://onnxruntime.ai/)
