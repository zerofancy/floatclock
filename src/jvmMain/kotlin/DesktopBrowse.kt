/*
 * Copyright © 2017 jjYBdx4IL (https://github.com/jjYBdx4IL)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.commons.lang3.SystemUtils
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.*

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
/**
 *
 * @author jjYBdx4IL
 */
object DesktopBrowse {
    //    private static final Logger LOG = LoggerFactory.getLogger(Desktop.class);
    fun browse(uri: URI): Boolean {
        if (browseDESKTOP(uri)) {
            return true
        }
        return openSystemSpecific(uri.toString())

//        LOG.warn(String.format("failed to browse %s", uri));
    }

    fun open(file: File): Boolean {
        if (openDESKTOP(file)) {
            return true
        }
        return openSystemSpecific(file.path)

//        LOG.warn(String.format("failed to open %s", file.getAbsolutePath()));
    }

    fun edit(file: File): Boolean {
        if (editDESKTOP(file)) {
            return true
        }
        return openSystemSpecific(file.path)

//        LOG.warn(String.format("failed to edit %s", file.getAbsolutePath()));
    }

    private fun openSystemSpecific(what: String): Boolean {
        if (SystemUtils.IS_OS_LINUX) {
            if (isXDG) {
                if (runCommand("xdg-open", "%s", what)) {
                    return true
                }
            }
            if (isKDE) {
                if (runCommand("kde-open", "%s", what)) {
                    return true
                }
            }
            if (isGNOME) {
                if (runCommand("gnome-open", "%s", what)) {
                    return true
                }
            }
            if (runCommand("kde-open", "%s", what)) {
                return true
            }
            if (runCommand("gnome-open", "%s", what)) {
                return true
            }
        }
        if (SystemUtils.IS_OS_MAC) {
            if (runCommand("open", "%s", what)) {
                return true
            }
        }
        if (SystemUtils.IS_OS_WINDOWS) {
            if (runCommand("explorer", "%s", what)) {
                return true
            }
        }
        return false
    }

    private fun browseDESKTOP(uri: URI): Boolean {
        return try {
            if (!Desktop.isDesktopSupported()) {
                //                LOG.debug("Platform is not supported.");
                return false
            }
            if (!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                //                LOG.debug("BROWSE is not supported.");
                return false
            }

            //            LOG.info("Trying to use Desktop.getDesktop().browse() with " + uri.toString());
            Desktop.getDesktop().browse(uri)
            true
        } catch (t: Throwable) {
            //            LOG.error("Error using desktop browse.", t);
            false
        }
    }

    private fun openDESKTOP(file: File): Boolean {
        return try {
            if (!Desktop.isDesktopSupported()) {
                //                LOG.debug("Platform is not supported.");
                return false
            }
            if (!Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                //                LOG.debug("OPEN is not supported.");
                return false
            }

            //            LOG.info("Trying to use Desktop.getDesktop().open() with " + file.toString());
            Desktop.getDesktop().open(file)
            true
        } catch (t: Throwable) {
            //            LOG.error("Error using desktop open.", t);
            false
        }
    }

    private fun editDESKTOP(file: File): Boolean {
        return try {
            if (!Desktop.isDesktopSupported()) {
                //                LOG.debug("Platform is not supported.");
                return false
            }
            if (!Desktop.getDesktop().isSupported(Desktop.Action.EDIT)) {
                //                LOG.debug("EDIT is not supported.");
                return false
            }

            //            LOG.info("Trying to use Desktop.getDesktop().edit() with " + file);
            Desktop.getDesktop().edit(file)
            true
        } catch (t: Throwable) {
            //            LOG.error("Error using desktop edit.", t);
            false
        }
    }

    private fun runCommand(command: String, args: String, file: String): Boolean {

//        LOG.info("Trying to exec:\n   cmd = " + command + "\n   args = " + args + "\n   %s = " + file);
        val parts = prepareCommand(command, args, file)
        return try {
            val p = Runtime.getRuntime().exec(parts) ?: return false
            try {
                val retval = p.exitValue()
                if (retval == 0) {
                    //                    LOG.error("Process ended immediately.");
                    false
                } else {
                    //                    LOG.error("Process crashed.");
                    false
                }
            } catch (itse: IllegalThreadStateException) {
                //                LOG.error("Process is running.");
                true
            }
        } catch (e: IOException) {
            //            LOG.error("Error running command.", e);
            false
        }
    }

    private fun prepareCommand(command: String, args: String?, file: String): Array<String> {
        val parts: MutableList<String> = ArrayList()
        parts.add(command)
        if (args != null) {
            for (s in args.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                val s2 = String.format(s, file) // put in the filename thing
                parts.add(s2.trim { it <= ' ' })
            }
        }
        return parts.toTypedArray()
    }

    private val isXDG: Boolean
        get() {
            val xdgSessionId = System.getenv("XDG_SESSION_ID")
            return xdgSessionId != null && !xdgSessionId.isEmpty()
        }
    private val isGNOME: Boolean
        get() {
            val gdmSession = System.getenv("GDMSESSION")
            return gdmSession != null && gdmSession.lowercase(Locale.getDefault()).contains("gnome")
        }
    private val isKDE: Boolean
        get() {
            val gdmSession = System.getenv("GDMSESSION")
            return gdmSession != null && gdmSession.lowercase(Locale.getDefault()).contains("kde")
        }
}