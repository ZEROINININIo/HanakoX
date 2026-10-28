package `fun`.kirari.hanako.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.delay

@Composable
fun DraftOutlinedTextField(
    fieldKey: String,
    value: String,
    onCommit: (String) -> Unit,
    label: String,
    minLines: Int = 1,
    modifier: Modifier = Modifier,
    singleLine: Boolean = minLines == 1,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    readOnly: Boolean = false
) {
    var textFieldValue by rememberSaveable(fieldKey, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    var isFocused by remember(fieldKey) { mutableStateOf(false) }

    LaunchedEffect(fieldKey, value, isFocused) {
        if (!isFocused && value != textFieldValue.text) {
            textFieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    LaunchedEffect(fieldKey, textFieldValue.text) {
        delay(250)
        if (textFieldValue.text != value) {
            onCommit(textFieldValue.text)
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = {
            if (!readOnly) {
                textFieldValue = it
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (!focusState.isFocused && textFieldValue.text != value) {
                    onCommit(textFieldValue.text)
                }
            },
        minLines = minLines,
        singleLine = singleLine,
        label = { Text(label) },
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        readOnly = readOnly,
        shape = HanakoTextFieldShape
    )
}
