import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CoffeeServer {
    private static final String ORDER_FILE = "orders.txt";
    private static final String MENU_FILE = "menu.txt";

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        System.out.println("☕ Coffee Shop Server running at http://localhost:8080");

        server.createContext("/", new HomeHandler());
        server.createContext("/order", new OrderHandler());
        server.createContext("/menu", new MenuHandler());
        server.createContext("/updateMenu", new UpdateMenuHandler());

        server.setExecutor(null);
        server.start();
    }

    // โหลดเมนูจากไฟล์
    static List<String[]> loadMenu() throws IOException {
        List<String[]> menu = new ArrayList<>();
        File f = new File(MENU_FILE);
        if (!f.exists()) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(MENU_FILE))) {
                pw.println("ลาเต้,65");
                pw.println("คาปูชิโน่,70");
                pw.println("เอสเพรสโซ่,60");
            }
        }
        try (BufferedReader br = new BufferedReader(new FileReader(MENU_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", 2);
                if (parts.length == 2) menu.add(parts);
            }
        }
        return menu;
    }

    // เขียนเมนูกลับลงไฟล์
    static void saveMenu(List<String[]> menu) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(MENU_FILE))) {
            for (String[] item : menu) {
                pw.println(item[0] + "," + item[1]);
            }
        }
    }

    // หน้าแรก (สั่งกาแฟ)
    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            List<String[]> menu = loadMenu();

            StringBuilder options = new StringBuilder();
            for (String[] item : menu) {
                options.append(String.format("<option value='%s'>%s (%s บาท)</option>", 
                                             item[0], item[0], item[1]));
            }

            String html = """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8">
                <title>Bam Coffee House</title>
                <style>
                body{font-family:sans-serif;background:#f8f5f2;margin:0;color:#3e2723;}
                header{background:#6d4c41;color:white;text-align:center;padding:20px;}
                main{max-width:700px;margin:20px auto;padding:20px;background:white;border-radius:10px;}
                input,select,button{padding:8px;margin:6px 0;width:100%;border-radius:6px;border:1px solid #ccc;}
                footer{background:#3e2723;color:white;text-align:center;padding:10px;margin-top:20px;}
                a{color:#6d4c41;}
                </style></head><body>
                <header><h1>Bam Coffee House</h1></header>
                <main>
                    <h2>สั่งกาแฟ</h2>
                    <form method='POST' action='/order'>
                        <label>ชื่อผู้สั่ง:</label>
                        <input type='text' name='name' required>
                        <label>เมนู:</label>
                        <select name='coffee'>
                """ + options + """
                        </select>
                        <label>จำนวน:</label>
                        <input type='number' name='quantity' value='1' min='1' required>
                        <button type='submit'>บันทึกการสั่งซื้อ</button>
                    </form>
                    <p><a href='/menu'>จัดการเมนู</a></p>
                </main>
                <footer>© 2025 Bam Coffee House</footer>
                </body></html>
                """;

            sendResponse(ex, html);
        }
    }

    // รับคำสั่งซื้อ
    static class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                String data = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseForm(data);

                try (PrintWriter out = new PrintWriter(new FileWriter(ORDER_FILE, true))) {
                    out.printf("%s | %s | %s แก้ว%n", 
                        params.get("name"), params.get("coffee"), params.get("quantity"));
                }

                sendResponse(ex, """
                    <html><body style='font-family:sans-serif;text-align:center'>
                    <h2>บันทึกคำสั่งซื้อแล้ว!</h2>
                    <p><a href='/'>กลับไปหน้าแรก</a></p>
                    </body></html>
                """);
            }
        }
    }

    // หน้าแก้ไขเมนู
    static class MenuHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            List<String[]> menu = loadMenu();
            StringBuilder table = new StringBuilder();

            for (int i = 0; i < menu.size(); i++) {
                String[] item = menu.get(i);
                table.append(String.format("""
                    <tr>
                        <td><input name='name%d' value='%s'></td>
                        <td><input name='price%d' value='%s'></td>
                    </tr>
                """, i, item[0], i, item[1]));
            }

            String html = """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8">
                <title>แก้ไขเมนู</title>
                <style>
                body{font-family:sans-serif;background:#f8f5f2;margin:0;color:#3e2723;}
                main{max-width:700px;margin:20px auto;padding:20px;background:white;border-radius:10px;}
                input,button{padding:6px;width:100%;border-radius:6px;margin:3px 0;}
                table{width:100%;border-collapse:collapse;}
                td{padding:6px;}
                th{text-align:left;}
                </style></head><body>
                <main>
                    <h2>แก้ไขเมนูกาแฟ</h2>
                    <form method='POST' action='/updateMenu'>
                        <table border='1'>
                            <tr><th>ชื่อเมนู</th><th>ราคา (บาท)</th></tr>
                """ + table + """
                        </table>
                        <button type='submit'>บันทึกการเปลี่ยนแปลง</button>
                    </form>
                    <p><a href='/'>กลับหน้าแรก</a></p>
                </main>
                </body></html>
                """;

            sendResponse(ex, html);
        }
    }

    // บันทึกเมนูที่แก้ไขแล้ว
    static class UpdateMenuHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                String data = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseForm(data);

                List<String[]> menu = new ArrayList<>();
                int i = 0;
                while (params.containsKey("name" + i)) {
                    String name = params.get("name" + i);
                    String price = params.get("price" + i);
                    if (!name.isEmpty() && !price.isEmpty())
                        menu.add(new String[]{name, price});
                    i++;
                }
                saveMenu(menu);

                sendResponse(ex, """
                    <html><body style='font-family:sans-serif;text-align:center'>
                    <h2>อัปเดตเมนูเรียบร้อย!</h2>
                    <p><a href='/'>กลับหน้าแรก</a></p>
                    </body></html>
                """);
            }
        }
    }

    // -------------------- Helper -----------------------
    static void sendResponse(HttpExchange ex, String response) throws IOException {
        ex.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    static Map<String, String> parseForm(String data) throws UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        for (String pair : data.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                map.put(URLDecoder.decode(parts[0], "UTF-8"), URLDecoder.decode(parts[1], "UTF-8"));
            }
        }
        return map;
    }
}
