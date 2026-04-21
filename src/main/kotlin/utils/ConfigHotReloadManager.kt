package org.iris.wiki.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.iris.wiki.Listener
import org.iris.wiki.Wiki
import org.iris.wiki.config.AliasConfig
import org.iris.wiki.config.AutoReplyConfig
import org.iris.wiki.config.CommandConfig
import org.iris.wiki.config.WikiConfig
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchService

object ConfigHotReloadManager {

    private val watchedConfigNames = setOf(
        "AliasConfig.yml",
        "CommandConfig.yml",
        "WikiConfig.yml",
        "AutoReplyConfig.yml"
    )

    @Volatile
    private var suppressEventsBefore = 0L

    private var watchJob: Job? = null
    private var watchService: WatchService? = null

    @Synchronized
    fun reloadAllConfigs(reason: String) {
        with(Wiki) {
            AliasConfig.reload()
            CommandConfig.reload()
            WikiConfig.reload()
            AutoReplyConfig.reload()
        }
        Listener.reloadRuntimeConfigCaches()
        Wiki.logger.info("配置已重载: $reason")
    }

    fun start() {
        stop()

        val configFolder = Wiki.configFolderPath
        Files.createDirectories(configFolder)
        watchService = FileSystems.getDefault().newWatchService().also { service ->
            configFolder.register(
                service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY
            )
        }

        watchJob = Wiki.launch(Dispatchers.IO) {
            watchLoop(configFolder)
        }

        Wiki.logger.info("已开启配置文件热重载: $configFolder")
    }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
        watchService?.close()
        watchService = null
    }

    private suspend fun watchLoop(configFolder: Path) {
        while (currentCoroutineContext().isActive) {
            val key = try {
                watchService?.take()
            } catch (_: ClosedWatchServiceException) {
                return
            } catch (_: InterruptedException) {
                return
            } ?: return

            val changedFiles = linkedSetOf<String>()
            key.pollEvents().forEach { event ->
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    return@forEach
                }
                val path = (event.context() as? Path)?.fileName?.toString() ?: return@forEach
                if (path in watchedConfigNames) {
                    changedFiles.add(path)
                }
            }

            if (!key.reset()) {
                Wiki.logger.warning("配置文件监听已停止，目录不可用: $configFolder")
                return
            }

            if (changedFiles.isEmpty()) {
                continue
            }

            val now = System.currentTimeMillis()
            if (now < suppressEventsBefore) {
                continue
            }

            delay(300)
            suppressEventsBefore = System.currentTimeMillis() + 1000

            runCatching {
                reloadAllConfigs("检测到 ${changedFiles.joinToString("、")} 变更")
            }.onFailure {
                Wiki.logger.warning("配置热重载失败: ${it.message}")
            }
        }
    }
}
