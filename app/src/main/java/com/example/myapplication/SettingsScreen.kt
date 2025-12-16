package com.example.myapplication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Re:fit 설정 화면
 * - 기본 설정 탭: 운동 설정, 피드백 설정, 화면 표시 설정
 * - 개인 설정 탭: 신체 정보, 목표 설정
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    var selectedTabIndex by remember { mutableStateOf(0) }

    val tabs = listOf("기본 설정", "개인 설정")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "설정",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로 가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 탭 행
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    )
                }
            }

            // 탭 내용
            when (selectedTabIndex) {
                0 -> DefaultSettingsTab(settingsDataStore)
                1 -> PersonalSettingsTab(settingsDataStore)
            }
        }
    }
}

/**
 * 기본 설정 탭
 */
@Composable
private fun DefaultSettingsTab(settingsDataStore: SettingsDataStore) {
    val scope = rememberCoroutineScope()

    // 설정값 수집
    val exerciseDuration by settingsDataStore.exerciseDuration.collectAsState(initial = 60)
    val restDuration by settingsDataStore.restDuration.collectAsState(initial = 30)
    val countingSpeed by settingsDataStore.countingSpeed.collectAsState(initial = "보통")
    val accuracyThreshold by settingsDataStore.accuracyThreshold.collectAsState(initial = 0.85f)
    val voiceGuidance by settingsDataStore.voiceGuidance.collectAsState(initial = true)
    val vibrationFeedback by settingsDataStore.vibrationFeedback.collectAsState(initial = true)
    val realtimeCorrection by settingsDataStore.realtimeCorrection.collectAsState(initial = true)
    val completionNotification by settingsDataStore.completionNotification.collectAsState(initial = true)
    val showSkeleton by settingsDataStore.showSkeleton.collectAsState(initial = true)
    val showKeypoints by settingsDataStore.showKeypoints.collectAsState(initial = false)
    val showConfidence by settingsDataStore.showConfidence.collectAsState(initial = true)
    val counterSize by settingsDataStore.counterSize.collectAsState(initial = "보통")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 운동 기본 설정
        item {
            SettingSection(title = "운동 기본 설정") {
                DropdownSetting(
                    label = "기본 운동 시간",
                    options = listOf("30초" to 30, "1분" to 60, "2분" to 120),
                    selectedValue = when (exerciseDuration) {
                        30 -> "30초"
                        60 -> "1분"
                        120 -> "2분"
                        else -> "사용자 지정"
                    },
                    onValueChange = { value ->
                        scope.launch {
                            settingsDataStore.setExerciseDuration(
                                when (value) {
                                    "30초" -> 30
                                    "1분" -> 60
                                    "2분" -> 120
                                    else -> 60
                                }
                            )
                        }
                    }
                )

                DropdownSetting(
                    label = "휴식 시간",
                    options = listOf("10초" to 10, "30초" to 30, "1분" to 60),
                    selectedValue = when (restDuration) {
                        10 -> "10초"
                        30 -> "30초"
                        60 -> "1분"
                        else -> "30초"
                    },
                    onValueChange = { value ->
                        scope.launch {
                            settingsDataStore.setRestDuration(
                                when (value) {
                                    "10초" -> 10
                                    "30초" -> 30
                                    "1분" -> 60
                                    else -> 30
                                }
                            )
                        }
                    }
                )

                RadioGroupSetting(
                    label = "카운팅 속도",
                    options = listOf("느림", "보통", "빠름"),
                    selectedValue = countingSpeed,
                    onValueChange = { scope.launch { settingsDataStore.setCountingSpeed(it) } }
                )

                SliderSetting(
                    label = "자세 정확도 기준",
                    value = accuracyThreshold,
                    valueRange = 0.75f..0.95f,
                    steps = 1,
                    onValueChange = { scope.launch { settingsDataStore.setAccuracyThreshold(it) } },
                    valueLabel = { String.format("%.0f%%", it * 100) }
                )
            }
        }

        // 피드백 설정
        item {
            SettingSection(title = "피드백 설정") {
                SwitchSetting(
                    label = "음성 안내",
                    checked = voiceGuidance,
                    onCheckedChange = { scope.launch { settingsDataStore.setVoiceGuidance(it) } }
                )

                SwitchSetting(
                    label = "진동 피드백",
                    checked = vibrationFeedback,
                    onCheckedChange = { scope.launch { settingsDataStore.setVibrationFeedback(it) } }
                )

                SwitchSetting(
                    label = "실시간 교정 알림",
                    checked = realtimeCorrection,
                    onCheckedChange = { scope.launch { settingsDataStore.setRealtimeCorrection(it) } }
                )

                SwitchSetting(
                    label = "운동 완료 알림",
                    checked = completionNotification,
                    onCheckedChange = { scope.launch { settingsDataStore.setCompletionNotification(it) } }
                )
            }
        }

        // 화면 표시 설정
        item {
            SettingSection(title = "화면 표시 설정") {
                SwitchSetting(
                    label = "스켈레톤 표시",
                    checked = showSkeleton,
                    onCheckedChange = { scope.launch { settingsDataStore.setShowSkeleton(it) } }
                )

                SwitchSetting(
                    label = "키포인트 좌표 표시",
                    checked = showKeypoints,
                    onCheckedChange = { scope.launch { settingsDataStore.setShowKeypoints(it) } }
                )

                SwitchSetting(
                    label = "신뢰도 수치 표시",
                    checked = showConfidence,
                    onCheckedChange = { scope.launch { settingsDataStore.setShowConfidence(it) } }
                )

                RadioGroupSetting(
                    label = "카운터 크기",
                    options = listOf("작게", "보통", "크게"),
                    selectedValue = counterSize,
                    onValueChange = { scope.launch { settingsDataStore.setCounterSize(it) } }
                )
            }
        }
    }
}

/**
 * 개인 설정 탭
 */
@Composable
private fun PersonalSettingsTab(settingsDataStore: SettingsDataStore) {
    val scope = rememberCoroutineScope()

    // 설정값 수집
    val userHeight by settingsDataStore.userHeight.collectAsState(initial = 170)
    val userWeight by settingsDataStore.userWeight.collectAsState(initial = 70)
    val exerciseLevel by settingsDataStore.exerciseLevel.collectAsState(initial = "초보자")
    val jointRestrictions by settingsDataStore.jointRestrictions.collectAsState(initial = emptySet())
    val preferredBodyParts by settingsDataStore.preferredBodyParts.collectAsState(initial = emptySet())
    val weeklyGoalCount by settingsDataStore.weeklyGoalCount.collectAsState(initial = 3)
    val weeklyGoalTime by settingsDataStore.weeklyGoalTime.collectAsState(initial = 150)
    val priority by settingsDataStore.priority.collectAsState(initial = "정확도")
    val healthGoal by settingsDataStore.healthGoal.collectAsState(initial = "근력향상")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 신체 정보
        item {
            SettingSection(title = "신체 정보") {
                NumberInputSetting(
                    label = "키 (cm)",
                    value = userHeight,
                    onValueChange = { scope.launch { settingsDataStore.setUserHeight(it) } },
                    range = 100..250
                )

                NumberInputSetting(
                    label = "몸무게 (kg)",
                    value = userWeight,
                    onValueChange = { scope.launch { settingsDataStore.setUserWeight(it) } },
                    range = 30..200
                )

                DropdownSetting(
                    label = "운동 경험",
                    options = listOf("초보자" to 1, "중급자" to 2, "고급자" to 3),
                    selectedValue = exerciseLevel,
                    onValueChange = { scope.launch { settingsDataStore.setExerciseLevel(it) } }
                )

                MultiSelectSetting(
                    label = "관절 제한사항",
                    options = listOf("무릎", "어깨", "허리", "발목", "손목"),
                    selectedOptions = jointRestrictions,
                    onSelectionChange = { scope.launch { settingsDataStore.setJointRestrictions(it) } }
                )

                MultiSelectSetting(
                    label = "선호 운동 부위",
                    options = listOf("상체", "하체", "전신", "코어"),
                    selectedOptions = preferredBodyParts,
                    onSelectionChange = { scope.launch { settingsDataStore.setPreferredBodyParts(it) } }
                )
            }
        }

        // 목표 설정
        item {
            SettingSection(title = "목표 설정") {
                NumberInputSetting(
                    label = "주간 운동 목표 (회)",
                    value = weeklyGoalCount,
                    onValueChange = { scope.launch { settingsDataStore.setWeeklyGoalCount(it) } },
                    range = 1..14
                )

                NumberInputSetting(
                    label = "주간 운동 목표 (분)",
                    value = weeklyGoalTime,
                    onValueChange = { scope.launch { settingsDataStore.setWeeklyGoalTime(it) } },
                    range = 30..600
                )

                RadioGroupSetting(
                    label = "자세 개선 우선순위",
                    options = listOf("정확도", "반복 횟수"),
                    selectedValue = priority,
                    onValueChange = { scope.launch { settingsDataStore.setPriority(it) } }
                )

                DropdownSetting(
                    label = "건강 목표",
                    options = listOf("근력향상" to 1, "체중관리" to 2, "자세교정" to 3, "재활" to 4),
                    selectedValue = healthGoal,
                    onValueChange = { scope.launch { settingsDataStore.setHealthGoal(it) } }
                )
            }
        }
    }
}

// 설정 섹션 컴포넌트들
@Composable
private fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueLabel: (Float) -> String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = valueLabel(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun RadioGroupSetting(
    label: String,
    options: List<String>,
    selectedValue: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(Modifier.selectableGroup()) {
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .selectable(
                            selected = (option == selectedValue),
                            onClick = { onValueChange(option) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (option == selectedValue),
                        onClick = null
                    )
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(
    label: String,
    options: List<Pair<String, Int>>,
    selectedValue: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (optionText, _) ->
                    DropdownMenuItem(
                        text = { Text(optionText) },
                        onClick = {
                            onValueChange(optionText)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberInputSetting(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value.toString(),
            onValueChange = {
                it.toIntOrNull()?.let { newValue ->
                    if (newValue in range) {
                        onValueChange(newValue)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun MultiSelectSetting(
    label: String,
    options: List<String>,
    selectedOptions: Set<String>,
    onSelectionChange: (Set<String>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        options.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = selectedOptions.contains(option),
                    onCheckedChange = { checked ->
                        val newSelection = if (checked) {
                            selectedOptions + option
                        } else {
                            selectedOptions - option
                        }
                        onSelectionChange(newSelection)
                    }
                )
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
