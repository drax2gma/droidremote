package hu.gmz.droidremote

import hu.gmz.droidremote.plan.StartPlan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPlanTest {

    private val own = "hu.gmz.droidremote"

    @Test
    fun needsHomeWhenOwnUiIsInFront() {
        assertTrue(StartPlan.needsHome(own, own))
    }

    @Test
    fun doesNotHomeWhenAnotherAppIsInFront() {
        assertFalse(StartPlan.needsHome("com.android.settings", own))
    }

    @Test
    fun doesNotHomeWhenThereIsNoObservation() {
        assertFalse(StartPlan.needsHome(null, own))
    }
}
