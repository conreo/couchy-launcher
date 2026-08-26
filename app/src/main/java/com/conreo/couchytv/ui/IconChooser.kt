package com.conreo.couchytv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.conreo.couchytv.R
import com.conreo.couchytv.data.AppEntry
import com.conreo.couchytv.data.IconPack
import com.conreo.couchytv.data.IconPackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Entry point of the "Change icon" flow: pick a photo, another app's icon, or a pack icon. */
@Composable
fun IconSourceMenu(
    app: AppEntry,
    hasPacks: Boolean,
    onPhoto: () -> Unit,
    onFromApp: () -> Unit,
    onFromPack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entries = buildList {
        add(MenuEntry(stringResource(R.string.icon_source_photo), AppIcons.Image, onPhoto))
        add(MenuEntry(stringResource(R.string.icon_source_app), AppIcons.Apps, onFromApp))
        if (hasPacks) add(MenuEntry(stringResource(R.string.icon_source_pack), AppIcons.Palette, onFromPack))
    }
    MenuDialog(title = app.label, entries = entries, onDismiss = onDismiss)
}

/** Full-screen searchable grid of every other installed app's icon. */
@Composable
fun AppIconPickDialog(
    apps: List<AppEntry>,
    onPick: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    IconPickerScaffold(title = stringResource(R.string.icon_pick_apps_title), query = query, onQueryChange = { query = it }, onDismiss = onDismiss) { firstFocus ->
        items(filtered.size, key = { filtered[it].pkg }) { i ->
            val app = filtered[i]
            IconPickTile(
                label = app.label,
                icon = app.banner ?: app.icon,
                banner = app.banner != null,
                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = { onPick(app) },
            )
        }
    }
}

/** One themed icon a pack ships, identified by the app it targets — the appfilter has no other catalog. */
private data class PackIconEntry(val drawableName: String, val label: String)

/**
 * Full-screen searchable grid of icons from an installed icon pack (chosen
 * first if more than one). Browsed by target app name via the pack's own
 * appfilter mapping — the same compiled-resource lookup [IconPack.appFilterMap]
 * already uses for pack-wide selection — since that mapping is the only
 * catalog the appfilter convention actually provides.
 */
@Composable
fun PackIconPickDialog(
    packs: List<IconPackInfo>,
    apps: List<AppEntry>,
    onPick: (packPkg: String, drawableName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var chosenPack by remember { mutableStateOf(packs.singleOrNull()) }

    if (chosenPack == null) {
        // More than one pack installed — choose which one to browse first.
        IconPickerScaffold(title = stringResource(R.string.icon_pick_choose_pack), query = null, onQueryChange = {}, onDismiss = onDismiss) { firstFocus ->
            items(packs.size, key = { packs[it].pkg }) { i ->
                val pack = packs[i]
                IconPickTile(
                    label = pack.label,
                    icon = pack.icon,
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { chosenPack = pack },
                )
            }
        }
        return
    }

    val pack = chosenPack!!
    var query by remember { mutableStateOf("") }
    val appLabels = remember(apps) { apps.associate { it.pkg to it.label } }
    val entries = remember(pack.pkg, appLabels) {
        IconPack.appFilterMap(context, pack.pkg).map { (targetPkg, drawable) ->
            PackIconEntry(drawable, appLabels[targetPkg] ?: targetPkg)
        }.sortedBy { it.label.lowercase() }
    }
    val filtered = remember(entries, query) {
        if (query.isBlank()) entries else entries.filter { it.label.contains(query, ignoreCase = true) }
    }
    IconPickerScaffold(title = pack.label, query = query, onQueryChange = { query = it }, onDismiss = onDismiss) { firstFocus ->
        items(filtered.size, key = { "${filtered[it].drawableName}-${filtered[it].label}" }) { i ->
            val entry = filtered[i]
            val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, pack.pkg, entry.drawableName) {
                value = withContext(Dispatchers.IO) { IconPack.loadPackBanner(context, pack.pkg, entry.drawableName) }?.asImageBitmap()
            }
            IconPickTile(
                label = entry.label,
                icon = bitmap,
                banner = true,
                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = { onPick(pack.pkg, entry.drawableName) },
            )
        }
        if (filtered.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.icon_pick_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Shared full-screen dialog chrome: title, optional search field, and a 5-column grid. */
@Composable
private fun IconPickerScaffold(
    title: String,
    query: String?,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.(androidx.compose.ui.focus.FocusRequester) -> Unit,
) {
    // A search grid like this wants all the screen it can get, so it's a true
    // full-bleed dialog (own opaque background) rather than a card floating
    // over the dimmed launcher — unlike every other dialog here.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (query != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 12.dp)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.icon_pick_search_hint),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 15.sp,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {}),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Box(Modifier.padding(top = 12.dp))
                }
                val firstFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    content(firstFocus)
                }
            }
        }
    }
}

@Composable
private fun IconPickTile(
    label: String?,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier = Modifier,
    banner: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.aspectRatio(if (banner) 16f / 9f else 1f), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
