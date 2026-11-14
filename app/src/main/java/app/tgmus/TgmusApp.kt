package app.tgmus

import android.app.Application
import app.tgmus.core.telegram.TelegramClient

class TgmusApp : Application() {

    /** Один клиент Telegram на всё приложение. */
    val telegram: TelegramClient by lazy { TelegramClient(this) }

    override fun onCreate() {
        super.onCreate()
        telegram.start()
    }
}
