package com.mxcfe.key.privilege

import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

object ShizukuShell {
    fun start(command: String): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", command), null, null)
        return process as Process
    }

    fun exec(command: String, timeoutSec: Long): Boolean {
        if (!ShizukuAccess.granted()) return false
        return try {
            val process = start(command)
            val stdout = Thread { process.inputStream.use { it.readBytes() } }
            val stderr = Thread { process.errorStream.use { it.readBytes() } }
            stdout.start()
            stderr.start()
            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                false
            } else {
                stdout.join(500)
                stderr.join(500)
                process.exitValue() == 0
            }
        } catch (error: Throwable) {
            Log.e(TAG, "privileged cmd failed: $command", error)
            false
        }
    }

    private const val TAG = "ActionKeyDispatcher"
}
