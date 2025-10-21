package com.walkcraft.app.ui.share

import android.content.Context
import androidx.core.content.FileProvider
import androidx.core.app.ShareCompat
import java.io.File

object ShareCsv {
    fun shareTextAsCsv(context: Context, fileName: String, csv: String, chooserTitle: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(csv)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        ShareCompat.IntentBuilder(context)
            .setType("text/csv")
            .addStream(uri)
            .setChooserTitle(chooserTitle)
            .startChooser()
    }
}
