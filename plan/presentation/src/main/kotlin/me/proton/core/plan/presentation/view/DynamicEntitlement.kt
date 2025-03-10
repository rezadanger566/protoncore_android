/*
 * Copyright (c) 2023 Proton Technologies AG
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

@file:Suppress("MagicNumber")

package me.proton.core.plan.presentation.view

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import me.proton.core.plan.domain.entity.DynamicEntitlement
import me.proton.core.plan.presentation.R
import me.proton.core.util.kotlin.takeIfNotBlank

fun DynamicEntitlement.toView(context: Context) = when (this) {
    is DynamicEntitlement.Description -> DynamicEntitlementDescriptionView(context).apply {
        icon = this@toView.iconUrl.takeIfNotBlank() ?: getFallbackIcon(context)
        text = this@toView.text
    }

    is DynamicEntitlement.Progress -> DynamicEntitlementProgressView(context).apply {
        // TODO the tagText will directly come from BE
        tagText = when (this@toView.tag) {
            DynamicEntitlement.Progress.Base -> context.getString(R.string.plan_entitlement_tag_base)
            DynamicEntitlement.Progress.Drive -> context.getString(R.string.plan_entitlement_tag_drive)
            else -> null
        }
        text = this@toView.text
        setProgress(
            min = this@toView.min.div(1000000).toInt(),
            max = this@toView.max.div(1000000).toInt(),
            current = this@toView.current.div(1000000).toInt()
        )
    }
}

private fun getFallbackIcon(context: Context) =
    ResourcesCompat.getDrawable(context.resources, R.drawable.ic_proton_checkmark, null)
