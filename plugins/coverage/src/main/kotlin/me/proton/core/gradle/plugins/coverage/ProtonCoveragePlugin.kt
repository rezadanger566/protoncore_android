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

package me.proton.core.gradle.plugins.coverage

import kotlinx.kover.gradle.plugin.KoverGradlePlugin
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.KoverNames
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import kotlinx.kover.gradle.plugin.dsl.KoverReportExtension
import kotlinx.kover.gradle.plugin.dsl.KoverReportFilters
import kotlinx.kover.gradle.plugin.dsl.KoverVerifyReportConfig
import kotlinx.kover.gradle.plugin.dsl.MetricType
import me.proton.core.gradle.plugins.coverage.rules.androidRules
import me.proton.core.gradle.plugins.coverage.rules.commonRules
import me.proton.core.gradle.plugins.coverage.rules.daggerRules
import me.proton.core.gradle.plugins.coverage.rules.kotlinParcelizeRules
import me.proton.core.gradle.plugins.coverage.rules.kotlinSerializationRules
import me.proton.core.gradle.plugins.coverage.rules.roomDbRules
import net.razvan.JacocoToCoberturaExtension
import net.razvan.JacocoToCoberturaPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.language.base.plugins.LifecycleBasePlugin

/** Default dir path for the HTML report, relative to the build directory. */
private const val DEFAULT_HTML_REPORT_DIR = "reports/kover/html"

/** Default file path for the XML report, relative to the build directory. */
internal const val DEFAULT_XML_REPORT_FILE = "reports/kover/report.xml"

private const val DEFAULT_COBERTURA_BASENAME = "cobertura"

/** Default file path for the XML Cobertura report, relative to the build directory. */
private const val DEFAULT_XML_REPORT_COBERTURA_FILE =
    "reports/kover/${DEFAULT_COBERTURA_BASENAME}.xml"

private const val TASK_NAME_KOVER = KoverNames.DEFAULT_XML_REPORT_NAME
private const val TASK_NAME_COBERTURA = "coberturaXmlReport"
private const val TASK_NAME_JACOCO_TO_COBERTURA = "jacocoToCobertura"

internal const val PROTON_COVERAGE_EXT: String = "protonCoverage"

/**
 * The plugin can be applied on a non-root project.
 * It can be used to generate coverage HTML/XML report, and verify minimum coverage percentages.
 */
public class ProtonCoveragePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (target == target.rootProject) error("${this::class.simpleName} should not be applied on the root project.")

        val ext = target.extensions.create<ProtonCoverageExtension>(PROTON_COVERAGE_EXT)

        if (target.shouldSkipPluginApplication()) {
            return
        }

        ext.applyGeneralConventions()
        ext.applyConventionsFrom(target.rootProject)
        ext.finalizeValuesOnRead()

        target.afterEvaluate {
            if (ext.disabled.get()) return@afterEvaluate
            target.pluginManager.apply(KoverGradlePlugin::class.java)
            target.pluginManager.apply(JacocoToCoberturaPlugin::class.java)
            onAfterEvaluate(ext)
        }
    }

    private fun Project.onAfterEvaluate(ext: ProtonCoverageExtension) {
        extensions.configure<KoverProjectExtension> {
            //useJacoco()
        }

        if (hasAndroidPlugin()) {
            ext.androidBuildVariant.convention(DEFAULT_ANDROID_BUILD_VARIANT)
            ext.enableAndroidRules.convention(true)
            ext.enableRoomDbRules.convention(true)
        }
        if (plugins.hasPlugin(PluginIds.hilt)) {
            ext.enableDaggerRules.convention(true)
        }
        if (plugins.hasPlugin(PluginIds.kotlinParcelize)) {
            ext.enableKotlinParcelizeRules.convention(true)
        }
        if (plugins.hasPlugin(PluginIds.kotlinSerialization)) {
            ext.enableKotlinSerializationRules.convention(true)
        }
        if (ext.enableAllRules.orNull == true) {
            ext.enableAndroidRules.convention(true)
            ext.enableDaggerRules.convention(true)
            ext.enableKotlinParcelizeRules.convention(true)
            ext.enableKotlinSerializationRules.convention(true)
            ext.enableRoomDbRules.convention(true)
        }
        ext.enableCommonRules.convention(true)

        configureKoverExtension(ext)
        configureJacocoToCoberturaExtension()
        registerCoberturaReportTask()
    }

    private fun Project.hasAndroidPlugin(): Boolean =
        plugins.hasPlugin(PluginIds.androidApp) || plugins.hasPlugin(PluginIds.androidLibrary)

    private fun Project.configureKoverExtension(ext: ProtonCoverageExtension) {
        extensions.configure<KoverReportExtension> {
            defaults {
                ext.androidBuildVariant.orNull?.let { mergeWith(it) }
                html {
                    setReportDir(layout.buildDirectory.dir(DEFAULT_HTML_REPORT_DIR))
                }
                xml {
                    setReportFile(layout.buildDirectory.file(DEFAULT_XML_REPORT_FILE))
                }
                verify {
                    applyVerificationConfig(ext)
                }
            }
            filters {
                applyFiltersConfig(ext)
            }
        }
    }

    private fun Project.configureJacocoToCoberturaExtension() {
        extensions.configure<JacocoToCoberturaExtension> {
            inputFile.set(layout.buildDirectory.file(DEFAULT_XML_REPORT_FILE))
            outputFile.set(layout.buildDirectory.file(DEFAULT_XML_REPORT_COBERTURA_FILE))
            splitByPackage.set(true)
        }
        afterEvaluate {
            val reportTask = tasks.named(TASK_NAME_KOVER)
            val conversionTask = tasks.named(TASK_NAME_JACOCO_TO_COBERTURA)
            conversionTask.configure {
                dependsOn(reportTask)
                onlyIf { !reportTask.get().state.skipped || reportTask.get().state.upToDate }
                doLast {
                    fixCoberturaSourcePaths()
                }
            }
        }
    }

    /** Fix for the `<source>` paths in Cobertura XML files.
     * For Gitlab to generate a coverage visualization, we need to provide
     * full path to a source file (or at least relative to the root project dir).
     * https://docs.gitlab.com/ee/ci/testing/test_coverage_visualization.html#automatic-class-path-correction
     */
    private fun Project.fixCoberturaSourcePaths() {
        val baseCoberturaFile =
            layout.buildDirectory.asFile.get().resolve(DEFAULT_XML_REPORT_COBERTURA_FILE)
        baseCoberturaFile.parentFile
            .listFiles { _, name -> name.startsWith(DEFAULT_COBERTURA_BASENAME) }
            ?.forEach { coberturaFile ->
                val sourceDir =
                    layout.projectDirectory.asFile.resolve("src/main/kotlin").absolutePath
                val updatedText = coberturaFile.readText()
                    .replace("<source>.</source>", "<source>${sourceDir}</source>")
                coberturaFile.writeText(updatedText)
            }
    }

    private fun Project.registerCoberturaReportTask() {
        tasks.register(TASK_NAME_COBERTURA) {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Generates Cobertura report from koverXmlReport task."
            dependsOn(TASK_NAME_JACOCO_TO_COBERTURA)
        }
    }

    private fun KoverReportFilters.applyFiltersConfig(ext: ProtonCoverageExtension) {
        if (ext.enableAndroidRules.orNull == true) androidRules()
        if (ext.enableDaggerRules.orNull == true) daggerRules()
        if (ext.enableKotlinParcelizeRules.orNull == true) kotlinParcelizeRules()
        if (ext.enableKotlinSerializationRules.orNull == true) kotlinSerializationRules()
        if (ext.enableRoomDbRules.orNull == true) roomDbRules()
        if (ext.enableCommonRules.orNull == true) commonRules()
        ext.excludes.get().forEach {
            excludes(it)
        }
    }

    private fun KoverVerifyReportConfig.applyVerificationConfig(ext: ProtonCoverageExtension) {
        // Set up the requirement:
        // Min coverage percentage must be equal to max coverage (+/- 1).
        // As a result, the build will fail, if the coverage changes.
        // This will give us a chance to update the coverage value in build files.

        rule("branchCoveragePercentage") {
            bound(
                minValue = ext.branchCoveragePercentage.get() - 1,
                maxValue = ext.branchCoveragePercentage.get() + 1,
                MetricType.BRANCH,
                AggregationType.COVERED_PERCENTAGE
            )
        }
        rule("lineCoveragePercentage") {
            bound(
                minValue = ext.lineCoveragePercentage.get() - 1,
                maxValue = ext.lineCoveragePercentage.get() + 1,
                MetricType.LINE,
                AggregationType.COVERED_PERCENTAGE
            )
        }
    }
}
