package com.odifek.mvi

import app.cash.turbine.test
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.Is.`is`
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.ExperimentalTime

class FeatureTest {

    private lateinit var feature: Feature<TestWish, TestAction, TestEffect, TestState, TestNews>

    private val initialState = TestState()

    private fun featureWithNewsPublisher(): Feature<TestWish, TestAction, TestEffect, TestState, TestNews> =
        Feature(
            initialState = TestState(),
            reducer = TestReducer(),
            wishToAction = WishToActionTransformer,
            actor = TestActor(),
            newsPublisher = SnackMessagePublisher(),
            coroutineScope = TestCoroutineScope()
        )
    private fun plainFeature(): Feature<TestWish, TestAction, TestEffect, TestState, TestNews> =  Feature(
        initialState = TestState(),
        reducer = TestReducer(),
        wishToAction = WishToActionTransformer,
        actor = TestActor(),
        coroutineScope = TestCoroutineScope()
    )

    @Before
    fun setUp() {

    }

    @After
    fun tearDown() {
    }

    @Test
    fun `initialState should contain defaults`() = runBlocking {
        feature = plainFeature()
        assertThat(feature.value, `is`(TestState(false, 0)))
    }

    @ExperimentalTime
    @Test
    fun `Wish1 triggers a loading state followed by success`() = runBlocking {
        feature = plainFeature()
        feature.test {
            assertThat(expectItem(), `is`(initialState))
            feature.invoke(TestWish.Wish1)
            assertThat(expectItem(), `is`(TestState(true)))
            assertThat(expectItem(), `is`(TestState(false, 1)))
        }
    }

    @ExperimentalTime
    @Test
    fun `Wish2 triggers a loading state followed by success`() = runBlocking {
        feature = plainFeature()
        feature.test {
            assertThat(expectItem(), `is`(initialState))
            feature.invoke(TestWish.Wish2)
            assertThat(expectItem(), `is`(TestState(true)))
            assertThat(expectItem(), `is`(TestState(false, 2)))
        }
    }

    @ExperimentalTime
    @Test
    fun `sequence of wishes should produce corresponding sequence of states`() = runBlocking {
        feature = plainFeature()
        feature.test {
            assertThat(expectItem(), `is`(initialState))
            feature.invoke(TestWish.Wish1)
            feature.invoke(TestWish.Wish2)
            assertThat(expectItem(), `is`(TestState(true)))
            assertThat(expectItem(), `is`(TestState(false, 1)))
            assertThat(expectItem(), `is`(TestState(true, 1)))
            assertThat(expectItem(), `is`(TestState(false, 3)))
        }
    }

    @ExperimentalTime
    @Test
    fun `published news gets emitted once`() = runBlocking {
        feature = featureWithNewsPublisher()

        feature.news.test {
            feature.invoke(TestWish.Wish1)
            assertThat(expectItem(), `is`(instanceOf(TestNews.Announce::class.java)))
            expectNoEvents()
        }
    }


}

sealed class TestWish {
    object Wish1 : TestWish()
    object Wish2 : TestWish()
}

sealed class TestAction {
    object Action1 : TestAction()
    object Action2 : TestAction()
}

sealed class TestEffect {
    object StartLoading : TestEffect()
    data class Success(val inc: Int) : TestEffect()
}

data class TestState(
    val loading: Boolean = false,
    val value: Int = 0
)

sealed class TestNews {
    data class Announce(val message: String) : TestNews()
}

class TestReducer : Reducer<TestState, TestEffect> {
    override fun invoke(state: TestState, effect: TestEffect): TestState = when (effect) {
        TestEffect.StartLoading -> state.copy(loading = true)
        is TestEffect.Success -> state.copy(loading = false, value = state.value + effect.inc)
    }
}

object WishToActionTransformer : (TestWish) -> TestAction {
    override fun invoke(wish: TestWish): TestAction = when (wish) {
        TestWish.Wish1 -> TestAction.Action1
        TestWish.Wish2 -> TestAction.Action2
    }
}

class TestActor : Actor<TestState, TestAction, TestEffect> {
    override fun invoke(state: TestState, action: TestAction): Flow<TestEffect> = when (action) {
        TestAction.Action1 -> {
            if (!state.loading) {
                flow<TestEffect> {
                    emit(TestEffect.Success(1))
                }.onStart { emit(TestEffect.StartLoading) }
            } else emptyFlow()
        }

        TestAction.Action2 -> if (!state.loading) {
            flow<TestEffect> {
                emit(TestEffect.Success(2))
            }.onStart { emit(TestEffect.StartLoading) }
        } else emptyFlow()
    }

}

class SnackMessagePublisher : NewsPublisher<TestAction, TestEffect, TestState, TestNews> {
    override fun invoke(action: TestAction, effect: TestEffect, state: TestState): TestNews? {
        return if (effect is TestEffect.Success) {
            if (state.value % 2 != 0) {
                val message = "Odd number, yaay!"
                TestNews.Announce(message)
            } else null
        } else null
    }

}