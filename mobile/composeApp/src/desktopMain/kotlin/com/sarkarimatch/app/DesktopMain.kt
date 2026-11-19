package com.sarkarimatch.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sarkarimatch.app.di.initKoin
import com.sarkarimatch.core.data.AppConfig
import com.sarkarimatch.core.data.Platform
import com.sarkarimatch.core.data.di.desktopDataModule
import java.io.File

/**
 * Desktop dev target (never shipped): the shared UI + data layer on the JVM against a local backend.
 *
 *     ./gradlew :composeApp:run                                   # http://localhost:8080/, data in ~/.sarkarimatch-dev
 *     SM_API_BASE_URL=http://host:8080/ SM_DATA_DIR=/tmp/sm ./gradlew :composeApp:run
 */
fun main() {
    startDesktopKoin(
        baseUrl = System.getenv("SM_API_BASE_URL") ?: "http://localhost:8080/",
        storageDir = File(System.getenv("SM_DATA_DIR") ?: (System.getProperty("user.home") + "/.sarkarimatch-dev")),
    )
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Sarkari Match (dev)",
            state = rememberWindowState(size = DpSize(412.dp, 892.dp)),
        ) { App() }
    }
}

/** Starts Koin for the desktop target with dev auth on (`Bearer dev:<uid>`). Also used by the e2e test. */
fun startDesktopKoin(baseUrl: String, storageDir: File) = initKoin(
    config = AppConfig(
        platform = Platform.ANDROID, // the store path is irrelevant on desktop; Android = Play-style verification
        apiBaseUrl = baseUrl.let { if (it.endsWith('/')) it else "$it/" },
        versionName = "0.1.0-desktop",
        versionCode = 1,
        httpLogging = false,
        devAuth = true,
    ),
    platformModule = desktopDataModule(storageDir),
)
