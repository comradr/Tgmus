package app.tgmus.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.tgmus.TgmusApp
import app.tgmus.core.telegram.AuthState
import app.tgmus.core.telegram.TelegramClient
import app.tgmus.core.telegram.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val step: AuthState = AuthState.Loading,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    /** Растёт после каждой повторной отправки кода, чтобы таймер на экране запускался заново. */
    val resendCount: Int = 0,
)

class AuthViewModel(private val telegram: TelegramClient) : ViewModel() {

    private data class Local(
        val isSubmitting: Boolean = false,
        val error: String? = null,
        /** Пользователь вернулся с экрана кода, чтобы исправить номер. */
        val editingPhone: Boolean = false,
        val resendCount: Int = 0,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<AuthUiState> = combine(telegram.authState, local) { auth, local ->
        val canEditPhone = auth is AuthState.WaitCode ||
            auth is AuthState.WaitEmailCode ||
            auth is AuthState.WaitPassword ||
            auth is AuthState.Unsupported
        AuthUiState(
            step = if (local.editingPhone && canEditPhone) AuthState.WaitPhone else auth,
            isSubmitting = local.isSubmitting,
            error = local.error,
            resendCount = local.resendCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthUiState())

    fun submitPhone(rawPhone: String) {
        val phone = normalizePhone(rawPhone)
        if (phone.length < 8) {
            local.update { it.copy(error = "Введи номер полностью, с кодом страны.") }
            return
        }
        submit(onSuccess = { it.copy(editingPhone = false) }) { telegram.setPhoneNumber(phone) }
    }

    fun submitCode(code: String) {
        if (code.isBlank()) return
        submit { telegram.checkCode(code.trim()) }
    }

    fun submitEmailCode(code: String) {
        if (code.isBlank()) return
        submit { telegram.checkEmailCode(code.trim()) }
    }

    fun submitPassword(password: String) {
        if (password.isEmpty()) return
        submit { telegram.checkPassword(password) }
    }

    fun resendCode() = submit(onSuccess = { it.copy(resendCount = it.resendCount + 1) }) { telegram.resendCode() }

    fun editPhone() = local.update { it.copy(editingPhone = true, error = null) }

    fun clearError() = local.update { it.copy(error = null) }

    fun retryStart() = telegram.retry()

    private fun submit(onSuccess: (Local) -> Local = { it }, request: suspend () -> Unit) {
        if (local.value.isSubmitting) return
        local.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                // Без сети TDLib держит запрос бесконечно, поэтому ограничиваем ожидание.
                withTimeout(REQUEST_TIMEOUT_MS) { request() }
                local.update { onSuccess(it).copy(isSubmitting = false) }
            } catch (e: TimeoutCancellationException) {
                local.update { it.copy(isSubmitting = false, error = e.toUserMessage()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(isSubmitting = false, error = e.toUserMessage()) }
            }
        }
    }

    /** Оставляет только цифры и приводит российское «8…» к «7…». */
    private fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length == 11 && digits.startsWith("8")) "7" + digits.drop(1) else digits
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 30_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AuthViewModel((this[APPLICATION_KEY] as TgmusApp).telegram)
            }
        }
    }
}
