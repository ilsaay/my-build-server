import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import javax.net.ssl.*;

public class Bilibili {
    
    private static final String CONFIG_FILE = "bilibili_config.ini";
    private static final String DEFAULT_HOST = "broadcastlv.chat.bilibili.com";
    
    private static String cookie = "";
    private static String roomId = "";
    private static String[] keywords = new String[0];
    private static String csrf = "";
    
    public static void main(String[] args) {
        System.out.println("=== Bilibili 弹幕机 (Java 1.6+ 兼容版) ===");
        initConfig();
        
        csrf = extractCsrf(cookie);
        if (csrf.isEmpty()) {
            System.err.println("[警告] Cookie 中未找到 bili_jct，发送弹幕功能可能失效！");
        }
        
        String[] danmuInfo = getDanmuInfo(roomId);
        String host = danmuInfo[0];
        String token = danmuInfo[1];
        System.out.println("[信息] 弹幕服务器: " + host);
        
        if (token.isEmpty()) {
            System.err.println("[致命错误] 未能获取到 WebSocket Token！");
            System.err.println("[排查建议] 1. 请检查 bilibili_config.ini 中的 roomid 是否为【长房间号】(真实房间号)，短号可能无法获取 token。");
            System.err.println("[排查建议] 2. 请检查 cookie 是否完整且有效 (必须包含 SESSDATA 和 bili_jct)。");
            System.err.println("[排查建议] 3. 确认该直播间当前处于开启状态。");
            System.exit(1);
        }
        System.out.println("[信息] 成功获取 Token，长度: " + token.length());
        
        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket socket = (SSLSocket) factory.createSocket(host, 443);
            socket.startHandshake();
            
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            
            byte[] nonce = new byte[16];
            new SecureRandom().nextBytes(nonce);
            String wsKey = encodeBase64(nonce);
            
            String handshake = "GET /sub HTTP/1.1\r\n" +
                    "Host: " + host + "\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: " + wsKey + "\r\n" +
                    "Sec-WebSocket-Version: 13\r\n" +
                    "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36\r\n" +
                    "Origin: https://live.bilibili.com\r\n\r\n";
            out.write(handshake.getBytes("UTF-8"));
            out.flush();
            
            // 手动逐字节读取 HTTP 响应头，避免 BufferedReader 吞掉二进制数据
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                headerBuf.write(b);
                byte[] buf = headerBuf.toByteArray();
                int len = buf.length;
                if (len >= 4 && buf[len-4] == '\r' && buf[len-3] == '\n' && buf[len-2] == '\r' && buf[len-1] == '\n') {
                    break;
                }
            }
            
            String headerStr = headerBuf.toString("UTF-8");
            if (!headerStr.contains("101")) {
                throw new RuntimeException("WebSocket 握手失败，服务器返回:\n" + headerStr);
            }
            System.out.println("[成功] WebSocket 连接已建立");
            
            // 发送认证包
            String authBody = "{\"uid\":0,\"roomid\":" + roomId + ",\"protover\":2,\"platform\":\"web\",\"type\":2,\"key\":\"" + token + "\"}";
            sendWsFrame(out, buildPacket(7, authBody.getBytes("UTF-8")));
            
            // 心跳线程
            final OutputStream finalOut = out;
            Thread heartbeatThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        while (true) {
                            Thread.sleep(30000);
                            sendWsFrame(finalOut, buildPacket(2, "".getBytes("UTF-8")));
                        }
                    } catch (Exception e) {
                        // 连接断开时正常退出
                    }
                }
            });
            heartbeatThread.setDaemon(true);
            heartbeatThread.start();
            
            // 控制台输入线程
            Thread consoleThread = new Thread(new Runnable() {
                public void run() {
                    Scanner scanner = new Scanner(System.in);
                    System.out.println("[提示] 在控制台输入内容并回车，可直接发送弹幕进行测试。输入 'exit' 退出。");
                    while (scanner.hasNextLine()) {
                        String msg = scanner.nextLine().trim();
                        if ("exit".equalsIgnoreCase(msg)) {
                            System.exit(0);
                        }
                        if (!msg.isEmpty()) {
                            sendDanmu(msg);
                        }
                    }
                }
            });
            consoleThread.setDaemon(true);
            consoleThread.start();
            
            // 主线程读取二进制帧
            DataInputStream dis = new DataInputStream(in);
            
            while (true) {
                int b1;
                try {
                    b1 = dis.readUnsignedByte();
                } catch (EOFException e) {
                    System.err.println("\n[错误] 连接被服务器主动关闭 (EOF)。");
                    System.err.println("[排查建议] 1. 请确保 bilibili_config.ini 中的 roomid 是【长房间号】(真实房间号)。");
                    System.err.println("[排查建议] 2. 请确保 cookie 有效且未过期。");
                    System.err.println("[排查建议] 3. 直播间可能已下播。");
                    break;
                }
                
                int b2 = dis.readUnsignedByte();
                boolean masked = (b2 & 0x80) != 0;
                int payloadLen = b2 & 0x7F;
                
                if (payloadLen == 126) {
                    payloadLen = dis.readUnsignedShort();
                } else if (payloadLen == 127) {
                    payloadLen = (int) dis.readLong();
                }
                
                byte[] maskKey = null;
                if (masked) {
                    maskKey = new byte[4];
                    dis.readFully(maskKey);
                }
                
                byte[] payload = new byte[payloadLen];
                dis.readFully(payload);
                
                if (masked) {
                    for (int i = 0; i < payloadLen; i++) {
                        payload[i] ^= maskKey[i % 4];
                    }
                }
                
                processBilibiliPacket(payload);
            }
            
        } catch (Exception e) {
            System.err.println("[致命错误] " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // ================= 核心业务逻辑 =================
    
    private static void processBilibiliPacket(byte[] data) {
        if (data.length < 16) return;
        int packetLen = readInt(data, 0);
        int headerLen = readShort(data, 4);
        int protoVer = readShort(data, 6);
        int operation = readInt(data, 8);
        
        if (operation == 5) {
            byte[] body = new byte[packetLen - headerLen];
            System.arraycopy(data, headerLen, body, 0, body.length);
            
            if (protoVer == 2) {
                try {
                    body = decompressZlib(body);
                    int offset = 0;
                    while (offset < body.length) {
                        int subLen = readInt(body, offset);
                        if (subLen <= 0 || offset + subLen > body.length) break;
                        byte[] subPacket = new byte[subLen];
                        System.arraycopy(body, offset, subPacket, 0, subLen);
                        processSinglePacket(subPacket);
                        offset += subLen;
                    }
                } catch (Exception e) {
                    processSinglePacket(data); 
                }
            } else {
                processSinglePacket(data);
            }
        }
    }
    
    private static void processSinglePacket(byte[] data) {
        if (data.length < 16) return;
        int packetLen = readInt(data, 0);
        int headerLen = readShort(data, 4);
        int operation = readInt(data, 8);
        
        if (operation == 5) {
            String json = new String(data, headerLen, packetLen - headerLen, Charset.forName("UTF-8"));
            if (json.contains("\"cmd\":\"DANMU_MSG\"")) {
                String content = extractDanmuContent(json);
                if (content != null && !content.isEmpty()) {
                    System.out.println("[弹幕] " + content);
                    checkKeywords(content);
                }
            }
        }
    }
    
    private static void checkKeywords(String content) {
        for (String kw : keywords) {
            if (content.contains(kw)) {
                String reply = "检测到关键词 [" + kw + "]，自动回复！";
                System.out.println("[触发] " + reply);
                sendDanmu(reply);
                break;
            }
        }
    }
    
    private static void sendDanmu(String msg) {
        try {
            URL url = new URL("https://api.live.bilibili.com/msg/send");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Cookie", cookie);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            String params = "roomid=" + roomId + 
                            "&msg=" + URLEncoder.encode(msg, "UTF-8") + 
                            "&color=16777215&fontsize=25&mode=1&rnd=" + (System.currentTimeMillis() / 1000) + 
                            "&csrf=" + csrf + "&csrf_token=" + csrf;
            
            OutputStream os = conn.getOutputStream();
            os.write(params.getBytes("UTF-8"));
            os.flush();
            os.close();
            
            int code = conn.getResponseCode();
            if (code == 200) {
                System.out.println("[发送成功] " + msg);
            } else {
                System.err.println("[发送失败] HTTP " + code);
            }
            conn.disconnect();
        } catch (Exception e) {
            System.err.println("[发送异常] " + e.getMessage());
        }
    }
    
    // ================= 配置与工具方法 =================
    
    private static void initConfig() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            try {
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
                pw.println("[config]");
                pw.println("# 请填入你的 Bilibili Cookie (必须包含 bili_jct)");
                pw.println("cookie=SESSDATA=your_sessdata; bili_jct=your_jct;");
                pw.println("# 直播间 ID (⚠️ 强烈建议使用长房间号/真实房间号，短号可能导致获取 token 失败)");
                pw.println("roomid=21452505");
                pw.println("# 触发关键词，用逗号分隔");
                pw.println("keywords=主播真帅,666,测试");
                pw.flush();
                pw.close();
                System.out.println("[提示] 已生成默认配置文件 " + CONFIG_FILE + "，请修改后重新运行。");
                System.exit(0);
            } catch (Exception e) {
                System.err.println("[错误] 无法创建配置文件: " + e.getMessage());
                System.exit(1);
            }
        }
        
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("cookie=")) cookie = line.substring(7).trim();
                else if (line.startsWith("roomid=")) roomId = line.substring(7).trim();
                else if (line.startsWith("keywords=")) {
                    String kws = line.substring(9).trim();
                    keywords = kws.split(",");
                    for(int i=0; i<keywords.length; i++) keywords[i] = keywords[i].trim();
                }
            }
            br.close();
        } catch (Exception e) {
            System.err.println("[错误] 读取配置失败: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static String[] getDanmuInfo(String roomId) {
        String host = DEFAULT_HOST;
        String token = "";
        try {
            URL url = new URL("https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo?id=" + roomId + "&type=0");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Cookie", cookie);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            
            if (conn.getResponseCode() != 200) {
                System.err.println("[警告] API 请求失败，HTTP " + conn.getResponseCode());
                return new String[]{host, token};
            }
            
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String json = sb.toString();
            
            int tokenIdx = json.indexOf("\"token\":\"");
            if (tokenIdx != -1) {
                int endIdx = json.indexOf("\"", tokenIdx + 9);
                if (endIdx != -1) {
                    token = json.substring(tokenIdx + 9, endIdx);
                }
            }
            
            int hostIdx = json.indexOf("\"host\":\"");
            if (hostIdx != -1) {
                int endHostIdx = json.indexOf("\"", hostIdx + 8);
                if (endHostIdx != -1) {
                    host = json.substring(hostIdx + 8, endHostIdx);
                }
            }
            
            // 如果没拿到 token，打印部分 JSON 帮助用户排查
            if (token.isEmpty()) {
                System.err.println("[调试] API 返回的 JSON 片段: " + json.substring(0, Math.min(300, json.length())));
            }
            
        } catch (Exception e) {
            System.err.println("[警告] 获取弹幕服务器信息失败: " + e.getMessage());
        }
        return new String[]{host, token};
    }
    
    private static String extractCsrf(String cookieStr) {
        int idx = cookieStr.indexOf("bili_jct=");
        if (idx != -1) {
            String sub = cookieStr.substring(idx + 9);
            int end = sub.indexOf(";");
            return end != -1 ? sub.substring(0, end) : sub;
        }
        return "";
    }
    
    private static String extractDanmuContent(String json) {
        int infoIdx = json.indexOf("\"info\":[");
        if (infoIdx == -1) return null;
        int start = json.indexOf("\"", infoIdx + 8) + 1;
        int end = json.indexOf("\"", start);
        if (start > 0 && end > start) {
            return json.substring(start, end);
        }
        return null;
    }
    
    // ================= 网络与协议底层实现 =================
    
    private static byte[] buildPacket(int operation, byte[] body) {
        int packetLen = 16 + body.length;
        byte[] packet = new byte[packetLen];
        writeInt(packet, 0, packetLen);
        writeShort(packet, 4, 16);
        writeShort(packet, 6, 1);
        writeInt(packet, 8, operation);
        writeInt(packet, 12, 1);
        System.arraycopy(body, 0, packet, 16, body.length);
        return packet;
    }
    
    private static void sendWsFrame(OutputStream out, byte[] payload) throws IOException {
        out.write(0x82);
        int len = payload.length;
        if (len < 126) {
            out.write(0x80 | len);
        } else if (len < 65536) {
            out.write(0x80 | 126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) out.write((len >> (8 * i)) & 0xFF);
        }
        
        byte[] mask = new byte[4];
        new SecureRandom().nextBytes(mask);
        out.write(mask);
        for (int i = 0; i < len; i++) {
            out.write(payload[i] ^ mask[i % 4]);
        }
        out.flush();
    }
    
    private static byte[] decompressZlib(byte[] data) throws DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!inflater.finished()) {
            int count = inflater.inflate(buf);
            if (count == 0) break;
            baos.write(buf, 0, count);
        }
        inflater.end();
        return baos.toByteArray();
    }
    
    private static final String BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static String encodeBase64(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i += 3) {
            int b = ((data[i] & 0xFF) << 16) | ((i + 1 < data.length ? data[i + 1] & 0xFF : 0) << 8) | (i + 2 < data.length ? data[i + 2] & 0xFF : 0);
            sb.append(BASE64_CHARS.charAt((b >> 18) & 0x3F));
            sb.append(BASE64_CHARS.charAt((b >> 12) & 0x3F));
            sb.append(i + 1 < data.length ? BASE64_CHARS.charAt((b >> 6) & 0x3F) : '=');
            sb.append(i + 2 < data.length ? BASE64_CHARS.charAt(b & 0x3F) : '=');
        }
        return sb.toString();
    }
    
    private static int readInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off+1] & 0xFF) << 16) | ((b[off+2] & 0xFF) << 8) | (b[off+3] & 0xFF);
    }
    private static int readShort(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off+1] & 0xFF);
    }
    private static void writeInt(byte[] b, int off, int val) {
        b[off] = (byte) ((val >> 24) & 0xFF);
        b[off+1] = (byte) ((val >> 16) & 0xFF);
        b[off+2] = (byte) ((val >> 8) & 0xFF);
        b[off+3] = (byte) (val & 0xFF);
    }
    private static void writeShort(byte[] b, int off, int val) {
        b[off] = (byte) ((val >> 8) & 0xFF);
        b[off+1] = (byte) (val & 0xFF);
    }
}
