package com.github.jozott00.wokwiintellij.services

import com.github.jozott00.wokwiintellij.states.WokwiSettingsState
import com.github.jozott00.wokwiintellij.utils.WokwiNotifier
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import espimg.EspImg
import espimg.ImageResult
import espimg.exceptions.EspImgException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

@Service(Service.Level.PROJECT)
class WokwiDataService(val project: Project) {

    private val configState = project.service<WokwiSettingsState>()

    private var lastFile: String? = null
    private var lastModifiedTime: Long? = null
    private var image: ImageResult? = null

//    fun retrieveImage(): ImageResult? {
//        if (checkForReload())
//            loadImage()
//
//        return image
//    }
//
//    private fun checkForReload(): Boolean {
//        if (configState.elfPath != lastFile) {
//            return true
//        }
//
//        val path = configState.elfPath
//        try {
//            val currentTimeStamp = this.readFileModification(path)
//
//            if (configState.elfPath != lastFile || currentTimeStamp != lastModifiedTime) {
//                println("RELOAD REQUIRED: ${configState.elfPath} vs $lastFile ... $currentTimeStamp vs $lastModifiedTime")
//                return true
//            }
//        } catch (e: IOException) {
//            println("RELOAD REQUIRED: EXCETPION $e")
//            return true
//        }
//
//
//        return false
//    }

//    private fun loadImage(): Boolean {
//        val path = configState.elfPath
//        println("LOADING IMAGE $path")
//        val file = File(path)
//
//        if (!file.exists()) {
//            println("File $file does not exist!")
////            thisLogger().warn("File $file does not exist!")
//        }
//
//        val vfile = VfsUtil.findFileByIoFile(file, true)
//
//        if (vfile == null) {
//            WokwiNotifier.notifyBalloon("ELF file `$path` not found", project, NotificationType.ERROR)
//            return false;
//        }
//
//        val inputStream: InputStream = vfile.inputStream
//
//        try {
//            this.image = EspImg.getFlashImage(inputStream.readAllBytes(), null, null)
//            this.lastModifiedTime = this.readFileModification(path)
//        } catch (e: EspImgException) {
//            WokwiNotifier.notifyBalloon("${e.message}", project, NotificationType.ERROR)
//            return false;
//        }
//
//        lastFile = file.path
//        return true
//    }

    private fun readFileModification(path: String): Long {
        val fileAttributes = Files.readAttributes(Paths.get(path), BasicFileAttributes::class.java)
        return fileAttributes.lastModifiedTime().toMillis()
    }


}