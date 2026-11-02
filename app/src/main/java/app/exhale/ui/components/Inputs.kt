package app.exhale.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExType

@Composable
fun ExField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    type: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = ExType.small) },
        placeholder = placeholder?.let { { Text(it, style = ExType.body) } },
        singleLine = true,
        textStyle = ExType.body.copy(color = Ex.Ink),
        keyboardOptions = KeyboardOptions(keyboardType = type),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ex.Oxy,
            unfocusedBorderColor = Ex.Line,
            focusedLabelColor = Ex.Oxy,
            unfocusedLabelColor = Ex.Mute,
            cursorColor = Ex.Oxy,
        ),
        modifier = modifier,
    )
}

/** Big number with − / + buttons. For counts the user adjusts by a few steps. */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = false) { stateDescription = "$value $unit" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", "Decrease", enabled = value > range.first) { onChange(value - 1) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = ExType.hero.copy(fontSize = 104.sp), color = Ex.Ink)
            Text(unit, style = ExType.small, color = Ex.Mute)
        }
        StepButton("+", "Increase", enabled = value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .border(1.5.dp, if (enabled) Ex.Mute else Ex.Line, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = ExType.title, color = if (enabled) Ex.Ink else Ex.Line)
    }
}
