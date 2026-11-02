package app.exhale.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.exhale.domain.TaperType
import app.exhale.domain.UserSettings
import app.exhale.ui.components.Eyebrow
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.Pill
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExType
import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * Shared form for onboarding ([onboarding] = true) and settings.
 * Onboarding starts the plan today; settings keep the original plan start.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    current: UserSettings,
    onboarding: Boolean,
    onSave: (UserSettings) -> Unit,
    onDemo: () -> Unit,
    onErase: () -> Unit,
    onClose: (() -> Unit)?,
) {
    var baseline by rememberSaveable { mutableStateOf(current.baselinePerDay.toString()) }
    var perPack by rememberSaveable { mutableStateOf(current.cigsPerPack.toString()) }
    var price by rememberSaveable { mutableStateOf("%.2f".format(current.packPriceMinor / 100.0)) }
    var currency by rememberSaveable { mutableStateOf(current.currencyCode) }
    var allowance by rememberSaveable { mutableStateOf(if (onboarding) "" else current.startAllowance.toString()) }
    var taper by rememberSaveable { mutableStateOf(current.taper) }
    var goalName by rememberSaveable { mutableStateOf(current.goalName) }
    var goal by rememberSaveable { mutableStateOf((current.goalMinor / 100).toString()) }
    var confirmErase by rememberSaveable { mutableStateOf(false) }

    val baselineN = baseline.toIntOrNull()
    val perPackN = perPack.toIntOrNull()
    val priceN = price.replace(',', '.').toDoubleOrNull()
    val allowanceN = allowance.toIntOrNull() ?: baselineN?.let { (it - 2).coerceAtLeast(0) }
    val goalN = goal.replace(',', '.').toDoubleOrNull()
    val valid = baselineN != null && baselineN in 1..100 && perPackN != null && perPackN in 1..100 &&
        priceN != null && priceN >= 0 && currency.length == 3 && allowanceN != null && goalN != null && goalN >= 0

    Column(
        Modifier
            .fillMaxSize()
            .then(if (onboarding) Modifier.safeDrawingPadding() else Modifier)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onboarding) {
            Eyebrow("Exhale")
            Text("Burn less.\nExhale more.", style = ExType.hero, color = Ex.Ink)
            Text(
                "Track every cigarette in one gesture, cut down at your own pace, and ride out cravings. Everything stays on this phone.",
                style = ExType.body, color = Ex.Mute,
            )
        } else {
            Row {
                Text("Settings", style = ExType.title, color = Ex.Ink, modifier = Modifier.weight(1f))
                if (onClose != null) {
                    Text("CLOSE", style = ExType.eyebrow, color = Ex.Mute, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(8.dp))
                }
            }
        }

        Eyebrow("Your habit")
        Field("Cigarettes a day, before Exhale", baseline, { baseline = it }, KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Per pack", perPack, { perPack = it }, KeyboardType.Number, Modifier.weight(1f))
            Field("Pack price", price, { price = it }, KeyboardType.Decimal, Modifier.weight(1f))
            Field("Currency", currency, { currency = it.uppercase().take(3) }, KeyboardType.Text, Modifier.weight(0.8f))
        }

        Eyebrow("Plan")
        Field(
            "Starting allowance per day",
            allowance, { allowance = it }, KeyboardType.Number,
            placeholder = baselineN?.let { "${(it - 2).coerceAtLeast(0)} (2 under your usual)" },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TaperType.entries.forEach { t -> Pill(t.label, taper == t, { taper = t }) }
        }
        Text(taper.description, style = ExType.small, color = Ex.Mute)

        Eyebrow("Goal")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Saving for", goalName, { goalName = it }, KeyboardType.Text, Modifier.weight(1.4f))
            Field("Amount", goal, { goal = it }, KeyboardType.Decimal, Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))
        ExButton(
            if (onboarding) "Start" else "Save",
            {
                if (valid) onSave(
                    current.copy(
                        onboarded = true,
                        baselinePerDay = baselineN!!,
                        cigsPerPack = perPackN!!,
                        packPriceMinor = (priceN!! * 100).roundToLong(),
                        currencyCode = currency,
                        startAllowance = allowanceN!!,
                        taper = taper,
                        planStart = if (onboarding) LocalDate.now() else current.planStart,
                        goalName = goalName.ifBlank { "Something nice" },
                        goalMinor = (goalN!! * 100).roundToLong(),
                    )
                )
            },
            Modifier.fillMaxWidth(),
            color = if (valid) Ex.Oxy else Ex.Line,
        )
        if (!valid) Text("Fill in the numbers above. Currency is a 3-letter code like USD or EUR.", style = ExType.small, color = Ex.Mute)

        Eyebrow("Prototype")
        ExButton(
            if (onboarding) "Try it with demo data" else "Replace with demo data",
            onDemo, Modifier.fillMaxWidth(), color = Ex.Sky, filled = false,
        )
        if (!onboarding) {
            ExButton(
                if (confirmErase) "Tap again to erase everything" else "Erase all data",
                { if (confirmErase) { onErase(); confirmErase = false } else confirmErase = true },
                Modifier.fillMaxWidth(), color = Ex.Flare, filled = confirmErase,
            )
        }
        Text("Exhale stores everything on this device only. No account, no cloud.", style = ExType.small, color = Ex.Mute)
    }
}

@Composable
private fun Field(
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
        placeholder = placeholder?.let { { Text(it, style = ExType.small) } },
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
