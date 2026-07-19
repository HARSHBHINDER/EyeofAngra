package com.eyeofangra.app.feature

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.eyeofangra.app.ui.theme.Angra

/// Shows why a permission is needed *before* the system dialog, and only when the
/// user reaches the feature that needs it. Once permanently denied, the system
/// dialog no longer appears, so the gate offers app settings instead of a button
/// that would silently do nothing.
@Composable
fun PermissionGate(
    permissions: List<String>,
    title: String,
    rationale: String,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity

    fun granted() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var isGranted by remember { mutableStateOf(granted()) }
    var asked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        isGranted = granted()
        asked = true
    }

    if (isGranted) {
        content()
        return
    }

    // After a denial with no rationale offered, the OS will not prompt again.
    val permanentlyDenied = asked && permissions.none {
        ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Angra.Background)
            .padding(Angra.s5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            title,
            color = Angra.TextPrimary,
            fontSize = Angra.titleSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Angra.s3))
        Text(rationale, color = Angra.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Angra.s5))

        if (permanentlyDenied) {
            Text(
                "Android will not ask again, so this has to be changed in system settings.",
                color = Angra.Warning,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Angra.s4))
            PrimaryAction("Open app settings") {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            }
        } else {
            PrimaryAction("Allow access") { launcher.launch(permissions.toTypedArray()) }
        }
    }
}
