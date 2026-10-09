package me.langyue.autotranslation.core.smoke;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.langyue.autotranslation.core.util.CoreLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A tiny local stand-in for an OpenAI compatible endpoint, used only by the automated start-up checks so that the
 * real HTTP code path can be exercised without any network access. It "translates" by prefixing every segment with
 * {@code TR| }. Uses only {@code java.base} and Gson.
 */
public final class FakeOpenAiServer implements AutoCloseable {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final ServerSocket socket;
    private final Thread thread;
    public final AtomicInteger requests = new AtomicInteger();
    public volatile String lastAuthorization = "";

    public FakeOpenAiServer() throws IOException {
        socket = new ServerSocket(0, 20, InetAddress.getLoopbackAddress());
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                serve();
            }
        }, "AutoTranslation-SmokeServer");
        thread.setDaemon(true);
        thread.start();
        CoreLog.info("[smoke-server] listening on " + socket.getLocalSocketAddress());
    }

    public String describe() {
        return "bound to " + socket.getLocalSocketAddress() + ", requests served: " + requests.get();
    }

    public int port() {
        return socket.getLocalPort();
    }

    private void serve() {
        while (!socket.isClosed()) {
            try {
                Socket client = socket.accept();
                try {
                    client.setSoTimeout(5000);
                    handle(client);
                } finally {
                    client.close();
                }
            } catch (Throwable t) {
                if (socket.isClosed()) return;
                CoreLog.warn("[smoke-server] request failed: " + t);
            }
        }
    }

    private void handle(Socket client) throws IOException {
        InputStream in = client.getInputStream();
        StringBuilder head = new StringBuilder();
        int previous = 0;
        int newlines = 0;
        int c;
        while ((c = in.read()) >= 0) {
            head.append((char) c);
            if (c == '\n' && previous == '\r') newlines++; else if (c != '\r' && c != '\n') newlines = 0;
            previous = c;
            if (newlines == 2) break;
        }
        if (head.length() == 0) return; // connection probe without a request
        int length = 0;
        for (String line : head.toString().split("\r\n")) {
            String lower = line.toLowerCase();
            if (lower.startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
            if (lower.startsWith("authorization:")) lastAuthorization = line.substring(14).trim();
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (body.size() < length) {
            int n = in.read(buffer, 0, Math.min(buffer.length, length - body.size()));
            if (n < 0) break;
            body.write(buffer, 0, n);
        }
        requests.incrementAndGet();
        byte[] response = answer(new String(body.toByteArray(), UTF8)).getBytes(UTF8);
        OutputStream out = client.getOutputStream();
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + response.length
                + "\r\nConnection: close\r\n\r\n").getBytes(Charset.forName("US-ASCII")));
        out.write(response);
        out.flush();
    }

    private static String answer(String requestBody) {
        JsonObject request = new JsonParser().parse(requestBody).getAsJsonObject();
        JsonArray messages = request.getAsJsonArray("messages");
        String user = messages.get(messages.size() - 1).getAsJsonObject().get("content").getAsString();
        JsonArray segments = new JsonParser().parse(user).getAsJsonObject().getAsJsonArray("segments");
        JsonArray translations = new JsonArray();
        for (JsonElement segment : segments) {
            translations.add(new com.google.gson.JsonPrimitive("TR| " + segment.getAsString()));
        }
        JsonObject content = new JsonObject();
        content.add("translations", translations);
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", content.toString());
        JsonObject choice = new JsonObject();
        choice.add("message", message);
        JsonArray choices = new JsonArray();
        choices.add(choice);
        JsonObject root = new JsonObject();
        root.add("choices", choices);
        return root.toString();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
