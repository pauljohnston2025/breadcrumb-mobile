package com.paul.infrastructure.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class ClipboardHandler constructor(private val context: Context): IClipboardHandler {
    private val clipboardManager: ClipboardManager by lazy {
        // This code will only run the first time clipboardManager is accessed
        // By then, if ClipboardHandler is used correctly (e.g., after onCreate),
        // the context will be valid.
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override suspend fun copyTextToClipboard(text: String, label: String) {
        // Clipboard operations should be quick, but for consistency with suspend
        // and potential future changes, we can keep it simple or use a specific dispatcher if needed.
        // For this simple operation, running on the caller's context (often Main) is usually fine.
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clip)
        // Optionally: Show a Toast or log
        // withContext(Dispatchers.Main) {
        //     Toast.makeText(context, "'$label' copied to clipboard", Toast.LENGTH_SHORT).show()
        // }
    }

    override suspend fun getTextFromClipboard(): String? {
        return if (clipboardManager.hasPrimaryClip()) {
            clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
        } else {
            null
        }
    }
}