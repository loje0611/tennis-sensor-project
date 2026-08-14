package io.github.loje0611.tennisdoc.testutil

import android.Manifest
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.camera.view.PreviewView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object DeviceTestUtils {
    fun grantCameraPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.CAMERA,
        )
    }

    fun revokeCameraPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.revokeRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.CAMERA,
        )
    }

    fun grantBleRuntimePermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pkg = instrumentation.targetContext.packageName
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(pkg, Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            instrumentation.uiAutomation.grantRuntimePermission(pkg, Manifest.permission.BLUETOOTH_SCAN)
            instrumentation.uiAutomation.grantRuntimePermission(pkg, Manifest.permission.BLUETOOTH_CONNECT)
            instrumentation.uiAutomation.grantRuntimePermission(pkg, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            instrumentation.uiAutomation.grantRuntimePermission(pkg, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun revokeBleRuntimePermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pkg = instrumentation.targetContext.packageName
        if (Build.VERSION.SDK_INT >= 31) {
            instrumentation.uiAutomation.revokeRuntimePermission(pkg, Manifest.permission.BLUETOOTH_SCAN)
            instrumentation.uiAutomation.revokeRuntimePermission(pkg, Manifest.permission.BLUETOOTH_CONNECT)
            instrumentation.uiAutomation.revokeRuntimePermission(pkg, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            instrumentation.uiAutomation.revokeRuntimePermission(pkg, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun previewViewScaleType(): PreviewView.ScaleType {
        waitForPreviewViewDisplayed()
        val holder = arrayOfNulls<PreviewView.ScaleType>(1)
        onView(isAssignableFrom(PreviewView::class.java)).check { view, _ ->
            holder[0] = (view as PreviewView).scaleType
        }
        return requireNotNull(holder[0]) { "PreviewView scaleType missing" }
    }

    fun waitForPreviewViewDisplayed(timeoutMs: Long = 20_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(isAssignableFrom(PreviewView::class.java)).check(
                    androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()),
                )
                return
            } catch (t: Throwable) {
                last = t
                Thread.sleep(250)
            }
        }
        throw AssertionError("PreviewView was not displayed within ${timeoutMs}ms", last)
    }

    fun waitForPreviewStreaming(timeoutMs: Long = 20_000L) {
        waitForPreviewViewDisplayed(timeoutMs)
        val holder = arrayOfNulls<PreviewView>(1)
        onView(isAssignableFrom(PreviewView::class.java)).check { view, _ ->
            holder[0] = view as PreviewView
        }
        val preview = requireNotNull(holder[0]) { "PreviewView not found" }
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val observer = androidx.lifecycle.Observer<PreviewView.StreamState> { state ->
                if (state == PreviewView.StreamState.STREAMING) {
                    latch.countDown()
                }
            }
            preview.previewStreamState.observeForever(observer)
            if (preview.previewStreamState.value == PreviewView.StreamState.STREAMING) {
                latch.countDown()
            }
        }
        assertTrue(
            "PreviewView did not reach STREAMING within ${timeoutMs}ms",
            latch.await(timeoutMs, TimeUnit.MILLISECONDS),
        )
    }

    fun clickSystemPermissionAllow(timeoutMs: Long = 8_000L): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = System.currentTimeMillis() + timeoutMs
        val allowLabels = listOf(
            "앱 사용 중에만 허용",
            "앱 사용 중에만 허용 ",
            "사용 중에만 허용",
            "허용",
            "Allow only while using the app",
            "While using the app",
            "Allow",
        )
        while (System.currentTimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: run {
                Thread.sleep(200)
                continue
            }
            val pkg = root.packageName?.toString().orEmpty()
            val isPermissionUi = pkg.contains("permission", ignoreCase = true)
            if (isPermissionUi) {
                for (label in allowLabels) {
                    val node = findNode(root) { child ->
                        child.text?.toString() == label ||
                            child.contentDescription?.toString() == label
                    }
                    if (node != null && node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                }
            }
            Thread.sleep(200)
        }
        return false
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNode(child, predicate)
            if (found != null) return found
        }
        return null
    }
}
