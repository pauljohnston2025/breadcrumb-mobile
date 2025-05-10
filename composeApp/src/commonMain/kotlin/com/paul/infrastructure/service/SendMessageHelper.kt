package com.paul.infrastructure.service

import androidx.compose.runtime.MutableState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SendMessageHelper {
    companion object {
        suspend fun <T> sendingMessage(
            viewModelScope: CoroutineScope,
            message: MutableState<String>,
            msg: String,
            cb: suspend () -> T): T? {
            try {
                viewModelScope.launch(Dispatchers.Main) {
                    message.value = msg
                }
                return cb()
            } catch (t: Throwable) {
                Napier.d("Failed to do operation: $msg $t")
            } finally {
                viewModelScope.launch(Dispatchers.Main) {
                    message.value = ""
                }
            }

            return null
        }
    }
}