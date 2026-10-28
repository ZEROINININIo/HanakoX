package `fun`.kirari.hanako.feature.history.application

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.HistoryRecordMetadata
import `fun`.kirari.hanako.core.data.QuestionCardArtifact
import `fun`.kirari.hanako.core.data.SettingsRepository
import `fun`.kirari.hanako.core.data.historyMetadataFor
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.latestAnswerText
import `fun`.kirari.hanako.core.ui.richtext.MarkdownLatexText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.text.DateFormat
import java.util.Date

class QuestionCardExporter(
    private val context: Context,
    private val repository: SettingsRepository
) {
    fun deleteArtifacts(historyId: String) {
        File(context.filesDir, "history_cards/$historyId").deleteRecursively()
    }

    fun deleteAllArtifacts() {
        File(context.filesDir, "history_cards").deleteRecursively()
    }

    suspend fun export(result: ProcessingResult): Result<QuestionCardArtifact> = runCatching {
        val metadata = repository.read().historyMetadataFor(result)
        val artifact = withContext(Dispatchers.Main.immediate) {
            render(result, metadata)
        }
        repository.updateHistoryMetadata(result.id) { it.copy(questionCard = artifact) }
        artifact
    }

    private fun render(result: ProcessingResult, metadata: HistoryRecordMetadata): QuestionCardArtifact {
        val widthPx = (context.resources.displayMetrics.density * 420f).toInt().coerceAtLeast(1080)
        val composeView = ComposeView(context)
        composeView.setContent {
            MaterialTheme {
                QuestionCardContent(
                    title = when (val title = metadata.title) {
                        `fun`.kirari.hanako.core.data.HistoryTitle.Assistant -> result.assistantName
                        is `fun`.kirari.hanako.core.data.HistoryTitle.Custom -> title.text.ifBlank { result.assistantName }
                        is `fun`.kirari.hanako.core.data.HistoryTitle.AiSummary -> title.text.ifBlank { result.assistantName }
                    },
                    assistantName = result.assistantName,
                    answer = result.latestAnswerText(),
                    question = result.extractedText,
                    createdAtMillis = result.createdAtMillis
                )
            }
        }
        composeView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        val heightPx = composeView.measuredHeight.coerceAtLeast(1)
        require(heightPx < 24_000) { "题目卡片过长，请分段导出" }
        composeView.layout(0, 0, widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        val directory = File(context.filesDir, "history_cards/${result.id}").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return QuestionCardArtifact(
            id = file.nameWithoutExtension,
            contentRevision = metadata.contentRevision,
            path = file.absolutePath,
            widthPx = widthPx,
            heightPx = heightPx,
            themeMode = "current-material-you",
            createdAtMillis = System.currentTimeMillis()
        )
    }
}

@Composable
private fun QuestionCardContent(
    title: String,
    assistantName: String,
    answer: String,
    question: String,
    createdAtMillis: Long
) {
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(28.dp)
    ) {
        androidx.compose.material3.Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        androidx.compose.material3.Text(assistantName + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(createdAtMillis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (question.isNotBlank()) {
            androidx.compose.material3.Text("题目", style = MaterialTheme.typography.titleMedium, modifier = androidx.compose.ui.Modifier.padding(top = 20.dp))
            MarkdownLatexText(question, modifier = androidx.compose.ui.Modifier.padding(top = 8.dp))
        }
        androidx.compose.material3.Text("答案", style = MaterialTheme.typography.titleMedium, modifier = androidx.compose.ui.Modifier.padding(top = 20.dp))
        MarkdownLatexText(answer.ifBlank { "暂无答案" }, modifier = androidx.compose.ui.Modifier.padding(top = 8.dp))
    }
}
