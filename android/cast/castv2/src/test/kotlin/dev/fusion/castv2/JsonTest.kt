package dev.fusion.castv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    @Test
    fun parsesReceiverStatusAndExtractsTransportId() {
        val json = """
            {
              "requestId": 1,
              "status": {
                "applications": [
                  {
                    "appId": "CC1AD845",
                    "displayName": "Default Media Receiver",
                    "sessionId": "7E2FF513-CDF6-9A91-2B28-3E3DE7BAC174",
                    "transportId": "web-5",
                    "namespaces": [{"name": "urn:x-cast:com.google.cast.media"}]
                  }
                ],
                "volume": {"level": 1, "muted": false}
              },
              "type": "RECEIVER_STATUS"
            }
        """.trimIndent()
        val obj = Json.parseObject(json)
        assertEquals("RECEIVER_STATUS", obj.str("type"))
        assertEquals(1.0, obj.num("requestId"))
        val app = obj.obj("status")!!.arr("applications")!![0] as Map<String, Any?>
        assertEquals("CC1AD845", app.str("appId"))
        assertEquals("web-5", app.str("transportId"))
        assertEquals("7E2FF513-CDF6-9A91-2B28-3E3DE7BAC174", app.str("sessionId"))
        assertEquals(1.0, obj.obj("status")!!.obj("volume")!!.num("level"))
    }

    @Test
    fun parsesMediaStatusAndExtractsSessionId() {
        val json = """
            {"type":"MEDIA_STATUS","status":[{"mediaSessionId":42,"playerState":"PLAYING","currentTime":12.5}]}
        """.trimIndent()
        val obj = Json.parseObject(json)
        val first = obj.arr("status")!![0] as Map<String, Any?>
        assertEquals(42.0, first.num("mediaSessionId"))
        assertEquals("PLAYING", first.str("playerState"))
        assertEquals(12.5, first.num("currentTime"))
    }

    @Test
    fun parsesScalarsAndArrays() {
        val obj = Json.parseObject("""{"a":[1,2,3],"b":true,"c":null,"d":"hi","e":-1.5e2}""")
        assertEquals(listOf(1.0, 2.0, 3.0), obj.arr("a"))
        assertEquals(true, obj["b"])
        assertNull(obj["c"])
        assertEquals("hi", obj.str("d"))
        assertEquals(-150.0, obj.num("e"))
    }

    @Test
    fun handlesEscapedStrings() {
        val obj = Json.parseObject("""{"k":"a\"b\\c\ndA"}""")
        assertEquals("a\"b\\c\ndA", obj.str("k"))
    }

    @Test
    fun escapeRoundTripsThroughParser() {
        val tricky = "quote\" backslash\\ newline\n tab\t unicode é 😀"
        val json = """{"v":"${Json.escape(tricky)}"}"""
        assertEquals(tricky, Json.parseObject(json).str("v"))
    }

    @Test
    fun rejectsTrailingData() {
        assertTrue(runCatching { Json.parse("""{"a":1} garbage""") }.isFailure)
    }
}
