package app.tgmus.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.tgmus.TgmusApp
import app.tgmus.core.telegram.TelegramClient
import app.tgmus.core.telegram.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val userName: String = "",
    val userPhone: String = "",
    val isLoggingOut: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(private val telegram: TelegramClient) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val me = telegram.getMe()
                _state.update {
                    it.copy(
                        userName = listOf(me.firstName, me.lastName)
                            .filter { part -> !part.isNullOrBlank() }
                            .joinToString(" "),
                        userPhone = me.phoneNumber.orEmpty(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }

    fun logOut() {
        if (_state.value.isLoggingOut) return
        _state.update { it.copy(isLoggingOut = true, error = null) }
        viewModelScope.launch {
            try {
                telegram.logOut()
                // Дальше TDLib сам сменит состояние входа, и откроется экран авторизации.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoggingOut = false, error = e.toUserMessage()) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel((this[APPLICATION_KEY] as TgmusApp).telegram)
            }
        }
    }
}
