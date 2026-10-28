package `fun`.kirari.hanako.feature.settings.ui.automation

import android.app.Service
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.AutomationSettings
import `fun`.kirari.hanako.core.ui.components.SectionCard

@Composable
fun StaticVibrationSettingsScreen(
    automationSettings: AutomationSettings,
    onUpdateSettings: ((AutomationSettings) -> AutomationSettings) -> Unit
) {
    val context = LocalContext.current
    var intraLetterGapInput by remember(automationSettings.staticIntraLetterGapMs) {
        mutableStateOf(automationSettings.staticIntraLetterGapMs.toString())
    }
    var interLetterGapInput by remember(automationSettings.staticInterLetterGapMs) {
        mutableStateOf(automationSettings.staticInterLetterGapMs.toString())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "静态模式振动") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "使用振动次数表示答案对应的选项。隐藏悬浮球动画和弹窗。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VibrationDurationField(
                        label = "单个字母内部间隔",
                        value = intraLetterGapInput,
                        onValueChange = { value ->
                            val digits = value.filter(Char::isDigit)
                            intraLetterGapInput = digits
                            digits.toIntOrNull()?.takeIf { it >= 0 }?.let { gap ->
                                onUpdateSettings { current ->
                                    current.copy(staticIntraLetterGapMs = gap)
                                }
                            }
                        }
                    )
                    VibrationDurationField(
                        label = "字母之间间隔",
                        value = interLetterGapInput,
                        onValueChange = { value ->
                            val digits = value.filter(Char::isDigit)
                            interLetterGapInput = digits
                            digits.toIntOrNull()?.takeIf { it >= 0 }?.let { gap ->
                                onUpdateSettings { current ->
                                    current.copy(staticInterLetterGapMs = gap)
                                }
                            }
                        }
                    )
                    OutlinedButton(
                        onClick = {
                            playPreviewVibration(context, automationSettings, "ABCD")
                            Toast.makeText(context, "正在试听 ABCD", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("试听 ABCD")
                    }
                }
            }
        }
    }
}

@Composable
private fun VibrationDurationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("毫秒") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

private fun playPreviewVibration(
    context: android.content.Context,
    settings: AutomationSettings,
    text: String
) {
    val pattern = mutableListOf<Long>()
    val amplitudes = mutableListOf<Int>()
    pattern += 0L
    amplitudes += 0

    text.uppercase().forEachIndexed { index, ch ->
        val count = ch.code - 'A'.code + 1
        if (count <= 0) return@forEachIndexed
        repeat(count) { pulseIndex ->
            pattern += 30L
            amplitudes += VibrationEffect.DEFAULT_AMPLITUDE
            if (pulseIndex != count - 1) {
                pattern += settings.staticIntraLetterGapMs.toLong()
                amplitudes += 0
            }
        }
        if (index != text.lastIndex) {
            pattern += settings.staticInterLetterGapMs.toLong()
            amplitudes += 0
        }
    }

    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern.toLongArray(), amplitudes.toIntArray(), -1))
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Service.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern.toLongArray(), amplitudes.toIntArray(), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern.toLongArray(), -1)
                }
            }
        }
    }
}
