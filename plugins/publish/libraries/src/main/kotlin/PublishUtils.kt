import org.gradle.api.Project
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * Execute a command line and return stdout. [command] is interpreted by being split using space, so if command
 * parameters contain space, they should be passed using [args] instead.
 */
internal fun Project.runCommand(
    command: String,
    args: List<String> = emptyList(),
    currentWorkingDir: File = file("./")
): String {
    val byteOut = ByteArrayOutputStream()
    val commandAsList = command.split("\\s".toRegex()).plus(args)
    val commandListPrefix = if (isWindows()) listOf("cmd", "/c") else emptyList()
    exec {
        workingDir = currentWorkingDir
        commandLine = commandListPrefix + commandAsList
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

internal fun isWindows() = System.getProperty("os.name").toLowerCase(Locale.US).contains("windows")
