package app.exhale.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.exhale.domain.Trigger
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExType

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Ex.Mute) {
    Text(text.uppercase(), style = ExType.eyebrow, color = color, modifier = modifier)
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = Ex.Oxy,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) selectedColor else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selected) selectedColor else Ex.Line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ExType.small, color = if (selected) Ex.Void else Ex.Ink)
    }
}

@Composable
fun TriggerRow(selected: Trigger?, onPick: (Trigger) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Trigger.entries.forEach { t -> Pill(t.label, selected == t, { onPick(t) }) }
    }
}

/** Big pill button. [filled] = primary action. */
@Composable
fun ExButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ex.Oxy,
    filled: Boolean = true,
    contentColor: Color = if (filled) Ex.Void else color,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .then(if (filled) Modifier.background(color) else Modifier.border(BorderStroke(1.5.dp, color), shape))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ExType.bodyStrong, color = contentColor, textAlign = TextAlign.Center)
    }
}

@Composable
fun SegmentRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .border(1.dp, Ex.Line, RoundedCornerShape(50))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == selected) Ex.Ink else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .heightIn(min = 34.dp)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = ExType.small, color = if (i == selected) Ex.Void else Ex.Mute)
            }
        }
    }
}
