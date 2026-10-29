package app.chompass.models

import org.junit.Assert.assertNotNull
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
  fun rfmMale_knownGolden() {
    val m = BodyMeasurement(waistCm = 80.0)
    assertEquals(19.0, m.relativeFatMassPercent(Gender.MALE, 180.0)!!, 1e-9)
  }

  @Test
  fun rfmFemale_addsSexTerm() {
    val m = BodyMeasurement(waistCm = 80.0)
    assertEquals(31.0, m.relativeFatMassPercent(Gender.FEMALE, 180.0)!!, 1e-9)
  }

  @Test
  fun rfmOther_usesMaleCoefficient() {
    val m = BodyMeasurement(waistCm = 80.0)
    assertEquals(19.0, m.relativeFatMassPercent(Gender.OTHER, 180.0)!!, 1e-9)
  }

  @Test
  fun rfm_rejectsMissingOrOutOfRange() {
    assertNull(BodyMeasurement().relativeFatMassPercent(Gender.MALE, 180.0))
    assertNull(BodyMeasurement(waistCm = 0.0).relativeFatMassPercent(Gender.MALE, 180.0))
    assertNull(BodyMeasurement(waistCm = 80.0).relativeFatMassPercent(Gender.MALE, 0.0))
    assertNull(BodyMeasurement(waistCm = 400.0).relativeFatMassPercent(Gender.FEMALE, 165.0))
  }

  @Test
  fun wristFrame_classifiesByGenderCutoffs() {
    val male = BodyMeasurement(wristCm = 17.0)
    assertEquals(BodyMeasurement.FrameSize.MEDIUM, male.wristFrame(Gender.MALE, 175.0))
    val femaleSmall = BodyMeasurement(wristCm = 14.0)
    assertEquals(BodyMeasurement.FrameSize.SMALL, femaleSmall.wristFrame(Gender.FEMALE, 165.0))
  }

    // --- SMM-LEE (Lee et al. 2000 limb-circumference model) ---

    @Test
    fun `skeletal muscle estimate needs all three limb sites`() {
        val noThigh = BodyMeasurement(upperArmCm = 32.0, calfCm = 37.0)
        assertNull(noThigh.estimatedSkeletalMuscleMassKg(Gender.MALE, 30, 175.0))
        val all = BodyMeasurement(upperArmCm = 32.0, thighCm = 55.0, calfCm = 37.0)
        assertNotNull(all.estimatedSkeletalMuscleMassKg(Gender.MALE, 30, 175.0))
    }

    @Test
    fun `skeletal muscle estimate rejects impossible inputs and results`() {
        val base = BodyMeasurement(upperArmCm = 32.0, thighCm = 55.0, calfCm = 37.0)
        // Height must be a real positive length.
        assertNull(base.estimatedSkeletalMuscleMassKg(Gender.MALE, 30, 0.0))
        // A negative age is not a person.
        assertNull(base.estimatedSkeletalMuscleMassKg(Gender.MALE, -1, 175.0))
        // A non-positive girth is a parsing bug, not a limb.
        assertNull(
            BodyMeasurement(upperArmCm = 0.0, thighCm = 55.0, calfCm = 37.0)
                .estimatedSkeletalMuscleMassKg(Gender.MALE, 30, 175.0)
        )
        // Girths that no human carries push the model output past the 80 kg cap.
        val absurd = BodyMeasurement(upperArmCm = 80.0, thighCm = 90.0, calfCm = 80.0)
        assertNull(absurd.estimatedSkeletalMuscleMassKg(Gender.MALE, 30, 175.0))
    }

    @Test
    fun `gender other uses the female coefficient`() {
        val m = BodyMeasurement(upperArmCm = 27.0, thighCm = 50.0, calfCm = 33.0)
        val female = m.estimatedSkeletalMuscleMassKg(Gender.FEMALE, 35, 165.0)!!
        val other = m.estimatedSkeletalMuscleMassKg(Gender.OTHER, 35, 165.0)!!
        assertEquals(female, other, 1e-9)
        val male = m.estimatedSkeletalMuscleMassKg(Gender.MALE, 35, 165.0)!!
        assertEquals(2.4, male - female, 1e-9)
    }
}
