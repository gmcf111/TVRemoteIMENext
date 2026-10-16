package com.tvremoteime.next

import android.app.Application
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import com.tvremoteime.next.IME.IMEService
import com.tvremoteime.next.theme.ThemePrefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class KeyboardRegressionTest {
    @Test fun keyboardDrawablesAndShiftStateAreIndependent() {
        val controller = Robolectric.buildService(IMEService::class.java).create()
        val service = controller.get()
        try {
            val keyboard = service.onCreateInputView() as ViewGroup
            val row = keyboard.findViewById<ViewGroup>(R.id.qweLine)
            val first = row.getChildAt(0) as Button
            val second = row.getChildAt(1) as Button
            assertNotSame(first.background, second.background)
            assertNotEquals(android.graphics.Color.WHITE, first.currentTextColor)
            assertEquals("q", first.text.toString())
            val shift = keyboard.findViewById<ImageButton>(R.id.btnCaps)
            val before = shift.background
            shift.performClick()
            assertEquals("Q", first.text.toString())
            assertNotSame(before, shift.background)
            ThemePrefs.setAccent(service, "red")
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNotSame(first.background, second.background)
            assertEquals("Q", first.text.toString())
        } finally { controller.destroy() }
    }
}
