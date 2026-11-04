package com.openxida.cedarmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openxida.cedarmanager.data.TerminalSessionController
import com.topjohnwu.superuser.Shell
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalRootLaunchTest {
    @Test
    fun rootTerminalCanRunElfNamedSoThroughSh() {
        assumeTrue(Shell.getShell().isRoot)
        val suHelp = Shell.cmd("/system/bin/su -h").exec()
        val supportsMountMaster = (suHelp.out + suHelp.err).any {
            it.contains("--target") || it.contains("--mount-master")
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val controller = TerminalSessionController(instrumentation.targetContext)
        val probe = "/data/local/tmp/cedar-terminal-probe-${android.os.Process.myPid()}.so"
        try {
            lateinit var session: com.termux.terminal.TerminalSession
            instrumentation.runOnMainSync {
                session = controller.start(root = true, workingDirectory = "/data/local/tmp")
                session.initializeEmulator(80, 40)
                controller.onEmulatorReady(session)
            }
            controller.write(
                "cp /system/bin/sh '$probe' && chmod 700 '$probe' && " +
                    "sh -c \"'$probe' -c /system/bin/id\"; result=\$?; " +
                    "printf 'HOME=%s TMPDIR=%s\\n' \"\$HOME\" \"\$TMPDIR\"; " +
                    "if [ \"\$(readlink /proc/self/ns/mnt)\" = \"\$(readlink /proc/1/ns/mnt)\" ]; " +
                    "then echo CEDAR_MOUNT_MASTER=yes; else echo CEDAR_MOUNT_MASTER=no; fi; " +
                    "printf 'CEDAR_PROBE_STATUS=%s\\n' \"\$result\"; " +
                    "echo CEDAR_PROBE_DONE\r",
            )
            val deadline = System.currentTimeMillis() + 12_000L
            var transcript = ""
            while (System.currentTimeMillis() < deadline) {
                transcript = session.emulator.screen.transcriptText
                if (transcript.contains("uid=0(root)") &&
                    transcript.contains("HOME=/ TMPDIR=/data/local/tmp") &&
                    (!supportsMountMaster || transcript.contains("CEDAR_MOUNT_MASTER=yes")) &&
                    transcript.contains("CEDAR_PROBE_STATUS=0")) break
                Thread.sleep(50)
            }
            assertTrue(transcript, transcript.contains("uid=0(root)"))
            assertTrue(transcript, transcript.contains("HOME=/ TMPDIR=/data/local/tmp"))
            if (supportsMountMaster) assertTrue(transcript, transcript.contains("CEDAR_MOUNT_MASTER=yes"))
            assertTrue(transcript, transcript.contains("CEDAR_PROBE_STATUS=0"))
            controller.write("'$probe' -c 'read -t 3 value; echo CEDAR_INPUT_\$value'\r")
            Thread.sleep(450)
            controller.write("cedarProbe42\r")
            while (System.currentTimeMillis() < deadline + 6_000L) {
                transcript = session.emulator.screen.transcriptText
                if (transcript.contains("CEDAR_INPUT_cedarProbe42")) break
                Thread.sleep(50)
            }
            assertTrue(transcript, transcript.contains("CEDAR_INPUT_cedarProbe42"))
        } finally {
            controller.close()
            Shell.cmd("rm -f '$probe'").exec()
        }
    }
}
