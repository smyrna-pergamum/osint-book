package io.github.smyrna_pergamum.osintbook.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableFloatStateOf
import io.github.smyrna_pergamum.osintbook.export.MapMarker
import io.github.smyrna_pergamum.osintbook.export.StaticMap
import io.github.smyrna_pergamum.osintbook.export.mapMarkers
import io.github.smyrna_pergamum.osintbook.domain.Analyzer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.smyrna_pergamum.osintbook.OsintApp
import io.github.smyrna_pergamum.osintbook.R
import io.github.smyrna_pergamum.osintbook.data.ReviewStatus
import io.github.smyrna_pergamum.osintbook.export.Csv
import io.github.smyrna_pergamum.osintbook.export.InvestigationBundle
import io.github.smyrna_pergamum.osintbook.export.JsonTransfer
import io.github.smyrna_pergamum.osintbook.export.PdfWriter
import io.github.smyrna_pergamum.osintbook.export.ReportOptions
import io.github.smyrna_pergamum.osintbook.export.buildCsv
import io.github.smyrna_pergamum.osintbook.export.buildReport
import io.github.smyrna_pergamum.osintbook.export.selectedFindings
import io.github.smyrna_pergamum.osintbook.providers.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Format(val mime: String, val ext: String) { PDF("application/pdf", "pdf"), CSV("text/csv", "csv"), JSON("application/json", "json") }

/** §1 Report and export: preview first, then save via SAF or share via FileProvider (FR-12, §5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(app: OsintApp, invId: String, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var bundle by remember { mutableStateOf<InvestigationBundle?>(null) }
    var options by remember { mutableStateOf(ReportOptions()) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var pendingFormat by remember { mutableStateOf(Format.PDF) }
    val savedMsg = stringResource(R.string.saved)
    val failedMsg = stringResource(R.string.save_failed)

    var mapBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var mapKey by remember { mutableStateOf<List<MapMarker>>(emptyList()) }
    var mapLoading by remember { mutableStateOf(false) }

    LaunchedEffect(invId) { bundle = app.repo.bundle(invId) }
    // The map image is fetched only when it is wanted and the set of markers changed.
    val markers = bundle?.mapMarkers(options)?.map { it.second }.orEmpty()
    LaunchedEffect(markers, options.includeMap) {
        if (!options.includeMap || markers.isEmpty() || markers == mapKey) return@LaunchedEffect
        mapLoading = true
        mapBitmap = withContext(Dispatchers.IO) { StaticMap.render(app.http, markers) }
        mapKey = markers
        mapLoading = false
    }
    LaunchedEffect(bundle, options, mapBitmap) {
        val b = bundle ?: return@LaunchedEffect
        val (file, bitmaps) = withContext(Dispatchers.Default) {
            val f = exportFile(context, b, Format.PDF)
            val map = mapBitmap.takeIf { options.includeMap }
            val analysis = Analyzer.analyze(b.targets, b.findings, b.queryRuns, b.relations, Providers.all.filter { app.repo.hasKey(it.id) }.map { it.id }.toSet())
            f.outputStream().use { PdfWriter { n -> context.getString(R.string.rep_footer, b.investigation.title.take(60), n) }.write(buildReport(context, b, options, map, analysis), it) }
            f to renderPreview(f)
        }
        pdfFile = file
        pages = bitmaps
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val b = bundle
        if (uri != null && b != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytesFor(context, b, options, pendingFormat, pdfFile)) } }.isSuccess
            }
            snack.showSnackbar(if (ok) savedMsg else failedMsg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.report)) },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        val b = bundle ?: return@Scaffold
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                // The common case first: save or share the PDF in one tap.
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.report_quick), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = pdfFile != null, onClick = { pendingFormat = Format.PDF; saver.launch(fileName(b, Format.PDF)) }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.save_as, "PDF"))
                            }
                            Button(enabled = pdfFile != null, onClick = { scope.launch { share(context, b, options, Format.PDF, pdfFile) } }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Share, null); Text("  " + stringResource(R.string.share_as, "PDF"))
                            }
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.rep_options), style = MaterialTheme.typography.titleMedium)
                val present = b.findings.map { it.providerId }.distinct()
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(options.providers == null, { options = options.copy(providers = null) }, { Text(stringResource(R.string.all_sources)) })
                    present.forEach { p ->
                        val sel = options.providers?.contains(p) == true
                        FilterChip(sel, {
                            val cur = options.providers ?: emptySet()
                            val next = if (sel) cur - p else cur + p
                            options = options.copy(providers = next.ifEmpty { null })
                        }, { Text(Providers.byId(p)?.name ?: p) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReviewStatus.entries.forEach { r ->
                        val sel = r in options.reviews
                        FilterChip(sel, {
                            val next = if (sel) options.reviews - r else options.reviews + r
                            if (next.isNotEmpty()) options = options.copy(reviews = next)
                        }, { Text(stringResource(reviewRes(r))) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(options.favoritesOnly, { options = options.copy(favoritesOnly = !options.favoritesOnly) }, { Text(stringResource(R.string.favorites)) })
                    FilterChip(options.includeNotes, { options = options.copy(includeNotes = !options.includeNotes) }, { Text(stringResource(R.string.include_notes)) })
                    FilterChip(options.includeEvidence, { options = options.copy(includeEvidence = !options.includeEvidence) }, { Text(stringResource(R.string.include_evidence)) })
                    if (markers.isNotEmpty()) FilterChip(options.includeMap, { options = options.copy(includeMap = !options.includeMap) }, { Text(stringResource(R.string.include_map)) })
                }
                if (markers.isNotEmpty() && options.includeMap) {
                    Text(stringResource(if (mapLoading) R.string.map_loading else if (mapBitmap == null && mapKey.isNotEmpty()) R.string.map_failed else R.string.include_map_hint),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.csv_columns), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Csv.Column.entries.forEach { c ->
                        val sel = c in options.csvColumns
                        FilterChip(sel, {
                            val next = if (sel) options.csvColumns - c else Csv.Column.entries.filter { it in options.csvColumns || it == c }
                            if (next.isNotEmpty()) options = options.copy(csvColumns = next)
                        }, { Text(c.name.lowercase()) })
                    }
                }
                Text(stringResource(R.string.rep_count, b.selectedFindings(options).size, b.findings.size), style = MaterialTheme.typography.bodySmall)
            }
            item {
                // §5: saved or shared files may leave the device; deleting the investigation does not delete them.
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(stringResource(R.string.export_warning), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Format.entries.forEach { fmt ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pendingFormat = fmt; saver.launch(fileName(b, fmt)) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.save_as, fmt.name))
                        }
                        OutlinedButton(onClick = { scope.launch { share(context, b, options, fmt, pdfFile) } }) {
                            Icon(Icons.Filled.Share, stringResource(R.string.share_as, fmt.name))
                        }
                    }
                }
                Text(stringResource(R.string.json_no_keys), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.preview), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.preview_zoom_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(enabled = pdfFile != null, onClick = { pdfFile?.let { openPdf(context, it) } }) { Text(stringResource(R.string.open_pdf)) }
                }
            }
            items(pages) { bmp ->
                Image(
                    bmp.asImageBitmap(), contentDescription = stringResource(R.string.preview_page),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White).pinchZoom(),
                )
            }
        }
    }
}

private fun fileName(b: InvestigationBundle, f: Format): String {
    val safe = b.investigation.title.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").take(60).ifBlank { "investigation" }
    return "$safe.${f.ext}"
}

private fun exportFile(context: Context, b: InvestigationBundle, f: Format): File {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000 }?.forEach { it.delete() }
    return File(dir, fileName(b, f))
}

private fun bytesFor(context: Context, b: InvestigationBundle, o: ReportOptions, f: Format, pdf: File?): ByteArray = when (f) {
    Format.PDF -> pdf?.readBytes() ?: ByteArray(0)
    Format.CSV -> buildCsv(b, o) { context.getString(reviewRes(it)) }.toByteArray(Charsets.UTF_8)
    // The JSON file always carries the full investigation so it can be reopened elsewhere.
    Format.JSON -> JsonTransfer.encode(b).toByteArray(Charsets.UTF_8)
}

private suspend fun share(context: Context, b: InvestigationBundle, o: ReportOptions, f: Format, pdf: File?) {
    val file = withContext(Dispatchers.IO) {
        exportFile(context, b, f).also { if (f != Format.PDF || pdf == null) it.writeBytes(bytesFor(context, b, o, f, pdf)) }
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    // The user completes the share sheet; we only grant temporary read access.
    val send = Intent(Intent.ACTION_SEND).setType(f.mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

/** Hands the PDF to the PDF viewer on the device for full-screen reading and zoom. */
private fun openPdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { context.startActivity(view) } catch (_: android.content.ActivityNotFoundException) { context.startActivity(Intent.createChooser(view, null)) }
}

/**
 * Pinch to zoom (1-5x) and pan a preview page; double tap toggles 2.5x. One-finger drags pass
 * through to the list while the page is not zoomed, so scrolling keeps working.
 */
private fun Modifier.pinchZoom(): Modifier = composed {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun clamp(o: Offset): Offset {
        val mx = size.width * (scale - 1) / 2
        val my = size.height * (scale - 1) / 2
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }
    this
        .clipToBounds()
        .onSizeChanged { size = it }
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f })
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2 || scale > 1f) {
                        scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                        offset = if (scale == 1f) Offset.Zero else clamp(offset + event.calculatePan())
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
}

private fun renderPreview(file: File): List<Bitmap> {
    val out = mutableListOf<Bitmap>()
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { r ->
            for (i in 0 until minOf(r.pageCount, 30)) r.openPage(i).use { p ->
                val bmp = Bitmap.createBitmap(p.width * 2, p.height * 2, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                out += bmp
            }
        }
    }
    return out
}
