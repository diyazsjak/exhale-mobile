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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import app.exhale.ui.components.ExField
import app.exhale.ui.components.Pill
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExType
import java.util.Currency
import kotlin.math.roundToLong

/** Edit everything set during onboarding. Keeps the original plan start date. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    current: UserSettings,
    onSave: (UserSettings) -> Unit,
    onDemo: () -> Unit,
    onErase: () -> Unit,
    onClose: () -> Unit,
) {
    var baseline by rememberSaveable { mutableStateOf(current.baselinePerDay.toString()) }
    var perPack by rememberSaveable { mutableStateOf(current.cigsPerPack.toString()) }
    var price by rememberSaveable { mutableStateOf("%.2f".format(current.packPriceMinor / 100.0)) }
    var currency by rememberSaveable { mutableStateOf(current.currencyCode) }
    var allowance by rememberSaveable { mutableStateOf(current.startAllowance.toString()) }
    var taper by rememberSaveable { mutableStateOf(current.taper) }
    var goalName by rememberSaveable { mutableStateOf(current.goalName) }
    var goal by rememberSaveable { mutableStateOf((current.goalMinor / 100).toString()) }
    var confirmErase by rememberSaveable { mutableStateOf(false) }

    val baselineN = baseline.toIntOrNull()?.takeIf { it in 1..100 }
    val perPackN = perPack.toIntOrNull()?.takeIf { it in 1..100 }
    val priceN = price.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
    val currencyOk = currency.length == 3 && runCatching { Currency.getInstance(currency) }.isSuccess
    val allowanceN = allowance.toIntOrNull()?.takeIf { it >= 0 }
    val goalN = goal.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
    val valid = baselineN != null && perPackN != null && priceN != null && currencyOk && allowanceN != null && goalN != null

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row {
            Text("Settings", style = ExType.title, color = Ex.Ink, modifier = Modifier.weight(1f))
            Text(
                "CLOSE", style = ExType.eyebrow, color = Ex.Mute,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(8.dp),
            )
        }

        Eyebrow("Your habit")
        ExField("Cigarettes a day, before Exhale", baseline, { baseline = it }, KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExField("Per pack", perPack, { perPack = it }, KeyboardType.Number, Modifier.weight(1f))
            ExField("Pack price", price, { price = it }, KeyboardType.Decimal, Modifier.weight(1f))
            ExField("Currency", currency, { currency = it.uppercase().take(3) }, KeyboardType.Text, Modifier.weight(0.8f))
        }

        Eyebrow("Plan")
        ExField("Starting allowance per day", allowance, { allowance = it }, KeyboardType.Number)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TaperType.entries.forEach { t -> Pill(t.label, taper == t, { taper = t }) }
        }
        Text(taper.description, style = ExType.small, color = Ex.Mute)

        Eyebrow("Goal")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExField("Saving for", goalName, { goalName = it }, KeyboardType.Text, Modifier.weight(1.4f))
            ExField("Amount", goal, { goal = it }, KeyboardType.Decimal, Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))
        ExButton(
            "Save",
            {
                onSave(
                    current.copy(
                        baselinePerDay = baselineN!!,
                        cigsPerPack = perPackN!!,
                        packPriceMinor = (priceN!! * 100).roundToLong(),
                        currencyCode = currency,
                        startAllowance = allowanceN!!,
                        taper = taper,
                        goalName = goalName.ifBlank { "Something nice" },
                        goalMinor = (goalN!! * 100).roundToLong(),
                    )
                )
            },
            Modifier.fillMaxWidth(),
            enabled = valid,
        )
        if (!valid) Text("Check the numbers above. Currency is a 3-letter code like USD, EUR or KZT.", style = ExType.small, color = Ex.Mute)

        Eyebrow("Prototype tools")
        Text(
            "Demo data replaces your logs with 30 days of made-up history, so every screen has something to show.",
            style = ExType.small, color = Ex.Mute,
        )
        ExButton("Replace my data with demo data", onDemo, Modifier.fillMaxWidth(), color = Ex.Sky, filled = false)
        ExButton(
            if (confirmErase) "Tap again to erase everything" else "Erase all data",
            { if (confirmErase) { onErase(); confirmErase = false } else confirmErase = true },
            Modifier.fillMaxWidth(), color = Ex.Flare, filled = confirmErase,
        )
        Text("Exhale stores everything on this device only. No account, no cloud.", style = ExType.small, color = Ex.Mute)
    }
}
