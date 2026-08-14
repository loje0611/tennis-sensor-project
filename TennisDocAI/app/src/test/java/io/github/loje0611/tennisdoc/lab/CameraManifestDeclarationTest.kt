package io.github.loje0611.tennisdoc.lab

import android.Manifest
import android.app.Application
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CameraManifestDeclarationTest {

    @Test
    fun cameraPermissionIsRequested() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requested = info.requestedPermissions?.toList().orEmpty()
        assertTrue(
            "Merged app must request CAMERA so the OS can show the runtime permission dialog",
            Manifest.permission.CAMERA in requested,
        )
    }

    @Test
    fun cameraHardwareFeatureIsOptional() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_CONFIGURATIONS,
        )
        val camera = info.reqFeatures?.firstOrNull { feature ->
            feature.name == PackageManager.FEATURE_CAMERA
        }
        assertNotNull(
            "Merged app must declare uses-feature android.hardware.camera",
            camera,
        )
        assertFalse(
            "android.hardware.camera must be required=false so devices without a camera can still install",
            (camera!!.flags and FeatureInfo.FLAG_REQUIRED) != 0,
        )
    }
}
