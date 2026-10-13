package dev.fusion.cast.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpnpParsingTest {

    private val description = """
        <root><device>
          <friendlyName>Living Room TV</friendlyName>
          <serviceList>
            <service>
              <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
              <controlURL>/rc/control</controlURL>
            </service>
            <service>
              <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
              <controlURL>/avt/control</controlURL>
            </service>
          </serviceList>
        </device></root>
    """.trimIndent()

    @Test fun headerLookupIsCaseInsensitive() {
        val response = "HTTP/1.1 200 OK\r\nlocation: http://10.0.0.5:8200/desc.xml\r\nST: x\r\n"
        assertEquals("http://10.0.0.5:8200/desc.xml", UpnpParsing.header(response, "LOCATION"))
    }

    @Test fun picksAvTransportNotRenderingControl() {
        assertEquals("/avt/control", UpnpParsing.avTransportControlPath(description))
    }

    @Test fun deviceWithoutAvTransportReturnsNull() {
        assertNull(UpnpParsing.avTransportControlPath("<root><device><serviceList/></device></root>"))
    }

    @Test fun relativeControlUrlResolvesAgainstLocation() {
        assertEquals(
            "http://10.0.0.5:8200/avt/control",
            UpnpParsing.resolve("http://10.0.0.5:8200/desc.xml", "/avt/control")
        )
    }

    @Test fun friendlyNameIsExtracted() {
        assertEquals("Living Room TV", UpnpParsing.tagText(description, "friendlyName"))
    }

    @Test fun urlsAreEscapedIntoSoap() {
        val body = UpnpParsing.soapEnvelope(
            "SetAVTransportURI", "<CurrentURI>${UpnpParsing.xmlEscape("http://h/a?x=1&y=2")}</CurrentURI>"
        )
        assertEquals(true, body.contains("x=1&amp;y=2"))
        assertEquals(true, body.contains("urn:schemas-upnp-org:service:AVTransport:1"))
    }
}
