package app.tgmus.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.tgmus.core.telegram.AuthState
import app.tgmus.core.telegram.CodeDelivery
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val step = state.step

    BackHandler(
        enabled = step is AuthState.WaitCode || step is AuthState.WaitEmailCode || step is AuthState.WaitPassword,
        onBack = viewModel::editPhone,
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (step) {
                    AuthState.Loading, AuthState.Ready -> LoadingStep()

                    AuthState.NotConfigured -> MessageStep(
                        title = "Ключи Telegram не заданы",
                        text = "В этой сборке нет api_id и api_hash. Добавь секреты TG_API_ID и TG_API_HASH " +
                            "в настройках репозитория на GitHub и собери приложение заново.",
                    )

                    AuthState.WaitPhone -> PhoneStep(
                        isSubmitting = state.isSubmitting,
                        error = state.error,
                        onChange = viewModel::clearError,
                        onSubmit = viewModel::submitPhone,
                    )

                    is AuthState.WaitCode -> CodeStep(
                        title = "Введи код",
                        text = when (step.delivery) {
                            CodeDelivery.TelegramApp -> "Код пришёл в Telegram на другом устройстве, в чат «Telegram»."
                            CodeDelivery.Sms -> "Код отправлен по SMS на +${step.phone.trimStart('+')}."
                            CodeDelivery.Call -> "Тебе позвонят на +${step.phone.trimStart('+')} и продиктуют код."
                            CodeDelivery.Other -> "Код отправлен на +${step.phone.trimStart('+')}."
                        },
                        resendTimeoutSec = step.resendTimeoutSec,
                        isSubmitting = state.isSubmitting,
                        error = state.error,
                        onChange = viewModel::clearError,
                        onSubmit = viewModel::submitCode,
                        onResend = viewModel::resendCode,
                        onBack = viewModel::editPhone,
                    )

                    is AuthState.WaitEmailCode -> CodeStep(
                        title = "Код из письма",
                        text = "Telegram отправил код на почту ${step.emailPattern}.",
                        resendTimeoutSec = null,
                        isSubmitting = state.isSubmitting,
                        error = state.error,
                        onChange = viewModel::clearError,
                        onSubmit = viewModel::submitEmailCode,
                        onResend = {},
                        onBack = viewModel::editPhone,
                    )

                    is AuthState.WaitPassword -> PasswordStep(
                        hint = step.hint,
                        isSubmitting = state.isSubmitting,
                        error = state.error,
                        onChange = viewModel::clearError,
                        onSubmit = viewModel::submitPassword,
                        onBack = viewModel::editPhone,
                    )

                    is AuthState.Unsupported -> MessageStep(
                        title = "Так войти пока нельзя",
                        text = step.reason,
                        actionLabel = "Ввести другой номер",
                        onAction = viewModel::editPhone,
                    )

                    is AuthState.Failed -> MessageStep(
                        title = "Telegram не запустился",
                        text = step.message,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, text: String) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("♪", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimary)
    }
    Spacer(Modifier.height(24.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun LoadingStep() {
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text("Подключаемся к Telegram…", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MessageStep(
    title: String,
    text: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Header(title, text)
    if (actionLabel != null) {
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun PhoneStep(
    isSubmitting: Boolean,
    error: String?,
    onChange: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var phone by rememberSaveable { mutableStateOf("+7") }
    Header(
        title = "Вход в Telegram",
        text = "Музыка берётся из твоих чатов и каналов, поэтому нужен вход в аккаунт. " +
            "Пароль и код уходят только в Telegram.",
    )
    AuthField(
        value = phone,
        onValueChange = { value ->
            phone = value.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' }.take(20)
            onChange()
        },
        label = "Номер телефона",
        keyboardType = KeyboardType.Phone,
        error = error,
        enabled = !isSubmitting,
        onDone = { onSubmit(phone) },
    )
    SubmitButton("Получить код", isSubmitting) { onSubmit(phone) }
}

@Composable
private fun CodeStep(
    title: String,
    text: String,
    resendTimeoutSec: Int?,
    isSubmitting: Boolean,
    error: String?,
    onChange: () -> Unit,
    onSubmit: (String) -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    Header(title, text)
    AuthField(
        value = code,
        onValueChange = { value ->
            code = value.filter { it.isLetterOrDigit() }.take(12)
            onChange()
        },
        label = "Код",
        keyboardType = KeyboardType.Number,
        error = error,
        enabled = !isSubmitting,
        onDone = { onSubmit(code) },
    )
    SubmitButton("Продолжить", isSubmitting) { onSubmit(code) }
    Spacer(Modifier.height(4.dp))
    if (resendTimeoutSec != null) {
        ResendButton(resendTimeoutSec, enabled = !isSubmitting, onResend = onResend)
    }
    TextButton(onClick = onBack, enabled = !isSubmitting) { Text("Изменить номер") }
}

@Composable
private fun ResendButton(timeoutSec: Int, enabled: Boolean, onResend: () -> Unit) {
    var secondsLeft by remember(timeoutSec) { mutableIntStateOf(timeoutSec.coerceAtLeast(0)) }
    LaunchedEffect(timeoutSec) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }
    TextButton(onClick = onResend, enabled = enabled && secondsLeft == 0) {
        Text(
            if (secondsLeft > 0) "Отправить код ещё раз через $secondsLeft сек"
            else "Отправить код ещё раз"
        )
    }
}

@Composable
private fun PasswordStep(
    hint: String,
    isSubmitting: Boolean,
    error: String?,
    onChange: () -> Unit,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
) {
    // Пароль не сохраняем через rememberSaveable, чтобы он не попал в сохранённое состояние.
    var password by remember { mutableStateOf("") }
    Header(
        title = "Облачный пароль",
        text = if (hint.isBlank()) "У аккаунта включена двухэтапная проверка. Введи пароль."
        else "У аккаунта включена двухэтапная проверка. Подсказка: $hint",
    )
    AuthField(
        value = password,
        onValueChange = { value ->
            password = value
            onChange()
        },
        label = "Пароль",
        keyboardType = KeyboardType.Password,
        visualTransformation = PasswordVisualTransformation(),
        error = error,
        enabled = !isSubmitting,
        onDone = { onSubmit(password) },
    )
    SubmitButton("Войти", isSubmitting) { onSubmit(password) }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onBack, enabled = !isSubmitting) { Text("Изменить номер") }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    error: String?,
    enabled: Boolean,
    onDone: () -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SubmitButton(label: String, isSubmitting: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isSubmitting,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}
