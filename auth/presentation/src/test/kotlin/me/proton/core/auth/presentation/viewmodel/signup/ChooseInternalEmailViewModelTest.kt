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

package me.proton.core.auth.presentation.viewmodel.signup

import app.cash.turbine.test
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import me.proton.core.auth.domain.usecase.AccountAvailability
import me.proton.core.auth.presentation.viewmodel.signup.ChooseInternalEmailViewModel.State
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.observability.domain.ObservabilityManager
import me.proton.core.observability.domain.metrics.SignupFetchDomainsTotal
import me.proton.core.observability.domain.metrics.SignupUsernameAvailabilityTotal
import me.proton.core.observability.domain.metrics.common.HttpApiStatus
import me.proton.core.observability.domain.metrics.common.UsernameAvailabilityStatus
import me.proton.core.test.android.ArchTest
import me.proton.core.test.kotlin.CoroutinesTest
import me.proton.core.user.domain.repository.DomainRepository
import me.proton.core.user.domain.repository.UserRepository
import me.proton.core.util.kotlin.coroutine.result
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChooseInternalEmailViewModelTest : ArchTest by ArchTest(), CoroutinesTest by CoroutinesTest() {

    private val observabilityManager = mockk<ObservabilityManager>(relaxed = true)

    private val userRepository = mockk<UserRepository> {
        coEvery { checkUsernameAvailable(any(), any()) } coAnswers {
            result("checkUsernameAvailable") { }
        }
    }

    private val domainRepository = mockk<DomainRepository> {
        coEvery { getAvailableDomains(any()) } coAnswers {
            result("getAvailableDomains") { listOf("protonmail.com", "protonmail.ch") }
        }
    }

    private lateinit var accountAvailability: AccountAvailability

    private lateinit var viewModel: ChooseInternalEmailViewModel

    @Before
    fun beforeEveryTest() {
        accountAvailability = AccountAvailability(userRepository, domainRepository)
    }

    @Test
    fun `domains are loaded correctly`() = coroutinesTest {
        // GIVEN
        viewModel = ChooseInternalEmailViewModel(accountAvailability, observabilityManager)
        // WHEN
        viewModel.state.test {
            // THEN
            assertIs<State.Idle>(awaitItem())
            assertIs<State.Processing>(awaitItem())
            val domains = (awaitItem() as State.Ready).domains
            assertEquals(listOf("protonmail.com", "protonmail.ch"), domains)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `domains loading non-retryable error`() = coroutinesTest {
        // GIVEN
        coEvery { domainRepository.getAvailableDomains(any()) } throws ApiException(
            ApiResult.Error.Http(
                httpCode = 404,
                message = "Error"
            )
        )
        viewModel = ChooseInternalEmailViewModel(accountAvailability, observabilityManager)
        // WHEN
        viewModel.state.test {
            // THEN
            assertIs<State.Idle>(awaitItem())
            assertIs<State.Processing>(awaitItem())
            assertIs<State.Error>(awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `check username success`() = coroutinesTest {
        // GIVEN
        val testUsername = "test-username"
        val testDomain = "test-domain"
        val testEmail = "$testUsername@$testDomain"
        coEvery { userRepository.checkUsernameAvailable(any(), testEmail) } returns Unit
        viewModel = ChooseInternalEmailViewModel(accountAvailability, observabilityManager)
        viewModel.state.test {
            viewModel.checkUsername(testUsername, testDomain)
            // THEN
            assertIs<State.Idle>(awaitItem())
            assertIs<State.Processing>(awaitItem())
            assertIs<State.Ready>(awaitItem())
            assertIs<State.Processing>(awaitItem())
            val item = awaitItem() as State.Success
            assertEquals(testUsername, item.username)
            assertEquals(testDomain, item.domain)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `check username error`() = coroutinesTest {
        // GIVEN
        val testUsername = "test-username"
        val testDomain = "test-domain"
        val testEmail = "$testUsername@$testDomain"
        coEvery { userRepository.checkUsernameAvailable(any(), testEmail) } throws ApiException(
            ApiResult.Error.Http(
                httpCode = 123,
                "http error",
                ApiResult.Error.ProtonData(
                    code = 12106,
                    error = "username not available"
                )
            )
        )
        // WHEN
        viewModel = ChooseInternalEmailViewModel(accountAvailability, observabilityManager)
        viewModel.state.test {
            viewModel.checkUsername(testUsername, testDomain)
            // THEN
            assertIs<State.Idle>(awaitItem())
            assertIs<State.Processing>(awaitItem())
            assertIs<State.Ready>(awaitItem())
            assertIs<State.Processing>(awaitItem())
            assertIs<State.Error.Message>(awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `get domains is initially called, before checkUsername`() = coroutinesTest {
        // GIVEN
        val testUsername = "test-username"
        val testDomain = "test-domain"
        val testEmail = "$testUsername@$testDomain"
        coEvery { userRepository.checkUsernameAvailable(any(), testEmail) } returns Unit
        viewModel = ChooseInternalEmailViewModel(accountAvailability, observabilityManager)
        viewModel.state.test {
            viewModel.checkUsername(testUsername, testDomain)
            assertIs<State.Idle>(awaitItem())
            assertIs<State.Processing>(awaitItem())
            assertIs<State.Ready>(awaitItem())
            assertIs<State.Processing>(awaitItem())
            assertIs<State.Success>(awaitItem())
            cancelAndConsumeRemainingEvents()
        }
        // THEN
        coVerify(ordering = Ordering.ORDERED) {
            domainRepository.getAvailableDomains(any())
            userRepository.checkUsernameAvailable(any(), testEmail)
        }
    }

    @Test
    fun `fetchDomains observability`() = coroutinesTest {
        // GIVEN
        val dataSlot = slot<SignupFetchDomainsTotal>()
        coEvery { domainRepository.getAvailableDomains(any()) } coAnswers {
            result("getAvailableDomains") { listOf("domain") }
        }

        // WHEN
        viewModel = ChooseInternalEmailViewModel(accountAvailability, observabilityManager)
        viewModel.state.first { it is State.Ready } // wait for domains

        // THEN
        verify { observabilityManager.enqueue(capture(dataSlot), any()) }
        assertEquals(HttpApiStatus.http2xx, dataSlot.captured.Labels.status)
    }

    @Test
    fun `checkUsername observability`() = coroutinesTest {
        val dataSlot = slot<SignupUsernameAvailabilityTotal>()

        // WHEN
        viewModel = ChooseInternalEmailViewModel(accountAvailability, observabilityManager)
        viewModel.checkUsername("test-user", "proton.test").join()

        // THEN
        verify { observabilityManager.enqueue(capture(dataSlot), any()) }
        assertEquals(UsernameAvailabilityStatus.http2xx, dataSlot.captured.Labels.status)
    }
}
