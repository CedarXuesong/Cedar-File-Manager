package com.openxida.cedarmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.openxida.cedarmanager.ui.CedarManagerApp
import com.openxida.cedarmanager.ui.installAnimationSpeedRecomposerFactory

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val treeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModel.setSafTreeUri(uri.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installAnimationSpeedRecomposerFactory { viewModel.settings.value.animationSpeed }
        enableEdgeToEdge()
        setContent {
            CedarManagerApp(
                viewModel = viewModel,
                hasAllFilesAccess = Environment.isExternalStorageManager(),
                onRequestAllFilesAccess = ::requestAllFilesAccess,
                onRequestSafTree = { treeLauncher.launch(null) },
            )
        }
    }

    private fun requestAllFilesAccess() {
        val packageUri = Uri.parse("package:$packageName")
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
        runCatching { startActivity(intent) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }
}
