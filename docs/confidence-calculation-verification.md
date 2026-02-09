# Confidence Calculation Logic Verification

## 개선된 로직 개요

### 3-Path 분기 구조

```
Rule 계산 (0~1 범위)
    ↓
강한 신호 체크
    ├─ YES → Rule-only 반환 (0.85~1.0)
    │         - Counter Scam 112 DB 전화번호
    │         - KISA 악성 URL
    │         - 경찰청 사기계좌 DB
    │         - 황금 패턴 (긴급+금전+URL)
    │
    └─ NO → Rule < 0.3?
            ├─ YES → Rule-only 반환 (0~0.3)
            └─ NO → LLM 호출
                    ↓
                Rule 30% + LLM 70%
```

## 테스트 시나리오

### Case 1: 강한 신호 (Rule-only, 높은 신뢰도)

**입력:**
```
이 계좌로 송금해줘 123-456-789
```

**분석 과정:**
1. **Keyword**: "송금", "계좌" → confidence ≈ 0.5
2. **Account**: 계좌번호 탐지 → riskScore ≈ 0.4
   - 계좌번호 보너스: +0.15 → 0.5 + 0.4*0.15 = 0.56
3. **Golden Pattern**: (긴급X, 금전O, URLX) → 미적용
4. **ruleConfidence**: 0.56 → 0.6 (정규화)
5. **isStrongSignal()**: 
   - DB 계좌 히트? 미확인 (실제 DB 조회 필요)
   - 가정: DB 미등록 → false
6. **shouldUseLLM**: ruleConfidence(0.6) >= 0.3 && hasMoney(true) → **true**
7. **LLM 호출** → 단순 가중 평균

**예상 출력:**
- 경로: **Rule + LLM**
- 최종 confidence: `0.6 * 0.3 + llm_score * 0.7`
  - LLM이 0.8 판단 시: `0.18 + 0.56 = 0.74`

**수정된 시나리오 (DB 히트 가정):**
- Account DB 히트 → `accountResult.hasFraudAccounts = true`
- **isStrongSignal()**: true
- 경로: **Rule-only**
- 최종 confidence: **0.85~1.0**

---

### Case 2: LLM 결합 - LLM이 낮게 판단 (친구 대화)

**입력:**
```
돈이 필요해
```

**분석 과정:**
1. **Keyword**: "돈" → confidence ≈ 0.3
2. **URL/Phone/Account**: 없음 → 추가 보너스 없음
3. **ruleConfidence**: 0.3
4. **isStrongSignal()**: false (DB 히트 없음)
5. **shouldUseLLM**: ruleConfidence(0.3) >= 0.3 && hasMoney(true) → **true**
6. **LLM 호출**:
   - 맥락: "친구와의 일상 대화"
   - LLM 판단: 0.2 (정상 대화)
7. **가중 평균**: `0.3 * 0.3 + 0.2 * 0.7 = 0.09 + 0.14 = 0.23`

**예상 출력:**
- 경로: **Rule + LLM**
- 최종 confidence: **0.23** (낮음, 정상)
- isScam: false (< 0.5)

---

### Case 3: LLM 결합 - LLM이 높게 판단 (사기 의심)

**입력:**
```
급하게 돈 좀 빌려줄 수 있어?
```

**분석 과정:**
1. **Keyword**: "돈", "급하" → confidence ≈ 0.4
2. **URL/Phone/Account**: 없음
3. **ruleConfidence**: 0.4
4. **isStrongSignal()**: false
5. **shouldUseLLM**: ruleConfidence(0.4) >= 0.3 && (hasMoney || hasUrgency) → **true**
6. **LLM 호출**:
   - 맥락: "긴급 금전 요구, 사전 맥락 부족"
   - LLM 판단: 0.75 (사기 의심)
7. **가중 평균**: `0.4 * 0.3 + 0.75 * 0.7 = 0.12 + 0.525 = 0.645`

**예상 출력:**
- 경로: **Rule + LLM**
- 최종 confidence: **0.65** (중고위험)
- isScam: true (> 0.5)

---

## 구현 확인 체크리스트

### ✅ 완료된 항목

1. **`isStrongSignal()` 함수 추가**
   - Counter Scam 112 DB 전화번호 체크
   - KISA 악성 URL 체크
   - 경찰청 사기계좌 DB 체크
   - 황금 패턴 체크
   
2. **`combineResultsSimple()` 함수 추가**
   - 단순 가중 평균 (Rule 30%, LLM 70%)
   - 복잡한 2단계 조정 제거
   
3. **`analyze()` 흐름 수정**
   - Rule 계산 후 `isStrongSignal()` 체크
   - 강한 신호 시 즉시 Rule-only 반환
   - LLM 경로에서 `combineResultsSimple()` 호출
   - Rule 신뢰도 0.65 캡 제거 (0~1 범위)

4. **기존 `combineResults()` 삭제**
   - 동적 가중치 로직 제거
   - LLM 밴드 보정 (×0.7, +0.2) 제거

5. **클래스 KDoc 업데이트**
   - 3-Path 분기 구조 설명 추가
   - 강한 신호 정의 명시

### 🔍 검증 필요 항목

1. **강한 신호 경로 테스트**
   - DB에 등록된 전화번호/계좌번호로 테스트
   - confidence가 0.8~1.0 범위인지 확인
   - LLM 호출이 생략되는지 확인

2. **LLM 낮음 경로 테스트**
   - 친구 대화 시뮬레이션
   - confidence가 0.2~0.3 범위인지 확인
   - isScam = false 확인

3. **LLM 높음 경로 테스트**
   - 사기 의심 메시지 시뮬레이션
   - confidence가 0.6~0.8 범위인지 확인
   - isScam = true 확인

4. **경계값 테스트**
   - ruleConfidence = 0.3 정확히 일치 시
   - ruleConfidence = 0.5 정확히 일치 시
   - LLM confidence = 0.5 정확히 일치 시

## 개선된 로직의 장점

### 1. 예측 가능성
- 단순 가중 평균으로 결과 계산 명확
- 경계값 동작 이해하기 쉬움

### 2. 디버깅 용이성
- 3가지 경로만 존재 (강한 신호, LLM 결합, Rule-only)
- 로그로 어떤 경로를 탔는지 명확히 확인 가능

### 3. 강한 신호 보존
- DB 히트는 LLM 영향 없이 높은 점수 유지
- 허위 긍정(False Positive) 감소

### 4. LLM 신뢰 반영
- 70% 가중치로 맥락 판단 존중
- 친구 대화와 사기 구분 능력 향상

### 5. MVP 적합성
- 복잡도 낮아 빠른 검증 가능
- 튜닝 포인트 명확 (가중치, 강한 신호 조건)

## 향후 튜닝 가이드

### 가중치 조정
```kotlin
// 현재: Rule 30%, LLM 70%
private const val RULE_WEIGHT = 0.3f
private const val LLM_WEIGHT = 0.7f

// Rule을 더 신뢰하려면:
private const val RULE_WEIGHT = 0.4f
private const val LLM_WEIGHT = 0.6f
```

### 강한 신호 조건 추가
```kotlin
// isStrongSignal() 함수에 조건 추가 예시:
if (ruleConfidence >= 0.85f && hasMoney && hasUrgency) {
    return true  // 매우 높은 Rule 점수도 강한 신호로 간주
}
```

### LLM 트리거 임계값 조정
```kotlin
// 현재: ruleConfidence >= 0.3f
// 더 엄격하게 (LLM 호출 줄이기):
ruleConfidence >= 0.4f

// 더 느슨하게 (LLM 호출 늘리기):
ruleConfidence >= 0.2f
```
