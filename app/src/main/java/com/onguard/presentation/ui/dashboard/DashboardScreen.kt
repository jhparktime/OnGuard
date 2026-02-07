//app/src/main/java/com/onguard/presentation/ui/dashboard/DashboardScreen.kt

package com.onguard.presentation.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.onguard.presentation.theme.*
import java.util.*

@Composable
fun DashboardScreen(
    state: DashboardUiState = DashboardUiState()
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(DashboardTab.RATIO) }

    // 시니어의 팁: 하단이 뜨지 않도록 NoBouncy 설정 및 Stiffness를 살짝 높여 단단한 느낌 부여
    val commonSpringAlpha = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )
    val commonSpringSize = spring<IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    // 탭 전환과 동일한 느낌(MediumBouncy + 20dp Slide Up)을 위한 스펙
    val tabLikeSpringOffset = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    val tabLikeSpringAlpha = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (totalDrag < -100f && !isExpanded) { // Swipe Up -> Expand
                            isExpanded = true
                        } else if (totalDrag > 100f && isExpanded) { // Swipe Down -> Collapse
                            isExpanded = false
                        }
                    }
                ) { change, dragAmount ->
                    // Do not consume the change to allow scrollable children to handle it if needed
                    // But for the root logic, we just observe or consume if we are sure?
                    // If we consume, children might not get it.
                    // Actually, for "anywhere", we probably want to observe.
                    // However, compose gestures: if we use detectVerticalDragGestures, it usually consumes.
                    // To let children scroll, we usually need nested scroll connection or ensure detection doesn't block.
                    // But here, if the user swipes on a scrollable area, the scrollable usually wins priority.
                    // Let's consume change only if we want to claim it.
                    // If we don't consume, it passes?
                    // detectVerticalDragGestures consumes internally.
                    // Note: If we put this on root, and child consumes, this might not fire or get cancelled.
                    // Correct behavior: If child scrolls, we SHOULD NOT toggle expand.
                    // So standard detectVerticalDragGestures on root is fine. It will only trigger if child doesn't consume (e.g. at bounds or non-scroll area).
                    change.consume() 
                    totalDrag += dragAmount
                }
            }
            .background(BrandGradient)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. 상단 고정 영역 (날짜)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))
                WeekCalendarSection()
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 2. 중간 콘텐츠 영역 (축소/확장 대상)
            // shrinkVertically 시 정렬 기준을 Top으로 하여 자연스럽게 위로 말려 올라가게 함
            AnimatedVisibility(
                visible = !isExpanded,
                enter = expandVertically(
                    animationSpec = commonSpringSize,
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = commonSpringAlpha),
                exit = shrinkVertically(
                    animationSpec = commonSpringSize,
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = commonSpringAlpha)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StatusBadge(state.status)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "%,d".format(state.totalDetectionCount),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        lineHeight = 72.sp
                    )
                    Text(
                        text = "회",
                        fontSize = 20.sp,
                        color = TextWhite.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    // 통합된 위험도 카드
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .padding(20.dp)
                    ) {
                        Column {
                            // 타이틀 영역
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.05f)), // Light gray for icon bg
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = TextPrimary, // Dark icon
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "탐지 문자 위험도",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary // Dark text
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // 3개 수치 나열 (세로형 리스트)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RiskStatItem("고위험", state.highRiskCount, RiskHigh)
                                RiskStatItem("중위험", state.mediumRiskCount, RiskMedium)
                                RiskStatItem("저위험", state.lowRiskCount, RiskLow)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChartStatCard(
                            "탐지 키워드", state.totalKeywords.toString(), SearhKeyword, "개",
                            chartPlaceholder = { MiniBarChart(SearhKeyword) },
                            modifier = Modifier.weight(1f)
                        )
                        ChartStatCard(
                            "탐지 시간", state.totalDetectionHours.toString(), SearhTime, "시간",
                            chartPlaceholder = { MiniBarChart(SearhTime) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }

            // 3. 하단 영역 (Daily Updates)
            // animateContentSize를 통해 weight 변화를 부드럽게 처리
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)

            ) {
                // 제목 영역 (헤더)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Daily Updates",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = TextWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // [핵심 수정] 하단 시트 컨테이너
                // Box를 사용하여 배경을 겹쳐 그립니다.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // 남은 공간을 모두 차지
                ) {
                    // (1) 시각적 앵커(Visual Anchor):
                    // Surface 뒤에 동일한 색상의 Box를 배치하고 화면 아래로 확장(offset)시킵니다.
                    // 애니메이션 중 하단이 튀어 올라가도 이 Box가 빈 공간을 채워 '뿌리 깊은' 느낌을 줍니다.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .align(Alignment.BottomCenter)
                            .offset(y = 200.dp) // 화면 아래로 100dp 더 그려서 갭 방지
                            .background(CardBackground)
                    )

                    // (2) 실제 콘텐츠 영역
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = CardBackground,
                        // Surface 자체에 모양을 부여 (clip 모디파이어 대신 사용)
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // 탭 바
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                DashboardTabBar(
                                    selectedTab = selectedTab,
                                    onTabSelected = { selectedTab = it }
                                )
                            }

                            // 내부 스크롤 콘텐츠
                            // 확장 시 내용물이 나타나는 애니메이션 (탭 전환 효과와 동일하게 적용)
                            val density = LocalDensity.current
                            val slideDistance = with(density) { 20.dp.roundToPx() }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn(animationSpec = tabLikeSpringAlpha) +
                                        slideInVertically(
                                            animationSpec = tabLikeSpringOffset,
                                            initialOffsetY = { slideDistance }
                                        ),
                                exit = fadeOut(animationSpec = tabLikeSpringAlpha) +
                                        slideOutVertically(
                                            animationSpec = tabLikeSpringOffset,
                                            targetOffsetY = { slideDistance }
                                        )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            var totalDrag = 0f
                                            detectHorizontalDragGestures(
                                                onDragStart = { totalDrag = 0f },
                                                onDragEnd = {
                                                    val tabs = DashboardTab.values()
                                                    val currentIndex = tabs.indexOf(selectedTab)
                                                    if (totalDrag < -100f) { // Swipe Left -> Next
                                                        val nextIndex = (currentIndex + 1).coerceAtMost(tabs.lastIndex)
                                                        selectedTab = tabs[nextIndex]
                                                    } else if (totalDrag > 100f) { // Swipe Right -> Prev
                                                        val prevIndex = (currentIndex - 1).coerceAtLeast(0)
                                                        selectedTab = tabs[prevIndex]
                                                    }
                                                }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                totalDrag += dragAmount
                                            }
                                        }
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 20.dp)
                                        // 스크롤 시 하단 네비게이션 바 등에 가리지 않도록 패딩 추가
                                        .navigationBarsPadding()
                                        .padding(bottom = 20.dp)
                                ) {
                                    when (selectedTab) {
                                        DashboardTab.RATIO -> {
                                            StaggeredAnimatedItem(delay = 0) {
                                                DailyRiskSummaryCard(state.dailyStats)
                                            }
                                            Spacer(modifier = Modifier.height(40.dp))
                                        }
                                        DashboardTab.HISTORY -> {
                                            StaggeredAnimatedItem(delay = 0) {
                                                DetailedRiskCard("일일 고위험 탐지", state.dailyStats.highRiskDetail, RiskHigh, Icons.Default.Warning)
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            StaggeredAnimatedItem(delay = 100) {
                                                DetailedRiskCard("일일 중위험 탐지", state.dailyStats.mediumRiskDetail, RiskMedium, Icons.Default.ErrorOutline)
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            StaggeredAnimatedItem(delay = 200) {
                                                DetailedRiskCard("일일 저위험 탐지", state.dailyStats.lowRiskDetail, RiskLow, Icons.Default.HelpOutline)
                                            }
                                            Spacer(modifier = Modifier.height(40.dp))
                                        }
                                        DashboardTab.LOG -> {
                                            Text(
                                                "로그 기능은 준비 중입니다.",
                                                modifier = Modifier.padding(vertical = 40.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StaggeredAnimatedItem(
    delay: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        visible = true
    }

    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 20.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "offsetY"
    )

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationY = offsetY.toPx()
                this.alpha = alpha
            }
    ) {
        content()
    }
}

@Composable
fun WeekCalendarSection() {
    val calendar = Calendar.getInstance()
    val days = mutableListOf<Triple<String, Int, Int>>()
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    for (offset in -2..2) {
        val tempCalendar = Calendar.getInstance()
        tempCalendar.add(Calendar.DAY_OF_MONTH, offset)
        val dayOfWeek = tempCalendar.get(Calendar.DAY_OF_WEEK)
        val dayName = dayNames[(dayOfWeek + 5) % 7]
        val dayNumber = tempCalendar.get(Calendar.DAY_OF_MONTH)
        days.add(Triple(dayName, dayNumber, offset))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        days.forEach { (dayName, dayNumber, offset) ->
            val isToday = offset == 0
            val alpha = when (kotlin.math.abs(offset)) {
                0 -> 1.0f
                1 -> 0.85f
                2 -> 0.70f
                else -> 0.70f
            }
            val heightOffset = when (offset) {
                -2, 2 -> 0.dp
                -1, 1 -> 10.dp
                0 -> 20.dp
                else -> 0.dp
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .offset(y = heightOffset)
            ) {
                Text(
                    text = dayName,
                    fontSize = 12.sp,
                    color = TextWhite.copy(alpha = alpha),
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dayNumber.toString(),
                    fontSize = if (isToday) 18.sp else 16.sp,
                    color = TextWhite.copy(alpha = alpha),
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: SecurityStatus) {
    Surface(
        color = Color.White.copy(alpha = 0.25f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = TextWhite,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (status == SecurityStatus.PROTECTED) "보호중" else "미보호",
                color = TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun DailyRiskSummaryCard(dailyStats: DailyRiskStats) {
    DashboardCard(backgroundColor = BackgroundLight) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "일일 위험 탐지",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    "고위험 + 중위험 + 저위험",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(20.dp))
                RiskRatioRow("일일 고위험 탐지", dailyStats.highRiskRatio, RiskHigh, Icons.Default.Warning)
                Spacer(modifier = Modifier.height(10.dp))
                RiskRatioRow("일일 중위험 탐지", dailyStats.mediumRiskRatio, RiskMedium, Icons.Default.ErrorOutline)
                Spacer(modifier = Modifier.height(10.dp))
                RiskRatioRow("일일 저위험 탐지", dailyStats.lowRiskRatio, RiskLow, Icons.Default.HelpOutline)
            }
            Spacer(modifier = Modifier.width(20.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                CircularProgressIndicator(
                    progress = 1f,
                    modifier = Modifier.size(140.dp),
                    color = Color(0xFFE0E0E0),
                    strokeWidth = 16.dp
                )
                CircularProgressIndicator(
                    progress = dailyStats.highRiskRatio,
                    modifier = Modifier.size(140.dp),
                    color = RiskHigh,
                    strokeWidth = 16.dp
                )
                CircularProgressIndicator(
                    progress = dailyStats.highRiskRatio + dailyStats.mediumRiskRatio,
                    modifier = Modifier.size(140.dp),
                    color = RiskMedium,
                    strokeWidth = 16.dp
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "누적",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        "%,d".format(dailyStats.cumulativeCount),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun RiskRatioRow(title: String, ratio: Float, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, color = color)
            Text("${(ratio * 100)}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RiskStatItem(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
        
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "%,d".format(count),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "회",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
fun DashboardScreenPreview() {
    DashboardScreen(state = DashboardUiState())
}