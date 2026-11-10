package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Color;

import io.nayuki.qrcodegen.QrCode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Locale;

/**
 * A tiny LAN web server that lives only while the parent gate is on screen, so the parent can answer
 * the gate question on their phone instead of typing on the remote (opt-in, kids settings). The TV
 * shows the gate card with a QR of http://&lt;tv-ip&gt;:&lt;port&gt;/k?t=&lt;token&gt;; the phone loads the page,
 * answers, and the TV checks the answer through {@link KidSession#submitGateAnswer} — the same path
 * as the on-TV keypad, so the answer never leaves the TV and wrong answers earn the same cooldown.
 * Bounded: one daemon accept thread handled sequentially, 5 s socket timeouts, small header/body
 * caps, one token that dies after TTL and is rotated live. Nothing here reaches the internet.
 */
public final class KidGateServer {

    static final int BASE_PORT = 8791;
    static final int PORT_RANGE = 10;
    static final long TOKEN_TTL_MS = 5 * 60_000L;
    private static final int HEAD_CAP = 8 * 1024, BODY_CAP = 512, READ_TIMEOUT_MS = 5000;

    private final KidSession session;
    private final SecureRandom random = new SecureRandom();

    private ServerSocket socket;
    private Thread thread;
    private int port;
    private String ip;
    private volatile String token = "";
    private long tokenAt;

    public KidGateServer(KidSession session) {
        this.session = session;
    }

    /** Opens the port and starts the accept loop; safe to call again. False when no LAN address. */
    public synchronized boolean start() {
        if (socket != null && !socket.isClosed()) return true;
        ip = localIp();
        if (ip == null) return false;
        for (int attempt = 0; attempt < PORT_RANGE; attempt++) {
            try {
                socket = new ServerSocket(BASE_PORT + attempt);
                port = BASE_PORT + attempt;
                break;
            } catch (IOException busy) {
                socket = null;
            }
        }
        if (socket == null) return false;
        rotateToken();
        thread = new Thread(this::acceptLoop, "kids-gate");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    public synchronized void stop() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        socket = null;
        thread = null;
    }

    public boolean running() { return socket != null; }

    /** The URL the QR encodes, or null while the server is down. */
    public synchronized String url() {
        return socket == null ? null : "http://" + ip + ":" + port + "/k?t=" + token;
    }

    /** The parent re-scans after this: the QR on the card is rebuilt when the URL changes. */
    public synchronized void rotateToken() {
        byte[] raw = new byte[16];
        random.nextBytes(raw);
        StringBuilder hex = new StringBuilder(32);
        for (byte b : raw) hex.append(String.format(Locale.US, "%02x", b));
        token = hex.toString();
        tokenAt = System.currentTimeMillis();
    }

    public synchronized boolean tokenValid(String asked) {
        return socket != null && token.equals(asked)
                && System.currentTimeMillis() - tokenAt < TOKEN_TTL_MS;
    }

    /** Milliseconds left before the QR's token dies; 0 when expired or the server is down. */
    public synchronized long tokenExpiresIn() {
        if (socket == null) return 0;
        return Math.max(0, TOKEN_TTL_MS - (System.currentTimeMillis() - tokenAt));
    }

    // ---- the accept loop ---------------------------------------------------------------------------

    private void acceptLoop() {
        while (true) {
            ServerSocket mine;
            synchronized (this) {
                mine = socket;
            }
            if (mine == null) return;
            Socket sock;
            try {
                sock = mine.accept();
            } catch (IOException closed) {
                return;   // stop() closed the socket
            }
            try {
                sock.setSoTimeout(READ_TIMEOUT_MS);
                handle(sock);
            } catch (Exception ignored) {
            } finally {
                try {
                    sock.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** One request: read the head, then the body it declares, route it, answer, close. Anything odd → 404. */
    private void handle(Socket sock) throws IOException {
        InputStream in = sock.getInputStream();
        byte[] head = readUpTo(in, HEAD_CAP, true);
        Request probe = Request.parse(head, new byte[0]);
        if (probe == null) {
            respond(sock, 404, page("打不开", "请求有误，请回到电视重新扫码。"));
            return;
        }
        byte[] body = readUpTo(in, probe.contentLength(), false);
        Request req = Request.parse(head, body);
        if (req == null) {
            respond(sock, 404, page("打不开", "请求有误，请回到电视重新扫码。"));
            return;
        }
        if ("GET".equals(req.method) && req.path.equals("/k") && tokenValid(req.param("t"))) {
            respond(sock, 200, questionPage());
            return;
        }
        if ("POST".equals(req.method) && req.path.equals("/a") && tokenValid(req.param("t"))) {
            String value = req.param("v");
            if (value == null || value.length() == 0) {
                respond(sock, 200, questionPage());
                return;
            }
            KidSession.GateResult result = session.submitGateAnswer(value);
            if (result == KidSession.GateResult.OK) {
                respond(sock, 200, choicePage());
            } else if (result == KidSession.GateResult.WRONG) {
                long left = waitSeconds();
                respond(sock, 200, waitPage("答案不正确，请再想一道新题", left));
            } else {
                respond(sock, 200, waitPage("请稍等，冷却还没结束", waitSeconds()));
            }
            return;
        }
        // The choice also comes as a GET anchor: plain links never clip their label the way
        // <button> does on phones. The token in the URL is the capability anyway.
        if ((req.method.equals("POST") || req.method.equals("GET")) && req.path.equals("/c")
                && tokenValid(req.param("t"))) {
            String choice = req.param("c");
            if ("unlock".equals(choice)) {
                session.unlockTodayAsync();   // view work goes to the main thread, never here
                respond(sock, 200, page("已解锁", "今天可以在家长陪同下继续看了。"));
            } else if ("close".equals(choice)) {
                session.closeModeAsync();
                respond(sock, 200, page("已关闭", "儿童模式已关闭，电视回到普通桌面。"));
            } else {
                respond(sock, 404, page("打不开", "请求有误，请回到电视重新扫码。"));
            }
            return;
        }
        respond(sock, 404, page("打不开", "链接已失效或地址有误，请回到电视重新扫码。"));
    }

    private long waitSeconds() {
        long left = (session.gateCooldownUntil() - System.currentTimeMillis() + 999) / 1000;
        return Math.max(1, left);
    }

    // ---- the phone pages ---------------------------------------------------------------------------

    private String questionPage() {
        KidQuiz.Question q = session.gateQuestion();
        long left = session.gateCooldownUntil() - System.currentTimeMillis();
        String note = left > 0 ? "<p style=\"color:#E57373\">请等 " + waitSeconds() + " 秒后再提交</p>" : "";
        return page("家长验证",
                "<p style=\"color:#8D9BA7\">请家长计算，孩子不用试啦</p>"
                + "<h1 style=\"font-size:42px;margin:18px 0\">" + esc(q.text) + " = ?</h1>" + note
                + "<form method=\"post\" action=\"/a\">"
                + "<input type=\"hidden\" name=\"t\" value=\"" + token + "\">"
                + "<input name=\"v\" inputmode=\"numeric\" autocomplete=\"off\" style=\"font-size:26px;width:70%;padding:12px;text-align:center;border:1px solid #53616B;border-radius:10px\" placeholder=\"输入结果\">"
                + "<br><button style=\"font-size:22px;padding:14px 56px;margin-top:22px;background:#33444F;color:#fff;border:0;border-radius:12px;line-height:1.5\">提交</button>"
                + "</form>");
    }

    private String waitPage(String line, long seconds) {
        long capped = Math.min(600, seconds);
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta http-equiv=\"refresh\" content=\"" + capped + "; url=/k?t=" + token + "\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>家长验证</title></head>"
                + "<body style=\"font-family:-apple-system,sans-serif;background:#101418;color:#E4E8EB;text-align:center;padding:60px 24px\">"
                + "<h2>" + esc(line) + "</h2>"
                + "<p style=\"color:#8D9BA7\">" + capped + " 秒后自动刷新到新题目</p></body></html>";
    }

    private String choicePage() {
        return page("验证通过",
                "<p style=\"color:#9AD6A8;font-size:22px\">验证通过，接下来想怎么做？</p>"
                + "<a href=\"/c?t=" + token + "&c=unlock\" style=\"display:block;width:80%;margin:26px auto 0;padding:18px 12px;font-size:20px;background:#33444F;color:#fff;border-radius:12px;text-decoration:none\">今日解锁（家长陪同）</a>"
                + "<a href=\"/c?t=" + token + "&c=close\" style=\"display:block;width:80%;margin:16px auto 0;padding:18px 12px;font-size:20px;background:#202A31;color:#E4E8EB;border:1px solid #53616B;border-radius:12px;text-decoration:none\">关闭儿童模式</a>");
    }

    private static String page(String title, String body) {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + esc(title) + "</title></head>"
                + "<body style=\"font-family:-apple-system,sans-serif;background:#101418;color:#E4E8EB;text-align:center;padding:48px 24px\">"
                + "<h2 style=\"margin:12px 0\">" + esc(title) + "</h2>" + body + "</body></html>";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ---- raw HTTP, kept small on purpose -------------------------------------------------------------

    /** The parsed first line, headers and body of one request; null when malformed. */
    static class Request {
        final String method, path, head, body;
        private final String all;

        Request(String method, String path, String head, String body) {
            this.method = method;
            this.path = path;
            this.head = head;
            this.body = body;
            this.all = head + "\r\n\r\n" + body;
        }

        String param(String name) {
            String key = name + "=";
            int bodyStart = head.length() + 4;
            for (int at = all.indexOf(key); at >= 0; at = all.indexOf(key, at + 1)) {
                // Only a real key position: body start, a query (?), a separator (&) or a line start.
                // Otherwise "ct=" in a header would read as "t=".
                boolean atKey = at == bodyStart || all.charAt(at - 1) == '&'
                        || all.charAt(at - 1) == '?' || all.charAt(at - 1) == '\n';
                if (!atKey) continue;
                int start = at + key.length(), end = start;
                while (end < all.length() && all.charAt(end) != '&' && all.charAt(end) != '\r'
                        && all.charAt(end) != '\n' && all.charAt(end) != ' ') end++;
                if (end == start) continue;
                return urlDecode(all.substring(start, end));
            }
            return null;
        }

        /** Content-Length from the head, capped at BODY_CAP; 0 when absent or malformed. */
        int contentLength() {
            for (String line : head.split("\r\n")) {
                if (line.toLowerCase(Locale.US).startsWith("content-length:")) {
                    try {
                        return Math.max(0, Math.min(BODY_CAP, Integer.parseInt(line.substring(15).trim())));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return 0;
        }

        /**
         * The head as read (ending at the blank line) plus the body bytes that followed it, which
         * the caller read per the head's Content-Length. Null when the head is not a request.
         */
        static Request parse(byte[] headBytes, byte[] bodyBytes) {
            String head = new String(headBytes, StandardCharsets.ISO_8859_1);
            if (!head.endsWith("\r\n\r\n")) return null;
            head = head.substring(0, head.length() - 4);
            String[] lines = head.split("\r\n");
            if (lines.length == 0) return null;
            String[] first = lines[0].split(" ");
            if (first.length < 2) return null;
            String path = first[1].contains("?") ? first[1].substring(0, first[1].indexOf('?')) : first[1];
            return new Request(first[0], path, head, new String(bodyBytes, StandardCharsets.ISO_8859_1));
        }
    }

    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception broken) {   // IllegalArgumentException and the checked UnsupportedEncodingException
            return s;
        }
    }

    /** Reads at most {@code cap} bytes, stopping after the head's blank line when {@code headOnly}. */
    private static byte[] readUpTo(InputStream in, int cap, boolean headOnly) throws IOException {
        byte[] buf = new byte[cap];
        int at = 0;
        while (at < cap) {
            int b = in.read();
            if (b < 0) break;
            buf[at++] = (byte) b;
            if (headOnly && at >= 4 && buf[at - 4] == '\r' && buf[at - 3] == '\n'
                    && buf[at - 2] == '\r' && buf[at - 1] == '\n') break;
        }
        byte[] out = new byte[at];
        System.arraycopy(buf, 0, out, 0, at);
        return out;
    }

    private void respond(Socket sock, int status, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        OutputStream out = sock.getOutputStream();
        out.write(("HTTP/1.1 " + status + " OK\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    /** The TV's LAN IPv4 (site-local: 192.168.x.x / 10.x.x.x / 172.16–31.x.x), or null when offline. */
    static String localIp() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                Enumeration<InetAddress> addrs = ifaces.nextElement().getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress() && !addr.isLoopbackAddress())
                        return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ---- the QR the gate card shows ------------------------------------------------------------------

    /** A white-backed QR of {@code url} about {@code aboutPx} wide, quiet zone included. */
    public static Bitmap qrBitmap(String url, int aboutPx) {
        QrCode code = QrCode.encodeText(url, QrCode.Ecc.MEDIUM);
        int size = code.size + 8;   // 4-module quiet zone all around, on the white backing
        int scale = Math.max(2, aboutPx / size);
        int dim = size * scale;
        int[] pixels = new int[dim * dim];
        for (int y = 0; y < dim; y++) {
            for (int x = 0; x < dim; x++) {
                boolean dark = x >= 4 * scale && y >= 4 * scale
                        && x < (size - 4) * scale && y < (size - 4) * scale
                        && code.getModule((x - 4 * scale) / scale, (y - 4 * scale) / scale);
                pixels[y * dim + x] = dark ? Color.BLACK : Color.WHITE;
            }
        }
        return Bitmap.createBitmap(pixels, dim, dim, Bitmap.Config.ARGB_8888);
    }
}
