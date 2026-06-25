package com.github.jozott00.wokwiintellij.utils

import java.io.File
import java.io.IOException
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/**
 * Utility class for file IO operations.
 *
 * Currently unused.
 * **/
class FileHandlingUtils {
    /**
     * (METHOD) Fetches content from a file, by it's absolute path.
     * @param filePath The file path string.
     * @return A 'VirtualFile' object containing file information and content. Can be null (in the case the file isn't found)
     **/
    fun fileRead(filePath : String) : VirtualFile? {
        var target : File? = null;
        try {
            target = File(filePath);
        }
        catch (e: IOException) {
            println(e.message);
        }

        if(target != null) return LocalFileSystem.getInstance().findFileByIoFile(target as File);

        return target;
    }
}