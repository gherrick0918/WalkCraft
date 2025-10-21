package com.walkcraft.app.ui.share

import android.content.Context
import android.content.Intent
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import java.io.File

object ShareCsv {
    fun shareTextAsCsv(context: Context, fileName: String, csv: String, chooserTitle: String) {
        val file = File(context.cacheDir, fileName)
        file.parentFile?.mkdirs()
        file.writeText(csv)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

        val builder = ShareCompat.IntentBuilder(context)
            .setType("text/csv")
            .addStream(uri)
            .setChooserTitle(chooserTitle)
        builder.intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        builder.startChooser()
    }
}
