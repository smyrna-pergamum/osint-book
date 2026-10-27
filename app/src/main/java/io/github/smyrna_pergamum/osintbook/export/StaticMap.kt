package io.github.smyrna_pergamum.osintbook.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import io.github.smyrna_pergamum.osintbook.net.Http
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

data class MapMarker(val lat: Double, val lon: Double, val label: String)

/**
 * Draws a static OpenStreetMap image for the PDF report: tiles from tile.openstreetmap.org are
 * stitched on the device and numbered markers are drawn on top. Only runs when the user keeps
 * "include map" on (it is the report's only network use).
 */
object StaticMap {
    private const val TILE = 256
    private const val WIDTH = 1024
    private const val HEIGHT = 640

    private fun worldX(lon: Double, z: Int) = (lon + 180.0) / 360.0 * TILE * (1 shl z)
    private fun worldY(lat: Double, z: Int): Double {
        val r = lat.coerceIn(-85.0, 85.0) * PI / 180
        return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * TILE * (1 shl z)
    }

    /** Highest zoom (max 12, city level) at which every marker fits with a margin. */
    fun zoomFor(markers: List<MapMarker>): Int {
        for (z in 12 downTo 1) {
            val xs = markers.map { worldX(it.lon, z) }
            val ys = markers.map { worldY(it.lat, z) }
            if (xs.max() - xs.min() < WIDTH * 0.75 && ys.max() - ys.min() < HEIGHT * 0.75) return z
        }
        return 1
    }

    suspend fun render(http: Http, markers: List<MapMarker>): Bitmap? {
        if (markers.isEmpty()) return null
        val z = if (markers.size == 1) 8 else zoomFor(markers)
        val cx = markers.map { worldX(it.lon, z) }.let { (it.min() + it.max()) / 2 }
        val cy = markers.map { worldY(it.lat, z) }.let { (it.min() + it.max()) / 2 }
        val left = cx - WIDTH / 2
        val top = cy - HEIGHT / 2
        val n = 1 shl z
        val tiles = mutableListOf<Triple<Int, Int, Int>>() // x, y, wrapped x
        for (ty in floor(top / TILE).toInt()..floor((top + HEIGHT) / TILE).toInt()) {
            if (ty < 0 || ty >= n) continue
            for (tx in floor(left / TILE).toInt()..floor((left + WIDTH) / TILE).toInt()) tiles += Triple(tx, ty, ((tx % n) + n) % n)
        }
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(230, 230, 228))
        val images = coroutineScope {
            tiles.map { (tx, ty, wx) ->
                async {
                    val bytes = runCatching { http.getBytes("https://tile.openstreetmap.org/$z/$wx/$ty.png", mapOf("Accept" to "image/png")) }.getOrNull()
                    Triple(tx, ty, bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) })
                }
            }.awaitAll()
        }
        if (images.all { it.third == null }) return null
        images.forEach { (tx, ty, img) ->
            img?.let { c.drawBitmap(it, (tx * TILE - left).toFloat(), (ty * TILE - top).toFloat(), null); it.recycle() }
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(194, 65, 12) }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f }
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        markers.forEachIndexed { i, m ->
            val x = (worldX(m.lon, z) - left).toFloat()
            val y = (worldY(m.lat, z) - top).toFloat()
            c.drawCircle(x, y, 18f, fill)
            c.drawCircle(x, y, 18f, ring)
            c.drawText("${i + 1}", x, y + 8f, num)
        }
        val attr = "© OpenStreetMap contributors"
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 40, 40); textSize = 18f }
        val w = small.measureText(attr)
        c.drawRect(WIDTH - w - 16, HEIGHT - 30f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply { color = Color.argb(200, 255, 255, 255) })
        c.drawText(attr, WIDTH - w - 8, HEIGHT - 9f, small)
        return bmp
    }
}
