package com.paul.infrastructure.service

interface IClipboardHandler {
    /**
     * Copies the given text to the system clipboard.
     * @param text The text to copy.
     * @param label A user-visible label for the copied data (mainly for Android).
     * @throws Exception if copying fails.
     */
    suspend fun copyTextToClipboard(text: String, label: String = "Copied Text");

    /**
     * Retrieves text from the system clipboard.
     * @return The text from the clipboard, or null if it's empty or not text.
     * @throws Exception if reading fails.
     */
    suspend fun getTextFromClipboard(): String?;
}