package com.conreo.couchytv.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.conreo.couchytv.R
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.conreo.couchytv.data.NetStatus
import com.conreo.couchytv.data.WEATHER_DISPLAY_ICON
import com.conreo.couchytv.data.WEATHER_DISPLAY_LOWHIGH
import com.conreo.couchytv.data.WeatherData
import com.conreo.couchytv.data.WeatherIconKind
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

private fun iconFor(kind: WeatherIconKind): ImageVector = when (kind) {
    WeatherIconKind.Clear -> AppIcons.WeatherClear
    WeatherIconKind.PartlyCloudy -> AppIcons.WeatherPartlyCloudy
    WeatherIconKind.Cloudy -> AppIcons.WeatherCloudy
    WeatherIconKind.Fog -> AppIcons.WeatherFog
    WeatherIconKind.Rain -> AppIcons.WeatherRain
    WeatherIconKind.Snow -> AppIcons.WeatherSnow
    WeatherIconKind.Storm -> AppIcons.WeatherStorm
}

@Composable
private fun labelFor(kind: WeatherIconKind): String = stringResource(
    when (kind) {
        WeatherIconKind.Clear -> R.string.weather_kind_clear
        WeatherIconKind.PartlyCloudy -> R.string.weather_kind_partly_cloudy
        WeatherIconKind.Cloudy -> R.string.weather_kind_cloudy
        WeatherIconKind.Fog -> R.string.weather_kind_fog
        WeatherIconKind.Rain -> R.string.weather_kind_rain
        WeatherIconKind.Snow -> R.string.weather_kind_snow
        WeatherIconKind.Storm -> R.string.weather_kind_storm
    },
)

@Composable
fun StatusBar(
    net: NetStatus,
    time: String,
    date: String,
    showVpn: Boolean,
    glass: Boolean,
    weather: WeatherData?,
    weatherDisplay: Int,
    onVpnClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onWeatherClick: () -> Unit,
    onWeatherLongClick: () -> Unit,
) {
    // Optional glass panel behind the icon cluster — same look as the dock
    // panel, corners follow the roundness setting.
    val cluster = if (glass) {
        Modifier
            .clip(SmoothCornerShape(LocalCornerRadius.current))
            .background(Color(0xB3121418))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    } else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = cluster,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Order: weather · VPN · network · settings · date · clock (clock far right)
        if (weather != null) {
            WeatherChip(weather, weatherDisplay, onWeatherClick, onWeatherLongClick)
        }
        if (showVpn) {
            StatusIcon(
                icon = AppIcons.Vpn,
                active = net.vpn,
                contentDescription = stringResource(R.string.cd_vpn),
                onClick = onVpnClick,
            )
        }
        StatusIcon(
            icon = when {
                net.ethernet -> AppIcons.Ethernet
                net.wifi -> AppIcons.Wifi
                else -> AppIcons.WifiOff
            },
            active = net.connected,
            contentDescription = stringResource(R.string.cd_network),
            onClick = onNetworkClick,
        )
        StatusIcon(
            icon = AppIcons.Gear,
            active = true,
            contentDescription = stringResource(R.string.cd_settings),
            onClick = onSettingsClick,
        )
        if (date.isNotEmpty()) {
            Text(
                text = date,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Text(
            // Tabular figures: digits keep a fixed width so the clock never jitters.
            text = time,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = Color.White,
            modifier = Modifier.padding(start = 10.dp),
        )
      }
    }
}

@Composable
private fun WeatherChip(weather: WeatherData, display: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    // Same D-pad long-press detection as AppCard: a remote's OK button only
    // reports onLongClick to the pointer/mouse path, so a held-down OK is
    // timed by hand and the click that follows its release is suppressed.
    var downAt by remember { mutableLongStateOf(0L) }
    var lastKeyDown by remember { mutableLongStateOf(0L) }
    var longPressAt by remember { mutableLongStateOf(0L) }
    fun fireLongPress() {
        longPressAt = SystemClock.uptimeMillis()
        onLongClick()
    }
    Surface(
        onClick = { if (SystemClock.uptimeMillis() - longPressAt > 1_000L) onClick() },
        onLongClick = { fireLongPress() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        modifier = Modifier.onPreviewKeyEvent { e ->
            val select = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            if (!select || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val now = SystemClock.uptimeMillis()
            if (lastKeyDown == 0L || now - lastKeyDown > 300) {
                downAt = now
                lastKeyDown = now
                return@onPreviewKeyEvent false
            }
            lastKeyDown = now
            if (now - longPressAt > 1_000L && now - downAt >= 450) fireLongPress()
            true
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = iconFor(weather.kind),
                contentDescription = stringResource(R.string.cd_weather),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            val text = when (display) {
                WEATHER_DISPLAY_ICON -> null
                WEATHER_DISPLAY_LOWHIGH -> "${weather.low.roundToInt()}°/${weather.high.roundToInt()}°"
                else -> "${weather.temp.roundToInt()}°"
            }
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = Color.White,
                )
            }
        }
    }
}

/** 7-day list: today called out up top, the rest as icon + low/high rows — opened from the weather chip. */
@Composable
fun WeeklyForecastDialog(weather: WeatherData, onDismiss: () -> Unit) {
    val locale = Locale.getDefault()
    val dayFmt = remember(locale) { SimpleDateFormat("EEE", locale) }
    val monthDayFmt = remember(locale) { SimpleDateFormat("MMM d", locale) }
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val today = weather.daily.firstOrNull()
    val rest = weather.daily.drop(1)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.widthIn(max = 460.dp).heightIn(max = 640.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.weather_forecast_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                if (today != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            iconFor(today.kind),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp).padding(end = 14.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(labelFor(today.kind), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${weather.temp.roundToInt()}°",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LowHigh(today.low, today.high, emphasize = true)
                    }
                    Spacer(Modifier.height(14.dp))
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(rest.size) { i ->
                        val day = rest[i]
                        val parsedDate = runCatching { dateFmt.parse(day.date) }.getOrNull()
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                parsedDate?.let { dayFmt.format(it) } ?: day.date,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.width(48.dp),
                            )
                            Text(
                                parsedDate?.let { monthDayFmt.format(it) } ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(56.dp),
                            )
                            Icon(
                                iconFor(day.kind),
                                contentDescription = labelFor(day.kind),
                                modifier = Modifier.size(24.dp).weight(1f, fill = false),
                            )
                            LowHigh(day.low, day.high)
                        }
                        if (i < rest.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Low/high pair, high in the stronger weight/color — the number a viewer scans for first. */
@Composable
private fun LowHigh(low: Double, high: Double, emphasize: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "${high.roundToInt()}°",
            style = (if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
                .copy(fontFeatureSettings = "tnum"),
        )
        Text(
            " ${low.roundToInt()}°",
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusIcon(
    icon: ImageVector,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.18f),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = if (active) 0.95f else 0.35f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
