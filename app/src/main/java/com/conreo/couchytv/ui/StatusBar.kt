package com.conreo.couchytv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.conreo.couchytv.R
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.conreo.couchytv.data.NetStatus

@Composable
fun StatusBar(
    net: NetStatus,
    time: String,
    date: String,
    showVpn: Boolean,
    glass: Boolean,
    onVpnClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onSettingsClick: () -> Unit,
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
        // Order: VPN · network · settings · date · clock (clock far right)
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
