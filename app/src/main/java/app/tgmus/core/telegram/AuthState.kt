package app.tgmus.core.telegram

/** Состояние входа в Telegram в виде, удобном для интерфейса. */
sealed interface AuthState {
    /** TDLib запускается или обрабатывает запрос. */
    data object Loading : AuthState

    /** В сборке нет api_id / api_hash. */
    data object NotConfigured : AuthState

    data object WaitPhone : AuthState

    data class WaitCode(
        val phone: String,
        val delivery: CodeDelivery,
        val resendTimeoutSec: Int,
    ) : AuthState

    data class WaitEmailCode(val emailPattern: String) : AuthState

    data class WaitPassword(val hint: String) : AuthState

    /** Telegram требует шаг, который приложение пока не умеет. */
    data class Unsupported(val reason: String) : AuthState

    /** TDLib не смог запуститься. */
    data class Failed(val message: String) : AuthState

    data object Ready : AuthState
}

/** Куда Telegram отправил код входа. */
enum class CodeDelivery { TelegramApp, Sms, Call, Other }

class TelegramException(val code: Int, val rawMessage: String) :
    Exception("Telegram error $code: $rawMessage")
