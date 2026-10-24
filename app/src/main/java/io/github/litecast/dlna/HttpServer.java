/*
 * Copyright (C) 2026 小狗便利店投屏 contributors
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Ported from puppy-store-cast (小狗便利店投屏) — core/HttpServer.java.
 * Changes: tolerant SOAP handling (SOAPAction header optional/unquoted, any service version,
 * missing optional arguments defaulted), chunked request bodies, duplicate headers (last wins),
 * configurable friendly name.
 */

package io.github.litecast.dlna;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Small HTTP/SOAP/GENA endpoint, bound only to the selected LAN IPv4 address. */
public final class HttpServer implements AutoCloseable {
    public static final String SERVER = "Android/10 UPnP/1.0 LiteCast/0.1";
    private static final int MAX_BODY = 131072;
    private final ServerSocket socket;
    private final String uuid, base, version;
    private final Supplier<String> friendlyName;
    private final Dlna.Controller controller;
    private final Consumer<String> log;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 8, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32));
    private final ScheduledExecutorService events = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private volatile boolean closed;

    private static final class Subscription {
        final String id = "uuid:" + UUID.randomUUID(), service;
        final URI callback;
        final InetAddress peer;
        volatile long expires;
        long sequence;
        Subscription(String service, URI callback, InetAddress peer, int seconds) {
            this.service = service; this.callback = callback; this.peer = peer; renew(seconds);
        }
        void renew(int seconds) { expires = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds); }
    }

    record Request(String method, String path, Map<String, String> headers, String body, InetAddress peer) {}

    public HttpServer(InetAddress address, int port, String uuid, Supplier<String> friendlyName, String version,
                      Dlna.Controller controller, Consumer<String> log) throws IOException {
        this.uuid = uuid; this.friendlyName = friendlyName; this.version = version; this.controller = controller; this.log = log;
        socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(address, port), 16);
        base = "http://" + address.getHostAddress() + ":" + socket.getLocalPort();
    }

    public String base() { return base; }

    public void start() {
        Thread t = new Thread(() -> {
            while (!closed) {
                try {
                    Socket client = socket.accept();
                    try { workers.execute(() -> handle(client)); }
                    catch (RejectedExecutionException busy) { client.close(); }
                } catch (IOException e) { if (!closed) log.accept("DLNA accept failed: " + e.getClass().getSimpleName()); }
            }
        }, "dlna-http");
        t.setDaemon(true);
        t.start();
    }

    private void handle(Socket client) {
        try (client) {
            client.setSoTimeout(8000);
            Request r;
            try { r = read(client); } catch (Exception invalid) { send(client, 400, Map.of(), "text/plain", "Bad request"); return; }
            if (r.method.equals("GET") || r.method.equals("HEAD")) {
                boolean head = r.method.equals("HEAD");
                if (r.path.equals("/description.xml") || r.path.equals("/")) {
                    if (r.path.equals("/") && !acceptsXml(r)) {
                        send(client, 200, Map.of(), "text/html; charset=utf-8", "<!doctype html><meta charset='utf-8'><title>LiteCast</title><h1>LiteCast</h1><p>"
                                + Xml.escape(friendlyName.get()) + "</p>", head);
                    } else {
                        send(client, 200, Map.of(), "text/xml; charset=\"utf-8\"", Dlna.description(uuid, base, friendlyName.get(), version), head);
                    }
                } else if (r.path.startsWith("/service/") && r.path.endsWith(".xml") && Dlna.SERVICES.contains(r.path.substring(9, r.path.length() - 4))) {
                    send(client, 200, Map.of(), "text/xml; charset=\"utf-8\"", Dlna.scpd(r.path.substring(9, r.path.length() - 4)), head);
                } else send(client, 404, Map.of(), "text/plain", "Not found");
            } else if (r.method.equals("POST") && r.path.startsWith("/control/")) {
                String service = r.path.substring(9);
                if (!Dlna.SERVICES.contains(service)) { send(client, 404, Map.of(), "text/plain", "Not found"); return; }
                String name = "Unknown";
                try {
                    var action = Xml.soapAction(r.body);
                    name = Xml.local(action);
                    checkAction(service, name, action.getNamespaceURI(), r.headers.get("soapaction"));
                    final String actionName = name;
                    Dlna.Action definition = Dlna.actions(service).stream().filter(a -> a.name().equals(actionName)).findFirst()
                            .orElseThrow(() -> new Dlna.Fault(401, "Invalid Action"));
                    Map<String, String> args = new LinkedHashMap<>(Xml.arguments(action));
                    for (Dlna.Arg arg : definition.args()) {
                        if (!arg.direction().equals("in") || args.containsKey(arg.name())) continue;
                        String fallback = Dlna.defaultArgument(arg.name());
                        if (fallback == null) throw new Dlna.Fault(402, "Invalid Args");
                        args.put(arg.name(), fallback);
                    }
                    Map<String, String> response = controller.action(service, name, args);
                    send(client, 200, Map.of("EXT", ""), "text/xml; charset=\"utf-8\"", Dlna.response(service, name, response));
                    if (!name.startsWith("Get") && !name.equals("ListPresets")) publish();
                } catch (Dlna.Fault f) {
                    send(client, 500, Map.of("EXT", ""), "text/xml; charset=\"utf-8\"", Dlna.fault(f.code, f.getMessage()));
                } catch (Exception e) {
                    log.accept("DLNA " + name + " failed: " + e.getClass().getSimpleName());
                    send(client, 500, Map.of("EXT", ""), "text/xml; charset=\"utf-8\"", Dlna.fault(501, "Action Failed"));
                }
            } else if ((r.method.equals("SUBSCRIBE") || r.method.equals("UNSUBSCRIBE")) && r.path.startsWith("/event/")) {
                subscribe(client, r);
            } else send(client, 405, Map.of("Allow", "GET, HEAD, POST, SUBSCRIBE, UNSUBSCRIBE"), "text/plain", "Method not allowed");
        } catch (Exception ignored) { /* Peer can disconnect while sending a command. */ }
    }

    private static boolean acceptsXml(Request r) {
        String accept = r.headers.getOrDefault("accept", "");
        String agent = r.headers.getOrDefault("user-agent", "").toLowerCase(Locale.US);
        return accept.contains("xml") || !agent.contains("mozilla");
    }

    /**
     * Senders disagree on SOAPAction quoting, service versions and even whether to send the header
     * at all. Only a header that names a different action is treated as a conflict.
     */
    static void checkAction(String service, String name, String namespace, String soapAction) throws Dlna.Fault {
        String prefix = "urn:schemas-upnp-org:service:" + service + ":";
        if (namespace != null && !namespace.isEmpty() && !namespace.startsWith(prefix)) throw new Dlna.Fault(401, "Invalid action namespace");
        if (soapAction == null) return;
        String value = soapAction.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) value = value.substring(1, value.length() - 1).trim();
        if (value.isEmpty()) return;
        int hash = value.lastIndexOf('#');
        String declared = hash >= 0 ? value.substring(hash + 1) : value;
        if (!declared.equals(name)) throw new Dlna.Fault(401, "SOAPAction does not match body");
    }

    private void subscribe(Socket client, Request r) throws Exception {
        String service = r.path.substring(7), sid = r.headers.get("sid");
        if (!Dlna.SERVICES.contains(service)) { send(client, 404, Map.of(), "text/plain", "Not found"); return; }
        subscriptions.values().removeIf(s -> s.expires < System.nanoTime());
        if (r.method.equals("UNSUBSCRIBE")) {
            Subscription s = sid == null ? null : subscriptions.get(sid);
            if (s == null || !s.peer.equals(r.peer) || !s.service.equals(service)) { send(client, 412, Map.of(), "text/plain", "Invalid subscription"); return; }
            subscriptions.remove(sid); send(client, 200, Map.of(), "text/plain", ""); return;
        }
        int seconds = 1800;
        try {
            seconds = Math.max(30, Math.min(1800, Integer.parseInt(r.headers.getOrDefault("timeout", "Second-1800").replaceFirst("(?i)^Second-", "").trim())));
        } catch (NumberFormatException ignored) { /* "infinite" and garbage both fall back to 1800 */ }
        Subscription s;
        if (sid != null) {
            s = subscriptions.get(sid);
            if (s == null || !s.peer.equals(r.peer) || !s.service.equals(service)) { send(client, 412, Map.of(), "text/plain", "Invalid subscription"); return; }
            s.renew(seconds);
        } else {
            try {
                if (subscriptions.size() >= 16 || !"upnp:event".equalsIgnoreCase(r.headers.getOrDefault("nt", "").trim())) throw new IllegalArgumentException();
                String raw = r.headers.getOrDefault("callback", "").trim();
                int open = raw.indexOf('<'), end = raw.indexOf('>', open + 1);
                if (open < 0 || end < 0) throw new IllegalArgumentException();
                URI uri = URI.create(raw.substring(open + 1, end));
                // Numeric callback must point back to the sender; no arbitrary URL fetching or DNS rebinding.
                if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null
                        || !r.peer.getHostAddress().equals(uri.getHost()) || (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535))) {
                    throw new IllegalArgumentException();
                }
                s = new Subscription(service, uri, r.peer, seconds);
                subscriptions.put(s.id, s);
            } catch (Exception e) { send(client, 412, Map.of(), "text/plain", "Invalid callback"); return; }
        }
        send(client, 200, Map.of("SID", s.id, "TIMEOUT", "Second-" + seconds), "text/plain", "");
        if (sid == null) {
            final Subscription first = s;
            events.schedule(() -> notifyOne(first), 150, TimeUnit.MILLISECONDS);
        }
    }

    public void publish() {
        if (!closed) try { events.execute(() -> subscriptions.values().forEach(this::notifyOne)); } catch (RejectedExecutionException ignored) { }
    }

    private void notifyOne(Subscription s) {
        if (closed || !subscriptions.containsKey(s.id)) return;
        if (s.expires < System.nanoTime()) { subscriptions.remove(s.id); return; }
        try {
            byte[] body = Dlna.eventXml(s.service, controller.event(s.service)).getBytes(StandardCharsets.UTF_8);
            int port = s.callback.getPort() < 0 ? 80 : s.callback.getPort();
            try (Socket connection = new Socket()) {
                connection.connect(new InetSocketAddress(s.peer, port), 2000);
                connection.setSoTimeout(2000);
                String path = s.callback.getRawPath() == null || s.callback.getRawPath().isEmpty() ? "/" : s.callback.getRawPath();
                if (s.callback.getRawQuery() != null) path += "?" + s.callback.getRawQuery();
                long sequence = s.sequence;
                s.sequence = sequence >= 4294967295L ? 1 : sequence + 1;
                String header = "NOTIFY " + path + " HTTP/1.1\r\nHOST: " + s.peer.getHostAddress() + ":" + port
                        + "\r\nCONTENT-TYPE: text/xml; charset=\"utf-8\"\r\nNT: upnp:event\r\nNTS: upnp:propchange\r\nSID: " + s.id
                        + "\r\nSEQ: " + sequence + "\r\nCONTENT-LENGTH: " + body.length + "\r\nConnection: close\r\n\r\n";
                connection.getOutputStream().write(header.getBytes(StandardCharsets.US_ASCII));
                connection.getOutputStream().write(body);
                connection.getOutputStream().flush();
                String status = line(connection.getInputStream(), 1024);
                if (status == null || !status.matches("HTTP/1\\.[01] 2\\d\\d.*")) subscriptions.remove(s.id);
            }
        } catch (Exception ignored) { /* A sleeping sender may temporarily miss an event; retain until expiry. */ }
    }

    static Request read(Socket client) throws Exception {
        return read(new BufferedInputStream(client.getInputStream()), client.getInetAddress());
    }

    static Request read(InputStream input, InetAddress peer) throws Exception {
        String start = line(input, 8192);
        if (start == null) throw new IOException();
        String[] pieces = start.trim().split(" +");
        if (pieces.length != 3 || !pieces[2].startsWith("HTTP/1.")) throw new IOException();
        String target = pieces[1];
        if (target.startsWith("http://")) { // absolute-form request target
            int slash = target.indexOf('/', 7);
            target = slash < 0 ? "/" : target.substring(slash);
        }
        if (!target.startsWith("/")) throw new IOException();
        Map<String, String> headers = new LinkedHashMap<>();
        int total = start.length();
        for (String h; (h = line(input, 8192)) != null && !h.isEmpty(); ) {
            total += h.length();
            if (total > 16384) throw new IOException();
            int colon = h.indexOf(':');
            if (colon <= 0) continue; // ignore malformed header lines rather than dropping the request
            headers.put(h.substring(0, colon).trim().toLowerCase(Locale.US), h.substring(colon + 1).trim());
        }
        byte[] bytes;
        if (headers.getOrDefault("transfer-encoding", "").toLowerCase(Locale.US).contains("chunked")) {
            bytes = readChunked(input);
        } else {
            int length = Integer.parseInt(headers.getOrDefault("content-length", "0").trim());
            if (length < 0 || length > MAX_BODY) throw new IOException();
            bytes = new byte[length];
            int offset = 0;
            while (offset < length) {
                int n = input.read(bytes, offset, length - offset);
                if (n < 0) throw new EOFException();
                offset += n;
            }
        }
        return new Request(pieces[0], target.split("\\?", 2)[0], headers, new String(bytes, StandardCharsets.UTF_8), peer);
    }

    private static byte[] readChunked(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = line(input, 1024);
            if (sizeLine == null) throw new EOFException();
            int semicolon = sizeLine.indexOf(';');
            int size = Integer.parseInt((semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine).trim(), 16);
            if (size < 0 || out.size() + size > MAX_BODY) throw new IOException();
            if (size == 0) {
                for (String trailer; (trailer = line(input, 1024)) != null && !trailer.isEmpty(); ) { /* discard trailers */ }
                return out.toByteArray();
            }
            byte[] chunk = new byte[size];
            int offset = 0;
            while (offset < size) {
                int n = input.read(chunk, offset, size - offset);
                if (n < 0) throw new EOFException();
                offset += n;
            }
            out.write(chunk);
            line(input, 16); // CRLF after each chunk
        }
    }

    private static String line(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int c; (c = input.read()) != -1; ) {
            if (c == '\n') return new String(b.toByteArray(), StandardCharsets.UTF_8).replace("\r", "");
            if (b.size() >= limit) throw new IOException();
            b.write(c);
        }
        return b.size() == 0 ? null : new String(b.toByteArray(), StandardCharsets.UTF_8).replace("\r", "");
    }

    private static void send(Socket s, int code, Map<String, String> headers, String mime, String body) throws IOException { send(s, code, headers, mime, body, false); }

    private static void send(Socket s, int code, Map<String, String> headers, String mime, String body, boolean head) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String reason = switch (code) {
            case 200 -> "OK"; case 400 -> "Bad Request"; case 404 -> "Not Found"; case 405 -> "Method Not Allowed";
            case 412 -> "Precondition Failed"; default -> "Internal Server Error";
        };
        StringBuilder h = new StringBuilder("HTTP/1.1 " + code + " " + reason + "\r\nSERVER: " + SERVER + "\r\nDATE: "
                + DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.now().atZone(ZoneOffset.UTC)) + "\r\nCONTENT-TYPE: " + mime
                + "\r\nCONTENT-LENGTH: " + bytes.length + "\r\nConnection: close\r\n");
        headers.forEach((k, v) -> h.append(k).append(": ").append(v).append("\r\n"));
        OutputStream output = s.getOutputStream();
        output.write(h.append("\r\n").toString().getBytes(StandardCharsets.US_ASCII));
        if (!head) output.write(bytes);
        output.flush();
    }

    @Override public void close() {
        closed = true;
        try { socket.close(); } catch (IOException ignored) { }
        workers.shutdownNow();
        events.shutdownNow();
        subscriptions.clear();
    }
}
