package system

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.SwingUtilities

class SystemOps {
    fun pickRepoFolder(): String? {
        return pickDirectory("Select Git Repository")
    }

    fun pickManualFolder(channel: String): String? {
        return pickDirectory("Select Folder $channel")
    }

    fun pickManualImage(channel: String): Map<String, Any?> {
        val selected = pickFile("Select Image $channel")
        return imageInfo(selected)
    }

    fun listImagesInFolder(folderPath: String): List<Map<String, Any?>> {
        val folder = runCatching { File(folderPath).canonicalFile }.getOrNull() ?: return emptyList()
        if (!folder.exists() || !folder.isDirectory) return emptyList()
        return folder
            .walkTopDown()
            .filter { it.isFile && isImageFile(it) }
            .map { file ->
                mapOf(
                    "name" to file.name,
                    "path" to file.absolutePath,
                    "size" to file.length(),
                )
            }
            .toList()
    }

    fun revealInFinder(repoPath: String, filePath: String) {
        val resolved = if (filePath.isNotEmpty()) File(repoPath, filePath) else File(repoPath)
        val fallbackDir = if (resolved.isDirectory) resolved else resolved.parentFile ?: File(repoPath)
        try {
            val os = System.getProperty("os.name", "").lowercase()
            when {
                os.contains("mac") -> {
                    if (resolved.exists() && resolved.isFile)
                        ProcessBuilder("open", "-R", resolved.absolutePath).start()
                    else
                        ProcessBuilder("open", fallbackDir.absolutePath).start()
                }
                os.contains("win") -> {
                    if (resolved.exists() && resolved.isFile)
                        ProcessBuilder("explorer", "/select,", resolved.absolutePath).start()
                    else
                        ProcessBuilder("explorer", fallbackDir.absolutePath).start()
                }
                else -> ProcessBuilder("xdg-open", fallbackDir.absolutePath).start()
            }
        } catch (_: Exception) {}
    }

    private fun pickDirectory(title: String): String? {
        var selectedPath: String? = null
        try {
            val os = System.getProperty("os.name", "").lowercase()
            if (os.contains("mac")) {
                System.setProperty("apple.awt.fileDialogForDirectories", "true")
                SwingUtilities.invokeAndWait {
                    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val file = dialog.file
                    if (dir != null && file != null) selectedPath = File(dir, file).absolutePath
                }
                System.setProperty("apple.awt.fileDialogForDirectories", "false")
            } else {
                SwingUtilities.invokeAndWait {
                    val chooser = JFileChooser().apply {
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        isMultiSelectionEnabled = false
                        dialogTitle = title
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        selectedPath = chooser.selectedFile?.absolutePath
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }
        return selectedPath
    }

    private fun pickFile(title: String): File? {
        var selected: File? = null
        try {
            val os = System.getProperty("os.name", "").lowercase()
            if (os.contains("mac")) {
                SwingUtilities.invokeAndWait {
                    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
                    dialog.filenameFilter = java.io.FilenameFilter { _, name ->
                        val ext = name.substringAfterLast('.', "").lowercase()
                        ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "tif", "tiff")
                    }
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val file = dialog.file
                    if (dir != null && file != null) selected = File(dir, file)
                }
            } else {
                SwingUtilities.invokeAndWait {
                    val chooser = JFileChooser().apply {
                        fileSelectionMode = JFileChooser.FILES_ONLY
                        isMultiSelectionEnabled = false
                        dialogTitle = title
                        fileFilter = FileNameExtensionFilter(
                            "Images (*.png, *.jpg, *.jpeg, *.webp, *.gif, *.bmp, *.tif, *.tiff)",
                            "png", "jpg", "jpeg", "webp", "gif", "bmp", "tif", "tiff"
                        )
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        selected = chooser.selectedFile
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }
        return selected?.takeIf { it.exists() && it.isFile && isImageFile(it) }
    }

    private fun imageInfo(file: File?): Map<String, Any?> {
        if (file == null) return mapOf("path" to null)
        return mapOf(
            "name" to file.name,
            "path" to file.absolutePath,
            "size" to file.length(),
        )
    }

    private fun isImageFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "tif", "tiff")
    }
}

