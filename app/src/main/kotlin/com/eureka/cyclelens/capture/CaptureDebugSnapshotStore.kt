package com.eureka.cyclelens.capture

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DebugSnapshotState(
    val pending: Boolean = false,
    val cachePath: String? = null,
    val error: String? = null,
)

class CaptureDebugSnapshotStore(
    private val cacheFile: File,
) {
    private val mutableState = MutableStateFlow(DebugSnapshotState())
    val state: StateFlow<DebugSnapshotState> = mutableState.asStateFlow()

    fun clearOnStartup() {
        cacheFile.delete()
        mutableState.value = DebugSnapshotState()
    }

    fun markPending() {
        mutableState.value = DebugSnapshotState(pending = true)
    }

    fun saved() {
        mutableState.value = DebugSnapshotState(cachePath = cacheFile.absolutePath)
    }

    fun failed(message: String) {
        mutableState.value = DebugSnapshotState(error = message)
    }

    fun delete(): Boolean {
        val deleted = !cacheFile.exists() || cacheFile.delete()
        if (deleted) {
            mutableState.value = DebugSnapshotState()
        }
        return deleted
    }

    fun file(): File = cacheFile

    companion object {
        private const val FILE_NAME = "cyclelens-debug-frame.png"

        fun create(context: Context): CaptureDebugSnapshotStore =
            CaptureDebugSnapshotStore(File(context.cacheDir, FILE_NAME))
    }
}
