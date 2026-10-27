package io.github.smyrna_pergamum.osintbook.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.smyrna_pergamum.osintbook.OsintApp
import io.github.smyrna_pergamum.osintbook.R
import io.github.smyrna_pergamum.osintbook.providers.COORDINATES_LABEL
import io.github.smyrna_pergamum.osintbook.providers.Providers
import io.github.smyrna_pergamum.osintbook.providers.parseCoordinates
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale

data class MapPoint(val lat: Double, val lon: Double, val title: String, val subtitle: String)

/**
 * OpenStreetMap view. Tiles are fetched from tile.openstreetmap.org only while a map is shown,
 * and cached in the app's private cache (no storage permission).
 */
@Composable
fun OsmMap(points: List<MapPoint>, modifier: Modifier = Modifier, zoomButtons: Boolean = false) {
    val context = LocalContext.current
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setUseDataConnection(true)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 2.0
            // osmdroid draws tiles beyond its own bounds unless clipped; inside a scrolling column
            // that painted over neighbouring content and left the map area looking grey.
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BOUNDS
            zoomController.setVisibility(if (zoomButtons) CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT else CustomZoomButtonsController.Visibility.NEVER)
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    AndroidView({ map }, modifier.clipToBounds()) { m ->
        // Markers and camera are set only when the points change, so recomposition never undoes the user's pan.
        if (m.tag == points) return@AndroidView
        m.tag = points
        m.overlays.clear()
        points.forEach { p ->
            m.overlays += Marker(m).apply {
                position = GeoPoint(p.lat, p.lon)
                title = p.title
                snippet = p.subtitle
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
        }
        // Zooming before the view has a size leaves osmdroid without a projection, so no tiles are
        // requested and the map stays grey. The camera is therefore applied after the first layout.
        val applyCamera = {
            when {
                points.size == 1 -> { m.controller.setZoom(10.0); m.controller.setCenter(GeoPoint(points[0].lat, points[0].lon)) }
                points.size > 1 -> {
                    val box = BoundingBox.fromGeoPoints(points.map { GeoPoint(it.lat, it.lon) })
                    m.zoomToBoundingBox(box.increaseByScale(1.4f), false)
                }
                else -> m.controller.setZoom(2.0)
            }
            m.invalidate()
        }
        if (m.width > 0 && m.height > 0) applyCamera() else m.addOnFirstLayoutListener { _, _, _, _, _ -> applyCamera() }
    }
}

/** Full-screen map of every coordinate in an investigation, or of one finding when [findingId] is set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(app: OsintApp, invId: String, findingId: String?, back: () -> Unit, openFinding: (String) -> Unit) {
    val context = LocalContext.current
    val rows by remember(invId) { app.db.findings().observeRows(invId) }.collectAsState(initial = emptyList())
    val coords = rows.filter { it.label == COORDINATES_LABEL && (findingId == null || it.id == findingId) }
        .mapNotNull { r -> parseCoordinates(r.value)?.let { r to it } }
    val points = coords.map { (r, c) -> MapPoint(c.first, c.second, r.targetValue, Providers.byId(r.providerId)?.name ?: r.providerId) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.rep_map)) },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (rows.isNotEmpty()) OsmMap(points, Modifier.fillMaxSize(), zoomButtons = true)
            }
            Text(stringResource(R.string.map_attribution), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            // Accessible text alternative to the map (UX-02).
            LazyColumn(Modifier.fillMaxWidth().weight(0.4f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                items(coords, key = { it.first.id }) { (r, c) ->
                    TextButton(onClick = { openFinding(r.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("${r.targetValue} · ${Providers.byId(r.providerId)?.name ?: r.providerId}", style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.lat_lon, "%.5f".format(Locale.ROOT, c.first), "%.5f".format(Locale.ROOT, c.second)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            TextButton(onClick = { points.firstOrNull()?.let { openInMapApp(context, it.lat, it.lon, it.title) } },
                enabled = points.isNotEmpty(), modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.open_in_map_app)) }
        }
    }
}

/** Hands the coordinates to whatever map app the user has (geo: URI); nothing is sent by us. */
fun openInMapApp(context: Context, lat: Double, lon: Double, label: String) {
    val q = String.format(Locale.ROOT, "%.6f,%.6f", lat, lon)
    val uri = Uri.parse("geo:$q?q=$q(${Uri.encode(label)})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        openLink(context, "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=10/$lat/$lon")
    }
}
