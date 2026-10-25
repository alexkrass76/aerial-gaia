package app.orchard.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.orchard.tv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only visible rows load icons; draw into a bounded bitmap rather than retaining full assets. */
@Composable
internal fun PackageIcon(pkg: String, size: Dp = 32.dp) {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { size.roundToPx() }.coerceIn(1, 128)
    var icon by remember(pkg, pixels) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(pkg, pixels) {
        icon = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(pkg).mutate()
                val bitmap = android.graphics.Bitmap.createBitmap(pixels, pixels, android.graphics.Bitmap.Config.ARGB_8888)
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                val scale = pixels.toFloat() / maxOf(width, height)
                val w = (width * scale).toInt().coerceAtLeast(1)
                val h = (height * scale).toInt().coerceAtLeast(1)
                val left = (pixels - w) / 2
                val top = (pixels - h) / 2
                drawable.setBounds(left, top, left + w, top + h)
                drawable.draw(android.graphics.Canvas(bitmap))
                bitmap.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        val image = icon
        if (image != null) Image(image, contentDescription = null, modifier = Modifier.size(size))
        else GlyphIcon(Glyph.Bag, Palette.Label, size)
    }
}
