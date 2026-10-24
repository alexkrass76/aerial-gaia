package app.orchard.tv

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.UserManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DirectBootHomeTest {
    @Test
    fun waitsForUnlockThenOpensHomeOnce() {
        val app = RuntimeEnvironment.getApplication()
        val users = app.getSystemService(UserManager::class.java)
        shadowOf(users).setUserUnlocked(false)
        val controller = Robolectric.buildActivity(DirectBootHomeActivity::class.java).setup()
        val activity = controller.get()
        assertNull(shadowOf(activity).nextStartedActivity)
        shadowOf(users).setUserUnlocked(true)
        app.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(MainActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        app.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(activity).nextStartedActivity)
        controller.pause().stop().destroy()
    }

    @Test
    @Config(qualifiers = "ru")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun rendersFrameworkOnlyPreview() {
        val view = DirectBootView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 1280, 720)
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/reports/gaia/direct-boot-preview.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
