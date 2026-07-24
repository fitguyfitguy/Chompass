package app.chompass.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyMeasurementCalculationTest {

  @Test
  fun waistToHipRatio_requiresBothSites() {
    val m = BodyMeasurement(waistCm = 90.0, hipsCm = 100.0)
    assertEquals(0.9, m.waistToHipRatio!!, 0.001)
    assertNull(BodyMeasurement(waistCm = 90.0).waistToHipRatio)
  }

  @Test
  fun waistToHeightRatio_usesProfileHeight() {
    val m = BodyMeasurement(waistCm = 90.0)
    assertEquals(0.5, m.waistToHeightRatio(180.0)!!, 0.001)
  }

  @Test
  fun usNavyMale_rejectsInvalidDomain() {
    val invalid = BodyMeasurement(neckCm = 40.0, waistCm = 38.0)
    assertNull(invalid.usNavyBodyFatPercent(Gender.MALE, 175.0))
  }

  @Test
  fun usNavyMale_reasonableEstimate() {
    val m = BodyMeasurement(neckCm = 38.0, waistCm = 85.0)
    val bf = m.usNavyBodyFatPercent(Gender.MALE, 175.0)
    requireNotNull(bf)
    assertEquals(true, bf in 5.0..35.0)
  }

  @Test
  fun usNavyFemale_requiresHips() {
    val m = BodyMeasurement(neckCm = 34.0, waistCm = 70.0, hipsCm = 95.0)
    val bf = m.usNavyBodyFatPercent(Gender.FEMALE, 165.0)
    requireNotNull(bf)
    assertEquals(true, bf in 10.0..45.0)
  }

  @Test
  fun wristFrame_classifiesByGenderCutoffs() {
    val male = BodyMeasurement(wristCm = 17.0)
    assertEquals(BodyMeasurement.FrameSize.MEDIUM, male.wristFrame(Gender.MALE, 175.0))
    val femaleSmall = BodyMeasurement(wristCm = 14.0)
    assertEquals(BodyMeasurement.FrameSize.SMALL, femaleSmall.wristFrame(Gender.FEMALE, 165.0))
  }
}
