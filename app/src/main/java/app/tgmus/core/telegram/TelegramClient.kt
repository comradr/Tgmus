package app.tgmus.core.telegram

import android.content.Context
import android.os.Build
import android.util.Log
import app.tgmus.BuildConfig
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Единственная точка общения с Telegram (TDLib).
 *
 * Остальной код не видит org.drinkless.tdlib.Client: он подписывается на [authState] и [updates]
 * и отправляет запросы через [send].
 */
class TelegramClient(context: Context) {

    private val appContext = context.applicationContext
    private val lock = Any()

    private var client: Client? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _updates = MutableSharedFlow<TdApi.Object>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Все обновления TDLib (новые сообщения, прогресс загрузки файлов и так далее). */
    val updates: SharedFlow<TdApi.Object> = _updates.asSharedFlow()

    val isConfigured: Boolean
        get() = BuildConfig.TG_API_ID != 0 && BuildConfig.TG_API_HASH.isNotBlank()

    /** Запускает TDLib. Повторные вызовы безопасны. */
    fun start() {
        if (!isConfigured) {
            _authState.value = AuthState.NotConfigured
            return
        }
        synchronized(lock) {
            if (client == null) client = createClientOrFail()
        }
    }

    /** Повторяет запуск после ошибки ([AuthState.Failed]). */
    fun retry() {
        if (_authState.value !is AuthState.Failed) return
        _authState.value = AuthState.Loading
        val current = synchronized(lock) { client }
        if (current == null) start() else sendParameters()
    }

    /** Отправляет запрос и ждёт ответ. Ошибка Telegram приходит как [TelegramException]. */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): T {
        val current = synchronized(lock) { client }
            ?: throw TelegramException(500, "CLIENT_NOT_STARTED")
        return suspendCancellableCoroutine { continuation ->
            current.send(function) { result ->
                if (!continuation.isActive) return@send
                if (result is TdApi.Error) {
                    continuation.resumeWithException(TelegramException(result.code, result.message))
                } else {
                    continuation.resume(result as T)
                }
            }
        }
    }

    suspend fun setPhoneNumber(phone: String) {
        send(TdApi.SetAuthenticationPhoneNumber(phone, null))
    }

    suspend fun checkCode(code: String) {
        send(TdApi.CheckAuthenticationCode(code))
    }

    suspend fun checkEmailCode(code: String) {
        send(TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(code)))
    }

    suspend fun resendCode() {
        send(TdApi.ResendAuthenticationCode(TdApi.ResendCodeReasonUserRequest()))
    }

    suspend fun checkPassword(password: String) {
        send(TdApi.CheckAuthenticationPassword(password))
    }

    suspend fun getMe(): TdApi.User = send(TdApi.GetMe())

    suspend fun logOut() {
        send(TdApi.LogOut())
    }

    /** Создаёт клиент. Если TDLib не загрузился (например, нет библиотеки под процессор), не роняет приложение. */
    private fun createClientOrFail(): Client? = try {
        createClient()
    } catch (t: Throwable) {
        Log.e(TAG, "Cannot create TDLib client", t)
        _authState.value = AuthState.Failed("Не удалось загрузить библиотеку Telegram на этом устройстве.")
        null
    }

    private fun createClient(): Client {
        configureLogging()
        return Client.create(
            { update -> onUpdate(update) },
            { error -> Log.e(TAG, "Update handler failed", error) },
            { error -> Log.e(TAG, "Result handler failed", error) },
        )
    }

    private fun configureLogging() {
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(if (BuildConfig.DEBUG) 2 else 1))
        } catch (e: Client.ExecutionException) {
            Log.w(TAG, "Cannot set TDLib log level", e)
        }
    }

    private fun onUpdate(update: TdApi.Object) {
        if (update is TdApi.UpdateAuthorizationState) {
            onAuthorizationState(update.authorizationState)
        }
        _updates.tryEmit(update)
    }

    private fun onAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> _authState.value = AuthState.WaitPhone
            is TdApi.AuthorizationStateWaitCode -> _authState.value = AuthState.WaitCode(
                phone = state.codeInfo.phoneNumber,
                delivery = state.codeInfo.type.toDelivery(),
                resendTimeoutSec = state.codeInfo.timeout,
            )
            is TdApi.AuthorizationStateWaitEmailCode -> _authState.value =
                AuthState.WaitEmailCode(state.codeInfo.emailAddressPattern)
            is TdApi.AuthorizationStateWaitPassword -> _authState.value =
                AuthState.WaitPassword(state.passwordHint.orEmpty())
            is TdApi.AuthorizationStateReady -> _authState.value = AuthState.Ready
            is TdApi.AuthorizationStateWaitEmailAddress -> _authState.value = AuthState.Unsupported(
                "Telegram просит привязать почту к аккаунту. Сделай это в официальном приложении и войди снова."
            )
            is TdApi.AuthorizationStateWaitRegistration -> _authState.value = AuthState.Unsupported(
                "Этот номер не зарегистрирован в Telegram. Создай аккаунт в официальном приложении."
            )
            is TdApi.AuthorizationStateWaitPremiumPurchase -> _authState.value = AuthState.Unsupported(
                "Telegram требует Premium для входа с этого номера. Войди через официальное приложение."
            )
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> _authState.value =
                AuthState.Unsupported("Вход по QR-коду пока не поддерживается.")
            is TdApi.AuthorizationStateLoggingOut,
            is TdApi.AuthorizationStateClosing -> _authState.value = AuthState.Loading
            is TdApi.AuthorizationStateClosed -> restart()
            else -> Log.w(TAG, "Unknown authorization state: ${state.javaClass.simpleName}")
        }
    }

    /** После выхода TDLib закрывает клиент. Для нового входа нужен новый экземпляр. */
    private fun restart() {
        _authState.value = AuthState.Loading
        synchronized(lock) {
            client = createClientOrFail()
        }
    }

    private fun sendParameters() {
        val databaseDir = File(appContext.filesDir, "tdlib").apply { mkdirs() }
        val filesDir = File(appContext.filesDir, "tdlib-files").apply { mkdirs() }
        val parameters = TdApi.SetTdlibParameters(
            /* useTestDc = */ false,
            /* databaseDirectory = */ databaseDir.absolutePath,
            /* filesDirectory = */ filesDir.absolutePath,
            /* databaseEncryptionKey = */ ByteArray(0),
            /* useFileDatabase = */ true,
            /* useChatInfoDatabase = */ true,
            /* useMessageDatabase = */ true,
            /* useSecretChats = */ false,
            /* apiId = */ BuildConfig.TG_API_ID,
            /* apiHash = */ BuildConfig.TG_API_HASH,
            /* systemLanguageCode = */ Locale.getDefault().language.ifBlank { "en" },
            /* deviceModel = */ "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            /* systemVersion = */ "Android ${Build.VERSION.RELEASE}",
            /* applicationVersion = */ BuildConfig.VERSION_NAME,
        )
        val current = synchronized(lock) { client } ?: return
        current.send(parameters) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "SetTdlibParameters failed: ${result.code} ${result.message}")
                _authState.value = AuthState.Failed(
                    "Не удалось запустить Telegram (${result.code}: ${result.message})"
                )
            }
        }
    }

    private fun TdApi.AuthenticationCodeType?.toDelivery(): CodeDelivery = when (this) {
        is TdApi.AuthenticationCodeTypeTelegramMessage -> CodeDelivery.TelegramApp
        is TdApi.AuthenticationCodeTypeSms,
        is TdApi.AuthenticationCodeTypeSmsWord,
        is TdApi.AuthenticationCodeTypeSmsPhrase -> CodeDelivery.Sms
        is TdApi.AuthenticationCodeTypeCall,
        is TdApi.AuthenticationCodeTypeFlashCall,
        is TdApi.AuthenticationCodeTypeMissedCall -> CodeDelivery.Call
        else -> CodeDelivery.Other
    }

    private companion object {
        const val TAG = "TelegramClient"
    }
}
