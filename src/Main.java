import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;

public class Main {

    // Java 6 没有 StandardCharsets，用 Charset.forName 兼容
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static void main(String[] args) throws IOException {
        int port = 25565;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new IntroHandler());
        server.setExecutor(null); // 使用默认执行器
        server.start();

        System.out.println("Web server started: http://localhost:" + port + "/");
        System.out.println("Press Ctrl+C to stop.");
    }

    static class IntroHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // 只处理根路径，其它返回 404
            if (!"/".equals(path) && !"/index.html".equals(path)) {
                byte[] notFound = "404 Not Found".getBytes(UTF8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(404, notFound.length);
                OutputStream os = exchange.getResponseBody();
                os.write(notFound);
                os.close();
                return;
            }

            String html = buildPage();
            byte[] data = html.getBytes(UTF8);

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, data.length);

            OutputStream os = exchange.getResponseBody();
            os.write(data);
            os.close();
        }

        private String buildPage() {
            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE html>\n");
            sb.append("<html lang=\"zh-CN\">\n");
            sb.append("<head>\n");
            sb.append("<meta charset=\"utf-8\">\n");
            sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
            sb.append("<title>个人介绍</title>\n");
            sb.append("<style>\n");
            sb.append("  * { margin: 0; padding: 0; box-sizing: border-box; }\n");
            sb.append("  body {\n");
            sb.append("    font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;\n");
            sb.append("    min-height: 100vh;\n");
            sb.append("    display: flex; align-items: center; justify-content: center;\n");
            sb.append("    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n");
            sb.append("    padding: 20px;\n");
            sb.append("  }\n");
            sb.append("  .card {\n");
            sb.append("    background: #fff;\n");
            sb.append("    max-width: 620px; width: 100%;\n");
            sb.append("    border-radius: 20px;\n");
            sb.append("    padding: 40px;\n");
            sb.append("    box-shadow: 0 20px 50px rgba(0,0,0,0.25);\n");
            sb.append("    animation: fadeIn 0.8s ease;\n");
            sb.append("  }\n");
            sb.append("  @keyframes fadeIn {\n");
            sb.append("    from { opacity: 0; transform: translateY(20px); }\n");
            sb.append("    to   { opacity: 1; transform: translateY(0); }\n");
            sb.append("  }\n");
            sb.append("  .avatar {\n");
            sb.append("    width: 96px; height: 96px; border-radius: 50%;\n");
            sb.append("    background: linear-gradient(135deg, #667eea, #764ba2);\n");
            sb.append("    color: #fff; font-size: 40px; font-weight: bold;\n");
            sb.append("    display: flex; align-items: center; justify-content: center;\n");
            sb.append("    margin: 0 auto 20px;\n");
            sb.append("  }\n");
            sb.append("  h1 { text-align: center; color: #2d3748; font-size: 28px; margin-bottom: 6px; }\n");
            sb.append("  .subtitle { text-align: center; color: #718096; margin-bottom: 28px; }\n");
            sb.append("  .section-title { color: #4a5568; font-size: 15px; font-weight: 600;\n");
            sb.append("    text-transform: uppercase; letter-spacing: 1px; margin: 22px 0 10px; }\n");
            sb.append("  p { color: #4a5568; line-height: 1.8; }\n");
            sb.append("  .tags { display: flex; flex-wrap: wrap; gap: 8px; }\n");
            sb.append("  .tag {\n");
            sb.append("    background: #edf2f7; color: #4a5568;\n");
            sb.append("    padding: 6px 14px; border-radius: 999px; font-size: 14px;\n");
            sb.append("  }\n");
            sb.append("  .contact { margin-top: 10px; }\n");
            sb.append("  .contact a { color: #667eea; text-decoration: none; }\n");
            sb.append("  .contact a:hover { text-decoration: underline; }\n");
            sb.append("  footer { text-align: center; color: #a0aec0; font-size: 13px; margin-top: 30px; }\n");
            sb.append("</style>\n");
            sb.append("</head>\n");
            sb.append("<body>\n");
            sb.append("  <div class=\"card\">\n");
            sb.append("    <div class=\"avatar\">M</div>\n");
            sb.append("    <h1>你好，我是 Ming</h1>\n");
            sb.append("    <p class=\"subtitle\">Java 后端开发 / 全栈爱好者</p>\n");

            sb.append("    <div class=\"section-title\">关于我</div>\n");
            sb.append("    <p>热爱编程，喜欢用简洁的代码解决复杂的问题。\n");
            sb.append("       平时专注于服务端开发，对网络编程、并发和系统设计有浓厚兴趣。\n");
            sb.append("       这个页面就是由一个纯 Java 标准库写的 Web 服务器渲染出来的。</p>\n");

            sb.append("    <div class=\"section-title\">技能</div>\n");
            sb.append("    <div class=\"tags\">\n");
            sb.append("      <span class=\"tag\">Java</span>\n");
            sb.append("      <span class=\"tag\">Spring Boot</span>\n");
            sb.append("      <span class=\"tag\">MySQL</span>\n");
            sb.append("      <span class=\"tag\">Redis</span>\n");
            sb.append("      <span class=\"tag\">Linux</span>\n");
            sb.append("      <span class=\"tag\">Docker</span>\n");
            sb.append("    </div>\n");

            sb.append("    <div class=\"section-title\">联系我</div>\n");
            sb.append("    <p class=\"contact\">\n");
            sb.append("      邮箱：<a href=\"mailto:ming@example.com\">ming@example.com</a><br>\n");
            sb.append("      GitHub：<a href=\"https://github.com/\" target=\"_blank\">github.com/ming</a>\n");
            sb.append("    </p>\n");

            sb.append("    <footer>© 2025 Ming · Powered by Pure Java</footer>\n");
            sb.append("  </div>\n");
            sb.append("</body>\n");
            sb.append("</html>\n");

            return sb.toString();
        }
    }
}
