/*
* Copyright (c) 2020 Proton Technologies AG
*
* This file is part of ProtonMail.
*
* ProtonMail is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* ProtonMail is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with ProtonMail. If not, see https://www.gnu.org/licenses/.
*/

package me.proton.core.test.android.instrumented.builders

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.PerformException
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnHolderItem
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers
import me.proton.core.test.android.instrumented.ui.Actions.clickOnMatchedDescendant
import org.hamcrest.Matcher
import org.hamcrest.core.AllOf

/**
 * Builder like class that simplifies syntax for actions and verifications on [RecyclerView.ViewHolder].
 */
class OnRecyclerView : ConditionWatcher {
    private var position: Int? = null
    private val matchers: java.util.ArrayList<Matcher<View>> = arrayListOf()
    private var viewHolderMatcher: Matcher<RecyclerView.ViewHolder>? = null
    private var itemChildViewMatcher: Matcher<View>? = null

    /** [ViewInteraction] for the [RecyclerView] instance. **/
    private fun viewInteraction(
        viewAssertion: ViewAssertion = ViewAssertions.matches(ViewMatchers.isDisplayed())
    ): ViewInteraction {
        waitForCondition({ onView(viewMatcher()).check(viewAssertion) })
        return onView(viewMatcher())
    }

    /** Final matcher for the view. **/
    private fun viewMatcher(): Matcher<View> = AllOf.allOf(matchers)

    /** Matcher wrappers **/
    fun withId(id: Int) = apply {
        matchers.add(ViewMatchers.withId(id))
    }

    fun withText(text: String) = apply {
        matchers.add(ViewMatchers.withText(text))
    }

    /** [RecyclerViewActions] **/
    fun click() = apply {
        perform(ViewActions.click())
    }

    fun doubleClick() = apply {
        perform(ViewActions.doubleClick())
    }

    fun longClick() = apply {
        perform(ViewActions.longClick())
    }

    fun swipeDown() = apply {
        perform(ViewActions.swipeDown())
    }

    fun swipeLeft() = apply {
        perform(ViewActions.swipeLeft())
    }

    fun swipeRight() = apply {
        perform(ViewActions.swipeRight())
    }

    fun swipeUp() = apply {
        perform(ViewActions.swipeUp())
    }

    fun waitUntilGone() = apply {
        waitForCondition({ viewInteraction().check(ViewAssertions.doesNotExist()) })
    }

    fun scrollToHolder(viewHolderMatcher: Matcher<RecyclerView.ViewHolder>) = apply {
        viewInteraction().perform(RecyclerViewActions.scrollToHolder(viewHolderMatcher))
    }

    /** Wrapper for [RecyclerViewActions.actionOnHolderItem]. **/
    fun onHolderItem(viewHolderMatcher: Matcher<RecyclerView.ViewHolder>) = apply {
        this.viewHolderMatcher = viewHolderMatcher
    }

    /** Wrapper for [RecyclerViewActions.actionOnItemAtPosition]. **/
    fun onItemAtPosition(position: Int) = apply {
        this.position = position
    }

    /** Points to perform an action on [RecyclerView.ViewHolder] child or descendant. **/
    fun onItemChildView(view: OnView) = apply {
        itemChildViewMatcher = view.viewMatcher()
    }

    /** Performs action on [RecyclerView] based on action defined by [OnRecyclerView.Builder]. **/
    private fun perform(viewAction: ViewAction): ViewInteraction {
        itemChildViewMatcher?.let {
            return viewInteraction().perform(clickOnMatchedDescendant(itemChildViewMatcher!!))
        }
        viewHolderMatcher?.let {
            return viewInteraction().perform(actionOnHolderItem(viewHolderMatcher, viewAction))
        }
        position?.let {
            return viewInteraction()
                .perform(
                    actionOnItemAtPosition<RecyclerView.ViewHolder?>(
                        position!!,
                        viewAction
                    )
                )
        }

        throw PerformException
            .Builder()
            .withActionDescription(
                "Unable to perform RecyclerView action when viewHolderMatcher, itemChildViewMatcher and " +
                    "position values are null! viewHolderMatcher, itemChildViewMatcher or position must be provided."
            )
            .build()
    }
}
