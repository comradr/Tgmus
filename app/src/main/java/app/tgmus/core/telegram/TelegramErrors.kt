package app.tgmus.core.telegram

/** Переводит ошибку Telegram в понятный текст для пользователя. */
fun Throwable.toUserMessage(): String {
    if (this is kotlinx.coroutines.TimeoutCancellationException) {
        return "Нет связи с Telegram. Проверь интернет и попробуй ещё раз."
    }
    val error = this as? TelegramException
        ?: return "Что-то пошло не так. Проверь интернет и попробуй ещё раз."
    val raw = error.rawMessage
    return when {
        raw.contains("PHONE_NUMBER_INVALID") -> "Неверный номер. Введи его в международном формате, например +79001234567."
        raw.contains("PHONE_NUMBER_BANNED") -> "Этот номер заблокирован в Telegram."
        raw.contains("PHONE_NUMBER_FLOOD") || raw.contains("PHONE_PASSWORD_FLOOD") ->
            "Слишком много попыток входа. Попробуй позже."
        raw.contains("PHONE_CODE_INVALID") || raw.contains("EMAIL_CODE_INVALID") -> "Неверный код."
        raw.contains("PHONE_CODE_EXPIRED") || raw.contains("EMAIL_CODE_EXPIRED") ->
            "Код устарел. Запроси новый."
        raw.contains("PHONE_CODE_EMPTY") -> "Введи код."
        raw.contains("PASSWORD_HASH_INVALID") -> "Неверный пароль."
        raw.contains("API_ID_INVALID") -> "Неверные ключи api_id / api_hash в сборке."
        error.code == 429 || raw.contains("FLOOD_WAIT") || raw.contains("Too Many Requests") -> {
            val seconds = Regex("(\\d+)").find(raw.substringAfter("after", raw))?.value?.toLongOrNull()
            if (seconds != null) "Слишком много попыток. Подожди ${formatWait(seconds)}."
            else "Слишком много попыток. Попробуй позже."
        }
        error.code == 500 && raw == "CLIENT_NOT_STARTED" -> "Telegram ещё запускается. Попробуй через пару секунд."
        else -> "Ошибка Telegram: $raw"
    }
}

private fun formatWait(seconds: Long): String = when {
    seconds < 60 -> "$seconds сек"
    seconds < 3600 -> "${(seconds + 59) / 60} мин"
    else -> "${(seconds + 3599) / 3600} ч"
}
