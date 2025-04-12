package eu.iamgio.quarkdown.cli.watcher

import io.methvin.watcher.DirectoryChangeEvent
import java.io.File
import kotlin.concurrent.thread

private typealias JDirectoryWatcher = io.methvin.watcher.DirectoryWatcher

/**
 * A [io.methvin.watcher.DirectoryWatcher] wrapper that recursively watches a directory for changes.
 * @param watcher [io.methvin.watcher.DirectoryWatcher] instance
 */
class DirectoryWatcher(
    private val watcher: JDirectoryWatcher,
) {
    /**
     * Synchronously starts watching for changes in the directory.
     */
    fun watchBlocking() {
        watcher.watch()
    }

    /**
     * Asynchronously starts watching for changes in the directory.
     */
    fun watch() {
        thread(start = true) {
            watchBlocking()
        }
    }

    /**
     * Stops watching for changes in the directory.
     */
    fun stop() {
        watcher.close()
    }

    companion object {
        /**
         * Creates a new [DirectoryWatcher].
         * @param directory directory to watch
         * @param exclude files or directories to exclude from watching
         * @param onChange function to call when a change is detected
         */
        private fun create(
            directory: File,
            exclude: List<File> = emptyList(),
            onChange: (DirectoryChangeEvent) -> Unit,
        ) = JDirectoryWatcher
            .builder()
            .path(directory.toPath())
            .listener {
                if (exclude.none { file -> it.path().startsWith(file.absolutePath) }) {
                    onChange(it)
                }
            }.build()
            .let(::DirectoryWatcher)

        /**
         * Creates a new [DirectoryWatcher].
         * @param directory directory to watch
         * @param exclude file or directory to exclude from watching. If `null`, no files are excluded
         * @param onChange function to call when a change is detected
         */
        fun create(
            directory: File,
            exclude: File?,
            onChange: (DirectoryChangeEvent) -> Unit,
        ) = create(directory, exclude?.let(::listOf) ?: emptyList(), onChange)
    }
}
