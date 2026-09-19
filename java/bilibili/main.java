import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class Main {

    // ============ 从配置文件读取的参数 ============
    static String COOKIE = "";
    static long ROOM_ID = 0;
    static String biliJct = "";
    static String uid = "";
    static long realRoomId = 0;
    static String token = "";
    static String host = "";
    static int port = 0;

    /** 关键词 -> 回复内容 */
    static final Map<String, String> KEYWORDS = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        // 1. 加载配置文件
        File configFile = locateConfigFile();
        if (configFile == null || !configFile.exists()) {
            System.err.println("找不到配置文件 bilibili_config.ini");
            System.err.println("请在 jar 所在目录创建该文件, 格式参考:");
            System.err.println("  SESSDATA=xxx");
            System.err.println("  bili_jct=xxx");
            System.err.println("  DedeUserID=xxx");
            System.err.println("  ROOM_ID=123456");
            System.err.println("  KEYWORD_你好=你好呀~");
            return;
        }
        System.out.println(">>> 加载配置文件: " + configFile.getAbsolutePath());
        loadConfig(configFile);

        // 2. 校验配置
        if (COOKIE.isEmpty() || ROOM_ID == 0) {
            System.err.println("配置不完整: SESSDATA/bili_jct/DedeUserID/ROOM_ID 必填");
            return;
        }
        System.out.println(">>> 房间号: " + ROOM_ID);
        System.out.println(">>> 关键词数量: " + KEYWORDS.size());

        // 3. 获取真实房间号
        getRealRoomId();
        System.out.println(">>> 真实房间号: " + realRoomId);

        // 4. 获取弹幕服务器信息
        getDanmuInfo();
        System.out.println(">>> 弹幕服务器: " + host + ":" + port);

        // 5. 连接WebSocket
        connectWebSocket();
    }

    // ==================== 配置加载 ====================

    /**
     * 定位配置文件: 优先 jar 所在目录, 其次当前工作目录
     */
    static File locateConfigFile() {
        List<File> candidates = new ArrayList<>();

        // 1. jar 所在目录
        try {
            File jar = new File(Main.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File dir = jar.isFile() ? jar.getParentFile() : jar;
            if (dir != null) {
                candidates.add(new File(dir, "bilibili_config.ini"));
            }
        } catch (Exception ignored) {}

        // 2. 当前工作目录
        candidates.add(new File("bilibili_config.ini"));
        candidates.add(new File(System.getProperty("user.dir"), "bilibili_config.ini"));

        for (File f : candidates) {
            if (f.exists() && f.isFile()) return f;
        }
        return null;
    }

    /**
     * 解析 ini 文件
     */
    static void loadConfig(File file) throws IOException {
        Map<String, String> kv = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                // 跳过空行和注释
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                // 去掉可能的引号
                if (val.length() >= 2 &&
                        ((val.startsWith("\"") && val.endsWith("\"")) ||
                         (val.startsWith("'") && val.endsWith("'")))) {
                    val = val.substring(1, val.length() - 1);
                }
                kv.put(key, val);
            }
        }

        // 组装 Cookie
        String sessdata = kv.getOrDefault("SESSDATA", "");
        biliJct        = kv.getOrDefault("bili_jct", "");
        uid            = kv.getOrDefault("DedeUserID", "");

        StringBuilder ck = new StringBuilder();
        if (!sessdata.isEmpty()) ck.append("SESSDATA=").append(sessdata).append("; ");
        if (!biliJct.isEmpty())  ck.append("bili_jct=").append(biliJct).append("; ");
        if (!uid.isEmpty())      ck.append("DedeUserID=").append(uid).append("; ");
        COOKIE = ck.toString().trim();

        // 房间号
        String roomStr = kv.getOrDefault("ROOM_ID", "0");
        try { ROOM_ID = Long.parseLong(roomStr); } catch (NumberFormatException e) { ROOM_ID = 0; }

        // 关键词: KEYWORD_xxx=yyy
        for (Map.Entry<String, String> e : kv.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("KEYWORD_")) {
                String keyword = k.substring("KEYWORD_".length());
                if (!keyword.isEmpty()) {
                    KEYWORDS.put(keyword, e.getValue());
                }
            }
        }
    }

    // ==================== HTTP 工具 ====================

    static String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", COOKIE);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        return readResponse(conn);
    }

    static String httpPost(String url, Map<String, String> params) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (body.length() > 0) body.append("&");
            body.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                .append("=")
                .append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Cookie", COOKIE);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        return readResponse(conn);
    }

    static String readResponse(HttpURLConnection conn) throws Exception {
        InputStream is = conn.getResponseCode() >= 400
                ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        is.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    // ==================== B站API ====================

    static void getRealRoomId() throws Exception {
        String url = "https://api.live.bilibili.com/room/v1/Room/room_init?id=" + ROOM_ID;
        String resp = httpGet(url);
        realRoomId = extractLong(resp, "\"room_id\":");
        if (realRoomId == 0) {
            throw new RuntimeException("获取真实房间号失败: " + resp);
        }
    }

    static void getDanmuInfo() throws Exception {
        String url = "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo?id="
                + realRoomId + "&type=0";
        String resp = httpGet(url);

        Matcher mHost = Pattern.compile("\"host\":\"([^\"]+)\"").matcher(resp);
        if (mHost.find()) host = mHost.group(1);
        Matcher mPort = Pattern.compile("\"wss_port\":(\\d+)").matcher(resp);
        if (mPort.find()) port = Integer.parseInt(mPort.group(1));
        Matcher mToken = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(resp);
        if (mToken.find()) token = mToken.group(1);

        if (host.isEmpty()) {
            host = "broadcastlv.chat.bilibili.com";
            port = 443;
        }
    }

    // ==================== WebSocket ====================

    static void connectWebSocket() throws Exception {
        Socket socket;
        if (port == 443) {
            javax.net.ssl.SSLSocketFactory factory =
                    (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
            javax.net.ssl.SSLSocket ssl = (javax.net.ssl.SSLSocket) factory.createSocket(host, port);
            ssl.startHandshake();
            socket = ssl;
        } else {
            socket = new Socket(host, port);
        }
        socket.setSoTimeout(0);
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();

        String key = Base64.getEncoder().encodeToString(
                UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.UTF_8));

        String handshake =
                "GET /sub HTTP/1.1\r\n" +
                "Host: " + host + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + key + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "Origin: https://live.bilibili.com\r\n" +
                "User-Agent: Mozilla/5.0\r\n" +
                "\r\n";
        out.write(handshake.getBytes(StandardCharsets.UTF_8));
        out.flush();

        // 读取握手响应
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            sb.append((char) c);
            if (sb.length() >= 4 && sb.substring(sb.length() - 4).equals("\r\n\r\n")) break;
        }
        System.out.println(">>> WebSocket 握手完成");

        // 发送认证包
        sendAuthPacket(out);
        System.out.println(">>> 已发送认证包, 等待弹幕...");

        // 心跳线程
        Thread heart = new Thread(() -> {
            try {
                while (!socket.isClosed()) {
                    Thread.sleep(30000);
                    sendHeartbeat(out);
                }
            } catch (Exception ignored) {}
        });
        heart.setDaemon(true);
        heart.start();

        // 主循环读消息
        while (true) {
            byte[] frame = readWsFrame(in);
            if (frame == null) break;
            handleBiliPacket(frame);
        }
    }

    static void sendAuthPacket(OutputStream out) throws Exception {
        String json = "{\"uid\":" + (uid.isEmpty() ? 0 : Long.parseLong(uid)) +
                ",\"roomid\":" + realRoomId +
                ",\"protover\":2,\"platform\":\"web\",\"type\":2,\"key\":\"" + token + "\"}";
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        byte[] packet = buildBiliPacket((short) 7, 1, payload);
        sendWsBinary(out, packet);
    }

    static void sendHeartbeat(OutputStream out) throws Exception {
        byte[] packet = buildBiliPacket((short) 2, 1, new byte[0]);
        sendWsBinary(out, packet);
    }

    // ==================== B站协议 ====================

    static byte[] buildBiliPacket(short action, int version, byte[] body) {
        int total = 16 + body.length;
        byte[] p = new byte[total];
        writeInt(p, 0, total);
        writeShort(p, 4, (short) 16);
        writeShort(p, 6, version);
        writeInt(p, 8, action);
        writeInt(p, 12, 1);
        System.arraycopy(body, 0, p, 16, body.length);
        return p;
    }

    static void writeInt(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24);
        b[off+1] = (byte) (v >>> 16);
        b[off+2] = (byte) (v >>> 8);
        b[off+3] = (byte) v;
    }
    static void writeShort(byte[] b, int off, short v) {
        b[off] = (byte) (v >>> 8);
        b[off+1] = (byte) v;
    }
    static int readInt(byte[] b, int off) {
        return ((b[off]&0xFF)<<24) | ((b[off+1]&0xFF)<<16) | ((b[off+2]&0xFF)<<8) | (b[off+3]&0xFF);
    }
    static short readShort(byte[] b, int off) {
        return (short)(((b[off]&0xFF)<<8) | (b[off+1]&0xFF));
    }

    // ==================== WebSocket 帧 ====================

    static void sendWsBinary(OutputStream out, byte[] payload) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0x82);
        int len = payload.length;
        if (len < 126) {
            bos.write(0x80 | len);
        } else if (len < 65536) {
            bos.write(0x80 | 126);
            bos.write((len >>> 8) & 0xFF);
            bos.write(len & 0xFF);
        } else {
            bos.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) bos.write((int)((long)len >>> (i*8)) & 0xFF);
        }
        byte[] mask = new byte[4];
        new Random().nextBytes(mask);
        bos.write(mask);
        for (int i = 0; i < len; i++) bos.write(payload[i] ^ mask[i % 4]);
        out.write(bos.toByteArray());
        out.flush();
    }

    static byte[] readWsFrame(InputStream in) throws Exception {
        int b1 = in.read();
        if (b1 == -1) return null;
        int b2 = in.read();
        int opcode = b1 & 0x0F;
        boolean masked = (b2 & 0x80) != 0;
        long len = b2 & 0x7F;
        if (len == 126) {
            len = (in.read() << 8) | in.read();
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | in.read();
        }
        byte[] mask = null;
        if (masked) {
            mask = new byte[4];
            readFully(in, mask);
        }
        byte[] payload = new byte[(int) len];
        readFully(in, payload);
        if (masked) {
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
        }
        if (opcode == 0x8) return null;
        if (opcode == 0x9) return new byte[0];
        return payload;
    }

    static void readFully(InputStream in, byte[] buf) throws Exception {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n == -1) throw new EOFException();
            off += n;
        }
    }

    // ==================== 消息处理 ====================

    static void handleBiliPacket(byte[] data) {
        if (data.length < 16) return;
        int offset = 0;
        while (offset + 16 <= data.length) {
            int packetLen = readInt(data, offset);
            short headerLen = readShort(data, offset + 4);
            short ver = readShort(data, offset + 6);
            int op = readInt(data, offset + 8);
            if (packetLen <= 0 || offset + packetLen > data.length) break;
            byte[] body = Arrays.copyOfRange(data, offset + headerLen, offset + packetLen);

            if (op == 5) {
                if (ver == 2) {
                    try {
                        byte[] unzipped = inflate(body);
                        handleBiliPacket(unzipped);
                    } catch (Exception ignored) {}
                } else {
                    handleMessage(new String(body, StandardCharsets.UTF_8));
                }
            } else if (op == 3) {
                int popularity = body.length >= 4 ? readInt(body, 0) : 0;
                System.out.println("[人气] " + popularity);
            } else if (op == 8) {
                System.out.println(">>> 认证成功, 开始接收弹幕");
            }
            offset += packetLen;
        }
    }

    static byte[] inflate(byte[] data) throws Exception {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!inflater.finished()) {
            int n = inflater.inflate(buf);
            if (n == 0) break;
            bos.write(buf, 0, n);
        }
        inflater.end();
        return bos.toByteArray();
    }

    static void handleMessage(String json) {
        try {
            String cmd = extractString(json, "\"cmd\":\"");
            if (cmd == null) return;

            if (cmd.startsWith("DANMU_MSG")) {
                String user = extractDanmuUser(json);
                String content = extractDanmuContent(json);
                System.out.println("[弹幕] " + user + ": " + content);

                // 关键词回复
                if (content != null) {
                    for (Map.Entry<String, String> e : KEYWORDS.entrySet()) {
                        if (content.contains(e.getKey())) {
                            sendDanmu(e.getValue());
                            break;
                        }
                    }
                }
            } else if (cmd.startsWith("SEND_GIFT")) {
                String user = extractString(json, "\"uname\":\"");
                String gift = extractString(json, "\"giftName\":\"");
                System.out.println("[礼物] " + user + " 送出 " + gift);
            } else if (cmd.startsWith("INTERACT_WORD")) {
                String user = extractString(json, "\"uname\":\"");
                System.out.println("[进入] " + user);
            }
        } catch (Exception ignored) {}
    }

    static String extractDanmuContent(String json) {
        Matcher m = Pattern.compile("\"info\":\\[\\[[^\\]]*\\],\"([^\"]*)\"").matcher(json);
        if (m.find()) return m.group(1);
        return null;
    }

    static String extractDanmuUser(String json) {
        Matcher m = Pattern.compile("\"info\":\\[\\[[^\\]]*\\],\"[^\"]*\",\\[[^\\]]*,\"([^\"]*)\"")
                .matcher(json);
        if (m.find()) return m.group(1);
        String u = extractString(json, "\"uname\":\"");
        return u == null ? "?" : u;
    }

    static String extractString(String json, String key) {
        int i = json.indexOf(key);
        if (i < 0) return null;
        int start = i + key.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    static long extractLong(String json, String key) {
        int i = json.indexOf(key);
        if (i < 0) return 0;
        int start = i + key.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == start) return 0;
        return Long.parseLong(json.substring(start, end));
    }

    // ==================== 发送弹幕 ====================

    static void sendDanmu(String msg) {
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("bubble", "0");
            params.put("msg", msg);
            params.put("color", "16777215");
            params.put("mode", "1");
            params.put("fontsize", "25");
            params.put("rnd", String.valueOf(System.currentTimeMillis() / 1000));
            params.put("roomid", String.valueOf(realRoomId));
            params.put("csrf", biliJct);
            params.put("csrf_token", biliJct);

            String resp = httpPost("https://api.live.bilibili.com/msg/send", params);
            System.out.println("[发送弹幕] " + msg + " -> " + resp);
        } catch (Exception e) {
            System.out.println("[发送弹幕失败] " + e.getMessage());
        }
    }
}
