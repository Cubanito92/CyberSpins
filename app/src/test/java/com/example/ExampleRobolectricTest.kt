package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric's SDK 36 (Baklava) sandbox requires Java 21 to run — this project's
// toolchain uses Java 17, so unit tests target SDK 35 instead. This only affects the
// simulated environment for this specific test, not the app's real compileSdk/targetSdk
// (36), which is unaffected.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Radio Studio", appName)
  }
}
