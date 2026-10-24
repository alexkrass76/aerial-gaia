package app.orchard.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.View

/** APK resources and framework drawing only: safe before credential storage is unlocked. */
internal class DirectBootView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = context.getString(R.string.app_name)
    private val loading = context.getString(R.string.gaia_boot_loading)
    private val face = if (Build.VERSION.SDK_INT >= 26) {
        runCatching { resources.getFont(R.font.inter) }.getOrNull()
            ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
    } else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val background = LinearGradient(0f, 540f, 960f, 0f,
        intArrayOf(0xFF071E29.toInt(), 0xFF102B54.toInt(), 0xFF392259.toInt()),
        null, Shader.TileMode.CLAMP)
    private val teal = RadialGradient(100f, 70f, 560f,
        intArrayOf(0x8033B59D.toInt(), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    private val violet = RadialGradient(850f, 430f, 480f,
        intArrayOf(0x665D50BA, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    private val glass = LinearGradient(290f, 180f, 670f, 360f,
        intArrayOf(0x26FFFFFF, 0x0CFFFFFF), null, Shader.TileMode.CLAMP)

    init {
        contentDescription = "$title. $loading"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Static, resolution-independent artwork. No timers, bitmap allocations, or GPU blur.
        canvas.drawColor(0xFF101D33.toInt())
        val scale = minOf(width / 960f, height / 540f)
        canvas.save()
        canvas.translate((width - 960f * scale) / 2f, (height - 540f * scale) / 2f)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        for (shader in arrayOf(background, teal, violet)) {
            paint.shader = shader
            canvas.drawRect(0f, 0f, 960f, 540f, paint)
        }
        paint.shader = glass
        canvas.drawRoundRect(278f, 174f, 682f, 366f, 32f, 32f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = 0x33FFFFFF
        canvas.drawRoundRect(278f, 174f, 682f, 366f, 32f, 32f, paint)
        paint.style = Paint.Style.FILL
        paint.typeface = face
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 43f
        paint.color = Color.WHITE
        canvas.drawText(title, 480f, 263f, paint)
        paint.textSize = 15f
        paint.color = 0xBDEBEBF5.toInt()
        canvas.drawText(loading, 480f, 300f, paint)
        // An understated waiting motif, not a fabricated progress percentage.
        for (i in 0..2) {
            paint.color = if (i == 1) 0xCCFFFFFF.toInt() else 0x55FFFFFF
            canvas.drawCircle(468f + i * 12f, 328f, 2.5f, paint)
        }
        canvas.restore()
    }
}
