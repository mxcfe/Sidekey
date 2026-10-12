package com.mxcfe.key.privilege

import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

object ShizukuShell {
    data class CommandResult(val code: Int, val output: String)

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
        return execResult(command, timeoutSec)?.code == 0
    }

    fun execOutput(command: String, timeoutSec: Long): String? {
        return execResult(command, timeoutSec)?.output
    }

    fun execResult(command: String, timeoutSec: Long): CommandResult? {
        if (!ShizukuAccess.granted()) return null
        return try {
            val process = start(command)
            var outBytes = ByteArray(0)
            var errBytes = ByteArray(0)
            val stdout = Thread { process.inputStream.use { outBytes = it.readBytes() } }
            val stderr = Thread { process.errorStream.use { errBytes = it.readBytes() } }
            stdout.start()
            stderr.start()
            val code = awaitExit(process, timeoutSec)
            stdout.join(1_000)
            stderr.join(1_000)
            if (code == null) {
                null
            } else {
                CommandResult(code, String(outBytes, Charsets.UTF_8) + String(errBytes, Charsets.UTF_8))
            }
        } catch (error: Throwable) {
            Log.e(TAG, "privileged cmd failed: $command", error)
            null
        }
    }

    private fun awaitExit(process: Process, timeoutSec: Long): Int? {
        val finished = try {
            val method = process.javaClass.getMethod(
                "waitForTimeout",
                Long::class.javaPrimitiveType,
                TimeUnit::class.java,
            )
            method.invoke(process, timeoutSec, TimeUnit.SECONDS) as Boolean
        } catch (error: Throwable) {
            Log.w(TAG, "waitForTimeout failed", error)
            try {
                process.waitFor(timeoutSec, TimeUnit.SECONDS)
            } catch (fallback: Throwable) {
                Log.w(TAG, "waitFor failed", fallback)
                false
            }
        }
        if (!finished) {
            runCatching { process.destroy() }
            return null
        }
        return runCatching { process.waitFor() }.getOrElse {
            runCatching { process.exitValue() }.getOrNull()
        }
    }

    private const val TAG = "ActionKeyDispatcher"
}
