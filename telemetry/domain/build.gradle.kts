/*
 * Copyright (c) 2023 Proton AG
 * This file is part of Proton AG and ProtonCore.
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

import org.gradle.kotlin.dsl.implementation
import org.gradle.kotlin.dsl.testImplementation
import studio.forface.easygradle.dsl.*

plugins {
    protonKotlinLibrary
}

protonCoverage {
    branchCoveragePercentage.set(87)
    lineCoveragePercentage.set(87)
}

publishOption.shouldBePublishedAsLib = true

kotlin {
    sourceSets {
        main {
            kotlin.srcDir("src/generated/kotlin")
        }
    }
}

dependencies {
    api(`javax-inject`)
    implementation(`coroutines-core`)
    implementation(project(Module.domain))
    implementation(project(Module.kotlinUtil))
    testImplementation(`coroutines-test`)
    testImplementation(junit)
    testImplementation(`kotlin-test`)
    testImplementation(mockk)
    testImplementation(project(Module.kotlinTest))
}
