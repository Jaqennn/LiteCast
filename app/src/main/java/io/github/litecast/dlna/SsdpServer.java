/*
 * Copyright (C) 2026 小狗便利店投屏 contributors
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Ported from puppy-store-cast (小狗便利店投屏) — core/SsdpServer.java.
 * Changes: accept unquoted MAN headers; announce in a short burst at start; if UDP 1900 is held
 * exclusively by another receiver (e.g. a pre-installed casting app), fall back to frequent
 * NOTIFY announcements from an ephemeral port so passive controllers still find us.
 */

package io.github.litecast.dlna;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class SsdpServer implements AutoCloseable {
    private static final String GROUP = "239.255.255.250";
    private final MulticastSocket socket;
    private final InetAddress group;
    private final String uuid, location;
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
    private final Consumer<String> log;
    private final List<String> types;
    private final boolean listening;
    private volatile boolean closed;
    private int pendingSearches;
    private long lastSearchLog;

    public SsdpServer(InetAddress address, String uuid, String location, Consumer<String> log) throws IOException {
        this.uuid = "uuid:" + uuid;
        this.location = location;
        this.log = log;
        types = new ArrayList<>(List.of("upnp:rootdevice", this.uuid, Dlna.DEVICE));
        for (String s : Dlna.SERVICES) types.add(Dlna.type(s));
        group = InetAddress.getByName(GROUP);
        NetworkInterface net = NetworkInterface.getByInetAddress(address);
        if (net == null) throw new IOException("No LAN interface");
        MulticastSocket bound;
        boolean canListen = true;
        try {
            bound = new MulticastSocket(null);
            bound.setReuseAddress(true);
            bound.bind(new InetSocketAddress(1900));
        } catch (IOException busy) {
            // Another app holds 1900 without SO_REUSEADDR: we can still announce, just not answer searches.
            canListen = false;
            bound = new MulticastSocket(new InetSocketAddress(address, 0));
            log.accept("SSDP port 1900 busy; announcing only");
        }
        socket = bound;
        listening = canListen;
        try {
            socket.setNetworkInterface(net);
            socket.setTimeToLive(4);
            if (listening) socket.joinGroup(new InetSocketAddress(group, 1900), net);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        scheduler.setRemoveOnCancelPolicy(true);
    }

    public boolean isListening() { return listening; }

    public void start() {
        // Controllers that only listen passively see us within a second; repeat in case of loss.
        for (long delay : new long[]{0, 1, 3}) scheduler.schedule(() -> announce("ssdp:alive"), delay, TimeUnit.SECONDS);
        long interval = listening ? 90 : 20;
        scheduler.scheduleWithFixedDelay(() -> announce("ssdp:alive"), interval, interval, TimeUnit.SECONDS);
        if (!listening) return;
        Thread t = new Thread(() -> {
            byte[] buffer = new byte[8192];
            while (!closed) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    if (packet.getAddress().isMulticastAddress() || packet.getAddress().isAnyLocalAddress()) continue;
                    String text = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.US_ASCII);
                    Search search = parseSearch(text);
                    if (search == null) continue;
                    List<String> matched = search.st.equalsIgnoreCase("ssdp:all") ? types : (types.contains(search.st) ? List.of(search.st) : List.of());
                    if (matched.isEmpty()) continue;
                    if (System.nanoTime() - lastSearchLog > TimeUnit.SECONDS.toNanos(10)) {
                        lastSearchLog = System.nanoTime();
                        log.accept("SSDP search from " + packet.getAddress().getHostAddress());
                    }
                    synchronized (this) {
                        if (pendingSearches >= 64) continue;
                        pendingSearches++;
                    }
                    InetAddress peer = packet.getAddress();
                    int port = packet.getPort();
                    scheduler.schedule(() -> {
                        try { for (String type : matched) reply(peer, port, type); }
                        finally { synchronized (this) { pendingSearches--; } }
                    }, ThreadLocalRandom.current().nextInt(10, Math.max(11, Math.min(search.mx, 2) * 500)), TimeUnit.MILLISECONDS);
                } catch (IOException e) {
                    if (!closed) log.accept("SSDP receive failed");
                } catch (RejectedExecutionException ignored) { }
            }
        }, "dlna-ssdp");
        t.setDaemon(true);
        t.start();
    }

    public record Search(String st, int mx) {}

    public static Search parseSearch(String text) {
        String[] lines = text.split("\r?\n");
        if (lines.length < 2 || !lines[0].trim().toUpperCase(Locale.US).startsWith("M-SEARCH ")) return null;
        Map<String, String> h = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int c = lines[i].indexOf(':');
            if (c > 0) h.put(lines[i].substring(0, c).trim().toLowerCase(Locale.US), lines[i].substring(c + 1).trim());
        }
        String man = h.getOrDefault("man", "").replace("\"", "").trim();
        if (!man.equalsIgnoreCase("ssdp:discover") || !h.containsKey("st")) return null;
        int mx = 1;
        try { mx = Math.min(5, Math.max(1, Integer.parseInt(h.getOrDefault("mx", "1").trim()))); } catch (NumberFormatException ignored) { }
        return new Search(h.get("st").trim(), mx);
    }

    private String usn(String type) { return type.equals(uuid) ? uuid : uuid + "::" + type; }

    private void reply(InetAddress peer, int port, String type) {
        send("HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nEXT:\r\nLOCATION: " + location
                + "\r\nSERVER: " + HttpServer.SERVER + "\r\nST: " + type + "\r\nUSN: " + usn(type) + "\r\nCONTENT-LENGTH: 0\r\n\r\n", peer, port);
    }

    private void announce(String nts) {
        for (String type : types) {
            send("NOTIFY * HTTP/1.1\r\nHOST: " + GROUP + ":1900\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: " + location + "\r\nNT: " + type
                    + "\r\nNTS: " + nts + "\r\nSERVER: " + HttpServer.SERVER + "\r\nUSN: " + usn(type) + "\r\nCONTENT-LENGTH: 0\r\n\r\n", group, 1900);
        }
    }

    private void send(String value, InetAddress peer, int port) {
        try {
            byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
            socket.send(new DatagramPacket(bytes, bytes.length, peer, port));
        } catch (IOException ignored) { }
    }

    @Override public void close() {
        if (closed) return;
        try { announce("ssdp:byebye"); } catch (RuntimeException ignored) { }
        closed = true;
        scheduler.shutdownNow();
        socket.close();
    }
}
