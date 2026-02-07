# Presentation Layer 구조 설명

## 📁 폴더 구조

```
presentation/
├── ui/
│   ├── main/
│   │   └── MainActivity.kt          # 앱 진입점, Compose 설정
│   ├── dashboard/
│   │   ├── DashboardScreen.kt       # 메인 대시보드 화면
│   │   ├── DashboardComponents.kt   # 재사용 가능한 UI 컴포넌트
│   │   └── DashboardState.kt        # UI 상태 데이터 클래스
│   └── theme/
│       ├── Color.kt                 # 색상 정의
│       ├── Theme.kt                 # Material3 테마
│       └── Type.kt                  # 타이포그래피 (필요시)
└── viewmodel/
    └── MainViewModel.kt             # UI 상태 관리 ViewModel
```

## 🎨 UI 디자인 구현 상태

### ✅ 완료된 부분

1. **상단 그라데이션 영역**
   - 주간 캘린더 (Mon-Fri)
   - 보호 상태 배지
   - 메인 숫자 (3,110 회)
   - 3단 통계 카드 (고/중/저위험)
   - 2단 차트 카드 (키워드/탐지시간)

2. **하단 흰색 영역**
   - Daily Updates 헤더
   - 탭 바 (통계/최근 알림/조언)
   - 일일 위험 탐지 요약 카드 (원형 게이지 포함)
   - 상세 위험 카드 3개 (프로그레스 바 포함)

### 🎯 주요 컴포넌트

#### DashboardScreen.kt
- `DashboardScreen()`: 메인 화면 레이아웃
- `WeekCalendarSection()`: 상단 주간 캘린더
- `StatusBadge()`: 보호 상태 배지
- `DailyRiskSummaryCard()`: 원형 게이지 카드
- `RiskRatioRow()`: 위험도 비율 표시

#### DashboardComponents.kt
- `DashboardCard()`: 공통 카드 스타일
- `SmallStatCard()`: 작은 통계 카드
- `ChartStatCard()`: 차트 카드
- `MiniBarChart()`: 미니 바 차트
- `DashboardTabBar()`: 탭 바
- `DetailedRiskCard()`: 상세 위험 카드

#### DashboardState.kt
- `DashboardUiState`: 전체 UI 상태
- `SecurityStatus`: 보호 상태 enum
- `DailyRiskStats`: 일일 통계
- `RiskDetail`: 위험 상세 정보

## 🔄 데이터 흐름

```
ScamAlertRepository (Room DB)
        ↓
MainViewModel (데이터 변환)
        ↓
DashboardUiState (UI 상태)
        ↓
DashboardScreen (UI 렌더링)
```

## 🎨 색상 팔레트

- **그라데이션**: `#EF6C4F` → `#F5C9B5`
- **고위험**: `#E94235` (빨강)
- **중위험**: `#FB8C00` (주황)
- **저위험**: `#FFB300` (노랑)
- **안전**: `#4CAF50` (초록)

## 📝 사용 방법

### MainActivity에서 사용
```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnGuardTheme {
                val uiState by viewModel.uiState.collectAsState()
                DashboardScreen(state = uiState)
            }
        }
    }
}
```

### 프리뷰
```kotlin
@Preview(showBackground = true, heightDp = 1200)
@Composable
fun DashboardScreenPreview() {
    DashboardScreen(state = DashboardUiState())
}
```

## 🚀 향후 개선 사항

1. **애니메이션 추가**
   - 숫자 카운트업 애니메이션
   - 원형 게이지 애니메이션
   - 카드 진입 애니메이션

2. **인터랙션 추가**
   - 탭 전환 기능
   - 카드 클릭 시 상세 화면
   - 스와이프 제스처

3. **차트 라이브러리 통합**
   - MPAndroidChart 또는 Vico
   - 실제 데이터 시각화

4. **다크 모드 지원**
   - 색상 팔레트 확장
   - 테마 전환 기능
