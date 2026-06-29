package com.example.alarm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.alarm.alarm.AlarmService
import com.example.alarm.data.Alarm
import com.example.alarm.data.AlarmDatabase
import com.example.alarm.data.AlarmRepository
import com.example.alarm.alarm.AlarmScheduler
import com.example.alarm.network.GeminiApiClient
import com.example.alarm.network.NewsItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AlarmDatabase.getDatabase(application)
    private val scheduler = AlarmScheduler(application)
    private val repository = AlarmRepository(database.alarmDao(), scheduler)

    // Alarm List State
    val alarms: StateFlow<List<Alarm>> = repository.allAlarms
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Selected screen: MAIN, RINGING, NEWS
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Main)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Math puzzle state
    private val _mathQuestion = MutableStateFlow("")
    val mathQuestion: StateFlow<String> = _mathQuestion.asStateFlow()

    private var correctMathAnswer: Int = 0

    private val _userAnswerInput = MutableStateFlow("")
    val userAnswerInput: StateFlow<String> = _userAnswerInput.asStateFlow()

    private val _mathError = MutableStateFlow<String?>(null)
    val mathError: StateFlow<String?> = _mathError.asStateFlow()

    // AI News State
    private val _newsState = MutableStateFlow<NewsUiState>(NewsUiState.Idle)
    val newsState: StateFlow<NewsUiState> = _newsState.asStateFlow()

    init {
        // Observe static ringing state of AlarmService
        viewModelScope.launch {
            AlarmService.ringingState.collect { ringingState ->
                when (ringingState) {
                    is AlarmService.RingingState.Ringing -> {
                        generateMathProblem()
                        _currentScreen.value = Screen.Ringing(ringingState.alarmId, ringingState.label)
                    }
                    AlarmService.RingingState.Idle -> {
                        if (_currentScreen.value is Screen.Ringing) {
                            _currentScreen.value = Screen.Main
                        }
                    }
                }
            }
        }
    }

    sealed interface Screen {
        object Main : Screen
        data class Ringing(val alarmId: Int, val label: String) : Screen
        object News : Screen
    }

    sealed interface NewsUiState {
        object Idle : NewsUiState
        object Loading : NewsUiState
        data class Success(val news: List<NewsItem>) : NewsUiState
        data class Error(val message: String) : NewsUiState
    }

    // --- Database Operations ---
    fun addAlarm(hour: Int, minute: Int, label: String, daysSelected: List<String>, toneUri: String = "", toneName: String = "Default Tone") {
        viewModelScope.launch {
            val daysStr = daysSelected.joinToString(",")
            val alarm = Alarm(
                hour = hour,
                minute = minute,
                label = label.ifBlank { "Alarm" },
                daysSelected = daysStr,
                toneUri = toneUri,
                toneName = toneName
            )
            repository.insert(alarm)
        }
    }

    fun updateAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.update(alarm)
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.delete(alarm)
        }
    }

    fun toggleAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.toggleEnabled(alarm)
        }
    }

    fun scheduleQuickTest(alarm: Alarm) {
        repository.scheduleQuickTestAlarm(delaySeconds = 5, alarmId = alarm.id, toneUri = alarm.toneUri)
    }

    // --- Math Operations ---
    fun generateMathProblem() {
        _userAnswerInput.value = ""
        _mathError.value = null

        val operators = listOf("+", "-", "*", "/")
        val operator = operators[Random.nextInt(operators.size)]

        when (operator) {
            "+" -> {
                val a = Random.nextInt(10, 100)
                val b = Random.nextInt(10, 100)
                _mathQuestion.value = "$a + $b = ?"
                correctMathAnswer = a + b
            }
            "-" -> {
                val a = Random.nextInt(20, 100)
                val b = Random.nextInt(10, a) // Ensure positive result
                _mathQuestion.value = "$a - $b = ?"
                correctMathAnswer = a - b
            }
            "*" -> {
                val a = Random.nextInt(2, 12)
                val b = Random.nextInt(3, 15)
                _mathQuestion.value = "$a × $b = ?"
                correctMathAnswer = a * b
            }
            "/" -> {
                val divisor = Random.nextInt(2, 10)
                val quotient = Random.nextInt(2, 12)
                val dividend = divisor * quotient // Ensure integer division
                _mathQuestion.value = "$dividend ÷ $divisor = ?"
                correctMathAnswer = quotient
            }
        }
    }

    fun onAnswerInputChanged(input: String) {
        // Only allow numbers and optional minus sign
        if (input.isEmpty() || input == "-" || input.all { it.isDigit() || it == '-' }) {
            _userAnswerInput.value = input
        }
    }

    fun submitMathAnswer() {
        val input = _userAnswerInput.value
        val userAns = input.toIntOrNull()
        if (userAns == null) {
            _mathError.value = "Please enter a valid number"
            return
        }

        if (userAns == correctMathAnswer) {
            // Success! Stop the alarm ringing
            AlarmService.stopAlarm(getApplication())
            // Transition to news and load news
            _currentScreen.value = Screen.News
            loadAiNews()
        } else {
            _mathError.value = "Incorrect. Try again!"
            // Re-generate to keep it interesting but also challenging
            generateMathProblem()
        }
    }

    // --- News Operations ---
    fun loadAiNews() {
        viewModelScope.launch {
            _newsState.value = NewsUiState.Loading
            try {
                val news = GeminiApiClient.fetchLatestAiNews()
                _newsState.value = NewsUiState.Success(news)
            } catch (e: Exception) {
                _newsState.value = NewsUiState.Error(e.message ?: "Unknown error loading news")
            }
        }
    }

    fun viewNewsDirectly() {
        _currentScreen.value = Screen.News
        loadAiNews()
    }

    fun navigateToMain() {
        _currentScreen.value = Screen.Main
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlarmViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AlarmViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
