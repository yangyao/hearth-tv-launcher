package com.gathin.tvhome;

import io.nayuki.qrcodegen.QrCode;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class KidGateServerTest {

    // ---- Request.parse: the only parsing the LAN gate server trusts ----------------------------------

    @Test public void parsesGetWithQuery() {
        KidGateServer.Request r = KidGateServer.Request.parse(
                "GET /k?t=abc123 HTTP/1.1\r\nHost: 192.168.2.211:8791\r\n\r\n"
                        .getBytes(StandardCharsets.ISO_8859_1));
        assertNotNull(r);
        assertEquals("GET", r.method);
        assertEquals("/k", r.path);
        assertEquals("abc123", r.param("t"));
        assertNull(r.param("v"));
    }

    @Test public void parsesPostBodyParams() {
        KidGateServer.Request r = KidGateServer.Request.parse(
                "POST /a?t=abc123 HTTP/1.1\r\nContent-Length: 14\r\nContent-Type: application/x-www-form-urlencoded\r\n\r\nv=183&t=abc123"
                        .getBytes(StandardCharsets.ISO_8859_1));
        assertNotNull(r);
        assertEquals("POST", r.method);
        assertEquals("/a", r.path);
        assertEquals("183", r.param("v"));
        assertEquals("abc123", r.param("t"));
    }

    @Test public void paramOnlyMatchesRealKeyPositions() {   // "ct=" in a header must not read as "t="
        KidGateServer.Request r = KidGateServer.Request.parse(
                "POST /a HTTP/1.1\r\nX-Note: ct=decoy\r\nContent-Length: 3\r\n\r\nv=7"
                        .getBytes(StandardCharsets.ISO_8859_1));
        assertNotNull(r);
        assertEquals("7", r.param("v"));
        assertNull(r.param("t"));
    }

    @Test public void malformedHeadsAreRejected() {
        assertNull(KidGateServer.Request.parse("garbage without a blank line".getBytes(StandardCharsets.ISO_8859_1)));
        assertNull(KidGateServer.Request.parse("\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test public void bodyIsCapped() {
        StringBuilder body = new StringBuilder("v=");
        for (int i = 0; i < 4000; i++) body.append('9');
        String head = "POST /a HTTP/1.1\r\nContent-Length: 4002\r\n\r\n";
        KidGateServer.Request r = KidGateServer.Request.parse(
                (head + body).getBytes(StandardCharsets.ISO_8859_1));
        assertNotNull(r);
        assertTrue("body capped at " + r.body.length(), r.body.length() <= 512);
    }

    // ---- the QR: the vendored nayuki library encodes the gate URL -------------------------------------

    @Test public void qrEncodesTheGateUrl() {
        String url = "http://192.168.2.211:8791/k?t=0123456789abcdef0123456789abcdef";
        QrCode code = QrCode.encodeText(url, QrCode.Ecc.MEDIUM);
        assertTrue("unexpectedly small QR", code.size >= 21);
        int dark = 0;
        for (int y = 0; y < code.size; y++)
            for (int x = 0; x < code.size; x++)
                if (code.getModule(x, y)) dark++;
        assertTrue("expected a real pattern, got " + dark + " dark modules", dark > code.size * code.size / 10);
    }
}
