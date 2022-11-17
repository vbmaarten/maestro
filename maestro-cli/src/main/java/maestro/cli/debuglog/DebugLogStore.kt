package maestro.cli.debuglog

import maestro.cli.App
import maestro.utils.FileUtils
import net.harawata.appdirs.AppDirsFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Properties
import java.util.logging.FileHandler
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

object DebugLogStore {

    private const val APP_NAME = "maestro"
    private const val APP_AUTHOR = "mobile_dev"
    private const val LOG_DIR_DATE_FORMAT = "yyyy-MM-dd_HHmmss"
    private const val KEEP_LOG_COUNT = 5
    val logDirectory = File(AppDirsFactory.getInstance().getUserLogDir(APP_NAME, null, APP_AUTHOR))

    private val currentRunLogDirectory: File
    private val maestroLogFile: File
    init {
        val dateFormatter = DateTimeFormatter.ofPattern(LOG_DIR_DATE_FORMAT)
        val date = dateFormatter.format(LocalDateTime.now())

        currentRunLogDirectory = File(logDirectory, date)
        currentRunLogDirectory.mkdirs()
        removeOldLogs(logDirectory)

        maestroLogFile = logFile("maestro")
        logSystemInfo()
    }

    fun loggerFor(clazz: Class<*>): Logger {
        val logger = Logger.getLogger(clazz.name)

        val fileHandler = FileHandler(maestroLogFile.absolutePath)
        fileHandler.formatter = object : SimpleFormatter() {
            private val format = "[%1\$tF %1\$tT] [%2$-7s] %3\$s %n"

            @Suppress("DefaultLocale")
            @Synchronized
            override fun format(lr: LogRecord): String {
                return java.lang.String.format(
                    format,
                    Date(lr.millis),
                    lr.level.localizedName,
                    lr.message
                )
            }
        }

        logger.addHandler(fileHandler)
        return logger
    }

    fun logOutputOf(processBuilder: ProcessBuilder) {
        val command = processBuilder.command().first() ?: "unknown"
        val logFile = logFile(command)
        val redirect = ProcessBuilder.Redirect.to(logFile)
        processBuilder
            .redirectOutput(redirect)
            .redirectError(redirect)
    }

    fun finalizeRun() {
        val output = File(currentRunLogDirectory.parent, "${currentRunLogDirectory.name}.zip")
        FileUtils.zipDir(currentRunLogDirectory.toPath(), output.toPath())
        currentRunLogDirectory.deleteRecursively()
    }

    private fun logFile(named: String): File {
        return File(currentRunLogDirectory, "$named.log")
    }

    private fun removeOldLogs(baseDir: File) {
        if (!baseDir.isDirectory) { return }

        val existing = baseDir.listFiles() ?: return
        val toDelete = existing.sortedByDescending { it.name }
            .drop(KEEP_LOG_COUNT)
            .toList()

        toDelete.forEach { it.deleteRecursively() }
    }

    private fun logSystemInfo() {
        val logData = """
            Maestro version: ${appVersion()}
            OS: ${System.getProperty("os.name")}
            OS version: ${System.getProperty("os.version")}
            Architecture: ${System.getProperty("os.arch")}
            """.trimIndent() + "\n"

        logFile("system_info").writeText(logData)
    }

    private fun appVersion(): String {
        val props = App::class.java.classLoader.getResourceAsStream("version.properties").use {
            Properties().apply { load(it) }
        }

        return props["version"].toString()
    }
}