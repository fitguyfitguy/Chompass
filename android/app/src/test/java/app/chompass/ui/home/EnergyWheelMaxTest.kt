package app.chompass.ui.home

import app.chompass.models.EnergyUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Energy wheel cap in display units. kcal keeps the long-standing 5000 cap
 * (Codeberg #104); kJ wheels must span the converted equivalent so kJ users
 * can dial the same meals kcal users can.
 */
class EnergyWheelMaxTest {
    @Test
    fun kcal_keeps5000() {
        assertEquals(5000, EnergyUnit.energyWheelMax(EnergyUnit.KCAL))
    }

    @Test
    fun kj_convertsTheCap() {
        assertEquals(20920, EnergyUnit.energyWheelMax(EnergyUnit.KJ))
    }
}
