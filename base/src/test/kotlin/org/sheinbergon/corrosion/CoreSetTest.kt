package org.sheinbergon.corrosion

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.amshove.kluent.shouldThrow
import org.apache.commons.lang3.math.NumberUtils
import org.junit.jupiter.api.Test
import org.sheinbergon.corrosion.util.CoreSetException
import java.util.*

class CoreSetTest {

    @Test
    fun `Illegal core-set specification - invalid range`() {
        { CoreSet.from("10-2") } shouldThrow CoreSetException::class
    }

    @Test
    fun `Illegal core-set specification - invalid range, mixed specifications`() {
        { CoreSet.from("5,10-2") } shouldThrow CoreSetException::class
    }

    @Test
    fun `Illegal core-set specification - negative value`() {
        { CoreSet.from(NumberUtils.LONG_MINUS_ONE) } shouldThrow CoreSetException::class
    }

    @Test
    fun `Illegal core-set specification - rubbish`() {
        { CoreSet.from("oxidise") } shouldThrow CoreSetException::class
    }

    @Test
    fun `Empty core set behavior`() {
        CoreSet.EMPTY.mask() shouldBeEqualTo NumberUtils.LONG_MINUS_ONE
        CoreSet.EMPTY.toString() shouldBeEqualTo NumberUtils.LONG_MINUS_ONE.toString()
    }

    @Test
    fun `Core set equality`() {
        val cs1 = CoreSet.from("0-1")
        val cs2 = CoreSet.from(5)
        val cs3 = CoreSet.from("0-1")
        cs1 shouldNotBeEqualTo null
        cs1 shouldNotBeEqualTo cs2
        cs1 shouldNotBeEqualTo this
        cs1 shouldBeEqualTo cs3
        cs1 shouldBeEqualTo cs1
    }

    @Test
    fun `Core set hashCode`() {
        val cs1 = CoreSet.from("0-1")
        val cs2 = CoreSet.from(5)
        cs1.hashCode() shouldBeEqualTo Objects.hashCode(3)
        cs2.hashCode() shouldBeEqualTo Objects.hashCode(5)
        cs1.hashCode() shouldNotBeEqualTo cs2.hashCode()
    }
}