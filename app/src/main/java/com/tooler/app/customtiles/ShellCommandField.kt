package com.tooler.app.customtiles

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Light shell syntax highlighting for the editor's command field, done as a [VisualTransformation]
 * so the stored value stays plain text. Colors map onto this app's Nord-tinted Material roles.
 * Colors `#` comments, quoted strings, `foo`/`--flag` tokens, and `&&`/`||`/`|`/`;`/redirections.
 */
class ShellHighlightingTransformation(
    private val commentColor: Color,
    private val stringColor: Color,
    private val flagColor: Color,
    private val operatorColor: Color,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        var i = 0
        val n = text.text.length
        while (i < n) {
            val c = text.text[i]
            when {
                c == '#' && (i == 0 || text.text[i - 1].isWhitespace()) -> {
                    var end = i
                    while (end < n && text.text[end] != '\n') end++
                    builder.addStyle(SpanStyle(color = commentColor), i, end)
                    i = end
                }
                c == '\'' -> {
                    var end = i + 1
                    while (end < n && text.text[end] != '\'') end++
                    if (end < n) end++
                    builder.addStyle(SpanStyle(color = stringColor), i, end)
                    i = end
                }
                c == '"' || c == '`' -> {
                    val quote = c
                    var end = i + 1
                    var escaped = false
                    while (end < n) {
                        val ch = text.text[end]
                        if (quote == '"' && ch == '\\' && !escaped) {
                            escaped = true
                        } else {
                            escaped = false
                            if (ch == quote) {
                                end++
                                break
                            }
                        }
                        end++
                    }
                    builder.addStyle(SpanStyle(color = stringColor), i, end)
                    i = end
                }
                c.isWhitespace() -> i++
                c == '&' || c == '|' || c == '<' || c == '>' || c == ';' -> {
                    var end = i + 1
                    while (end < n &&
                        (text.text[end] == '&' || text.text[end] == '|' ||
                            text.text[end] == '<' || text.text[end] == '>' ||
                            text.text[end] == ';')
                    ) end++
                    builder.addStyle(SpanStyle(color = operatorColor), i, end)
                    i = end
                }
                else -> {
                    var end = i
                    while (end < n && !text.text[end].isWhitespace() &&
                        text.text[end] != '\'' && text.text[end] != '"' && text.text[end] != '`' &&
                        text.text[end] != '&' && text.text[end] != '|' &&
                        text.text[end] != '<' && text.text[end] != '>' && text.text[end] != ';'
                    ) end++
                    if (end > i && text.text.startsWith("-", i)) {
                        builder.addStyle(SpanStyle(color = flagColor), i, end)
                    }
                    i = end
                }
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/** Wraps [OutlinedTextField] with shell syntax highlighting, keeping the app's rounded corners and
 *  the `TextFieldValue` state that sidesteps the Compose cursor bug. */
@Composable
fun ShellCommandField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val transformation = remember(colorScheme) {
        ShellHighlightingTransformation(
            commentColor = colorScheme.outline,
            stringColor = colorScheme.tertiary,
            flagColor = colorScheme.primary,
            operatorColor = colorScheme.secondary
        )
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        shape = RoundedCornerShape(12.dp),
        maxLines = 4,
        textStyle = LocalTextStyle.current.copy(color = colorScheme.onSurfaceVariant),
        visualTransformation = transformation,
        modifier = modifier.fillMaxWidth()
    )
}