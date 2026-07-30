import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class main {
    private static final Map<String, BankAccount> accounts = new LinkedHashMap<>();
    private static int nextAccountNumber = 1;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", main::handleRequest);
        server.setExecutor(null);
        server.start();

        System.out.println("Banking app is running at http://localhost:8080");
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if (path.equals("/")) {
            serveFile(exchange, "index.html", "text/html; charset=UTF-8");
            return;
        }

        if (path.equals("/styles.css")) {
            serveFile(exchange, "styles.css", "text/css; charset=UTF-8");
            return;
        }

        if (path.equals("/app.js")) {
            serveFile(exchange, "app.js", "application/javascript; charset=UTF-8");
            return;
        }

        if (path.startsWith("/api/accounts")) {
            handleApi(exchange);
            return;
        }

        sendJson(exchange, 404, "{\"message\":\"Not found\"}");
    }

    private static void handleApi(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");

        if ("GET".equals(exchange.getRequestMethod()) && path.equals("/api/accounts")) {
            sendJson(exchange, 200, buildAccountsResponse());
            return;
        }

        if ("POST".equals(exchange.getRequestMethod()) && path.equals("/api/accounts")) {
            createAccount(exchange);
            return;
        }

        if (parts.length == 4 && "GET".equals(exchange.getRequestMethod())) {
            BankAccount account = accounts.get(parts[3]);
            if (account == null) {
                sendJson(exchange, 404, "{\"message\":\"Account not found\"}");
                return;
            }
            sendJson(exchange, 200, buildAccountResponse(account));
            return;
        }

        if (parts.length == 4 && "DELETE".equals(exchange.getRequestMethod())) {
            String accountId = parts[3];
            BankAccount removed = accounts.remove(accountId);
            if (removed == null) {
                sendJson(exchange, 404, "{\"message\":\"Account not found\"}");
                return;
            }
            sendJson(exchange, 200, "{\"message\":\"Account closed\"}");
            return;
        }

        if (parts.length == 5 && "POST".equals(exchange.getRequestMethod())) {
            String accountId = parts[3];
            BankAccount account = accounts.get(accountId);
            if (account == null) {
                sendJson(exchange, 404, "{\"message\":\"Account not found\"}");
                return;
            }

            Map<String, String> params = readFormData(exchange);
            double amount;
            try {
                amount = Double.parseDouble(params.getOrDefault("amount", "0"));
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, "{\"message\":\"Amount must be a valid number\"}");
                return;
            }

            if (parts[4].equals("deposit")) {
                if (amount <= 0) {
                    sendJson(exchange, 400, "{\"message\":\"Amount must be greater than zero\"}");
                    return;
                }
                account.deposit(amount);
                sendJson(exchange, 200, buildAccountResponse(account));
                return;
            }

            if (parts[4].equals("withdraw")) {
                if (amount <= 0) {
                    sendJson(exchange, 400, "{\"message\":\"Amount must be greater than zero\"}");
                    return;
                }
                if (!account.withdraw(amount)) {
                    sendJson(exchange, 400, "{\"message\":\"Insufficient funds\"}");
                    return;
                }
                sendJson(exchange, 200, buildAccountResponse(account));
                return;
            }
        }

        sendJson(exchange, 404, "{\"message\":\"Route not found\"}");
    }

    private static void createAccount(HttpExchange exchange) throws IOException {
        Map<String, String> params = readFormData(exchange);
        String ownerName = params.getOrDefault("ownerName", "").trim();

        if (ownerName.isEmpty()) {
            sendJson(exchange, 400, "{\"message\":\"Name cannot be empty\"}");
            return;
        }

        String accountId = "ACC" + nextAccountNumber++;
        BankAccount account = new BankAccount(accountId, ownerName);
        accounts.put(accountId, account);

        sendJson(exchange, 201, buildAccountResponse(account));
    }

    private static Map<String, String> readFormData(HttpExchange exchange) throws IOException {
        InputStream inputStream = exchange.getRequestBody();
        String body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = new LinkedHashMap<>();

        if (body.isEmpty()) {
            return params;
        }

        for (String entry : body.split("&")) {
            String[] pair = entry.split("=", 2);
            if (pair.length == 2) {
                params.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        }

        return params;
    }

    private static void serveFile(HttpExchange exchange, String fileName, String contentType) throws IOException {
        Path path = Paths.get(System.getProperty("user.dir"), fileName);
        if (!Files.exists(path)) {
            path = Paths.get(".").resolve(fileName).normalize();
        }

        byte[] data = Files.readAllBytes(path);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(data);
        }
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(data);
        }
    }

    private static String buildAccountsResponse() {
        double totalBalance = accounts.values().stream().mapToDouble(BankAccount::getBalance).sum();
        List<BankAccount> accountList = new ArrayList<>(accounts.values());

        StringBuilder builder = new StringBuilder();
        builder.append("{\"accountCount\":")
                .append(accounts.size())
                .append(",\"totalBalance\":")
                .append(String.format(Locale.US, "%.2f", totalBalance))
                .append(",\"accounts\":[");

        for (int i = 0; i < accountList.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(buildAccountJson(accountList.get(i)));
        }

        builder.append("]}");
        return builder.toString();
    }

    private static String buildAccountResponse(BankAccount account) {
        return "{\"account\":" + buildAccountJson(account) + "}";
    }

    private static String buildAccountJson(BankAccount account) {
        return "{\"id\":\"" + account.getId() + "\",\"ownerName\":\"" + escapeJson(account.getOwnerName())
                + "\",\"balance\":" + String.format(Locale.US, "%.2f", account.getBalance()) + "}";
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class BankAccount {
        private final String id;
        private final String ownerName;
        private double balance;

        public BankAccount(String id, String ownerName) {
            this.id = id;
            this.ownerName = ownerName;
        }

        public void deposit(double amount) {
            balance += amount;
        }

        public boolean withdraw(double amount) {
            if (balance < amount) {
                return false;
            }
            balance -= amount;
            return true;
        }

        public String getId() {
            return id;
        }

        public String getOwnerName() {
            return ownerName;
        }

        public double getBalance() {
            return balance;
        }
    }
}
