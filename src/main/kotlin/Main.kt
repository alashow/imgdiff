import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.saralapps.composemultiplatformwebview.PlatformWebView
import git.GitImageService
import git.LfsResolver
import git.ProcessGitCommandRunner
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlin.system.exitProcess
import server.configureApi
import system.SystemOps
import window.WindowPrefs

fun main() = application {
    val windowState = with(WindowPrefs.load()) {
        rememberWindowState(width = width, height = height)
    }

    LaunchedEffect(windowState) {
        snapshotFlow { windowState.size }.collect { size: DpSize -> WindowPrefs.save(size) }
    }

    val runner = ProcessGitCommandRunner()
    val service = GitImageService(runner, LfsResolver(runner))
    val systemOps = SystemOps()

    val server = embeddedServer(Netty, port = 8080) {
        configureApi(service, systemOps)
    }.start()

    Thread.sleep(800)

    Window(
        state = windowState,
        icon = painterResource("ImgDiff.svg"),
        onCloseRequest = {
            try {
                WindowPrefs.save(windowState.size)
                server.stop(250, 1000)
            } finally {
                exitApplication()
                exitProcess(0)
            }
        },
        title = "Image Diff"
    ) {
        PlatformWebView(url = "http://localhost:8080", modifier = Modifier.fillMaxSize())
    }
}
