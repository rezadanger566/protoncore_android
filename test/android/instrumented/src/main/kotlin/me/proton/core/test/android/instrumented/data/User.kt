/*
 * Copyright (c) 2020 Proton Technologies AG
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

package me.proton.core.test.android.instrumented.data

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.Serializable
import me.proton.core.payment.domain.entity.PaymentMethodType
import me.proton.core.test.android.instrumented.utils.StringUtils.getEmailString
import me.proton.core.util.kotlin.deserializeList
import kotlin.random.Random
import me.proton.core.test.android.instrumented.utils.StringUtils.getAlphaNumericStringWithSpecialCharacters as randomString

@Serializable
data class  User(
    val name: String = randomString(),
    val firstName: String = randomString(),
    val lastName: String = randomString(),
    val email: String = getEmailString(),
    val password: String = "test",
    val type: Int = Random.nextInt(1, 2),
    val phone: String = "",
    val country: String = "",
    val passphrase: String = "",
    val twoFa: String = "",
    val plan: String = "",
    val paymentMethods: List<PaymentMethod> = emptyList()
) {

    val isDefault: Boolean = passphrase.isEmpty() && twoFa.isEmpty() && name.isNotEmpty()
    fun hasPaymentMethodType(paymentMethodType: PaymentMethodType): Boolean =
        paymentMethods.any { it.paymentMethodType == paymentMethodType }

    companion object Users {

        private const val testUserJsonPath: String = "sensitive/users.json"

        private val users: List<User> = InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(testUserJsonPath)
            .bufferedReader()
            .use { it.readText() }
            .deserializeList()

        fun getUser(predicate: (User) -> Boolean = { it.isDefault }): User =
            users.filterTo(ArrayList(), predicate).random()
    }
}
