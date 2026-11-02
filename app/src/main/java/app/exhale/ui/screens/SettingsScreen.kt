package app.exhale.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.exhale.domain.TaperType
import app.exhale.domain.ThemeMode
import app.exhale.domain.UserSettings
import app.exhale.ui.components.ButtonStyle
import app.exhale.ui.components.ExButton
import app.exhale.ui.components.ExCard
import app.exhale.ui.components.ExIconButton
import app.exhale.ui.components.ExIcons
import app.exhale.ui.components.ExScreen
import app.exhale.ui.components.ExTextField
import app.exhale.ui.components.HeaderLabel
import app.exhale.ui.components.ListRow
import app.exhale.ui.components.OptionCard
import app.exhale.ui.components.Overline
import app.exhale.ui.components.SegmentedControl
import app.exhale.ui.theme.Ex
import app.exhale.ui.theme.ExSize
import app.exhale.ui.theme.ExSpace
import app.exhale.ui.theme.ExType
import java.util.Currency
import kotlin.math.roundToLong

/** Edit everything set during onboarding. Keeps the original plan start date. Theme applies instantly. */
@Composable
fun SettingsScreen(
    current: UserSettings,
    onSave: (UserSettings) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onDemo: () -> Unit,
    onErase: () -> Unit,
    onDesignSystem: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Ex.colors
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

    ExScreen(
        bottomClearance = ExSpace.xl,
        header = {
            ExIconButton(ExIcons.Back, "Back", onClose)
            HeaderLabel("Settings")
            Spacer(Modifier.size(ExSize.iconButton))
        },
    ) {
        Overline("Appearance")
        SegmentedControl(ThemeMode.entries.map { it.label }, current.theme.ordinal, { onTheme(ThemeMode.entries[it]) })

        Overline("Your habit", Modifier.paddingTop())
        ExTextField("Cigarettes a day, before Exhale", baseline, { baseline = it }, type = KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
            ExTextField("Pack price", price, { price = it }, Modifier.weight(1f), type = KeyboardType.Decimal)
            ExTextField("Per pack", perPack, { perPack = it }, Modifier.weight(0.8f), type = KeyboardType.Number)
            ExTextField("Currency", currency, { currency = it.uppercase().take(3) }, Modifier.weight(0.8f))
        }

        Overline("Plan", Modifier.paddingTop())
        ExTextField("Starting allowance per day", allowance, { allowance = it }, type = KeyboardType.Number)
        TaperType.entries.forEach { t -> OptionCard(t.label, t.description, taper == t, { taper = t }) }

        Overline("Goal", Modifier.paddingTop())
        Row(horizontalArrangement = Arrangement.spacedBy(ExSpace.sm)) {
            ExTextField("Saving for", goalName, { goalName = it }, Modifier.weight(1.4f))
            ExTextField("Amount", goal, { goal = it }, Modifier.weight(1f), type = KeyboardType.Decimal)
        }

        ExButton(
            "Save changes",
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
            Modifier.fillMaxWidth().paddingTop(),
            enabled = valid,
        )
        if (!valid) Text("Check the numbers above. Currency is a 3-letter code like USD, EUR or KZT.", style = ExType.caption, color = c.inkSoft)

        Overline("Prototype tools", Modifier.paddingTop())
        ExCard(padding = ExSpace.sm) {
            ListRow("Design system", onDesignSystem)
        }
        Text(
            "Demo data replaces your logs with 30 days of made-up history, so every screen has something to show.",
            style = ExType.caption, color = c.inkSoft,
        )
        ExButton("Replace my data with demo data", onDemo, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
        ExButton(
            if (confirmErase) "Tap again to erase everything" else "Erase all data",
            { if (confirmErase) { onErase(); confirmErase = false } else confirmErase = true },
            Modifier.fillMaxWidth(),
            style = if (confirmErase) ButtonStyle.Accent else ButtonStyle.Ghost,
            accent = c.flare,
        )
        Text("Exhale stores everything on this device only. No account, no cloud.", style = ExType.caption, color = c.inkFaint)
    }
}

private fun Modifier.paddingTop() = this.padding(top = ExSpace.xs)
