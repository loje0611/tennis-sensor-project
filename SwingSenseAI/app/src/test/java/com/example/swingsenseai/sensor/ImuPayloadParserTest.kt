package com.example.swingsenseai.sensor

import org.junit.Assert.*
import org.junit.Test

class ImuPayloadParserTest {

    @Test
    fun `valid 6-axis payload is parsed`() {
        val result = ImuPayloadParser.parseLine("1.0,2.0,3.0,4.0,5.0,6.0")
        assertNotNull(result)
        assertEquals(6, result!!.size)
        assertEquals(1.0f, result[0], 0.001f)
        assertEquals(6.0f, result[5], 0.001f)
    }

    @Test
    fun `payload with spaces is parsed`() {
        val result = ImuPayloadParser.parseLine("  1.0, 2.0, 3.0, 4.0, 5.0, 6.0  ")
        assertNotNull(result)
    }

    @Test
    fun `empty string returns null`() {
        assertNull(ImuPayloadParser.parseLine(""))
        assertNull(ImuPayloadParser.parseLine("   "))
    }

    @Test
    fun `error prefix returns null`() {
        assertNull(ImuPayloadParser.parseLine("ERR: sensor disconnected"))
        assertNull(ImuPayloadParser.parseLine("err: timeout"))
    }

    @Test
    fun `wrong number of axes returns null`() {
        assertNull(ImuPayloadParser.parseLine("1.0,2.0,3.0"))
        assertNull(ImuPayloadParser.parseLine("1.0,2.0,3.0,4.0,5.0,6.0,7.0"))
    }

    @Test
    fun `non-numeric value returns null`() {
        assertNull(ImuPayloadParser.parseLine("1.0,abc,3.0,4.0,5.0,6.0"))
    }

    @Test
    fun `negative values are parsed`() {
        val result = ImuPayloadParser.parseLine("-1.5,-2.3,0.0,-4.1,5.2,-6.7")
        assertNotNull(result)
        assertEquals(-1.5f, result!![0], 0.001f)
        assertEquals(-6.7f, result[5], 0.001f)
    }
}
