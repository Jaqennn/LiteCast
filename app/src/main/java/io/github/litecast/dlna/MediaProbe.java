/*
 * Copyright (C) 2026 小狗便利店投屏 contributors
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Ported from puppy-store-cast (小狗便利店投屏) — core/MediaProbe.java. Changes: user agent only.
 */

package io.github.litecast.dlna;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Bounded format inspection, used only after a parser error. Never logs URL queries or payloads. */
public final class MediaProbe {
    public static final String HLS = "application/x-mpegURL", DASH = "application/dash+xml";
    /** Same style as the user agent that was field-tested by puppy-store-cast on Sony TVs. */
    public static final String USER_AGENT = "LiteCast/0.1 AndroidTV";
    private static final int LIMIT = 8192;
    private static final Pattern MPD = Pattern.compile("<(?:[A-Za-z_][A-Za-z0-9_.-]*:)?MPD(?:\\s|>)");

    private MediaProbe() {}

    public record Format(String kind, String mime) {
        public boolean nonMedia() { return kind.equals("HTML") || kind.equals("JSON") || kind.equals("XML"); }
    }

    public record Result(int status, String contentType, Format format, int bytes) {}

    public static String mime(String value) {
        if (value == null) return "";
        String type = value.split(";", 2)[0].trim().toLowerCase(Locale.US);
        if (type.length() > 100 || !type.matches("[a-z0-9.+_-]+/[a-z0-9.+_-]+")) return "";
        if (type.equals("application/vnd.apple.mpegurl") || type.equals("application/x-mpegurl")
                || type.equals("audio/mpegurl") || type.equals("audio/x-mpegurl")) return HLS;
        return type;
    }

    public static String initialMime(String uri, String declared) {
        String ext = extensionMime(uri);
        return ext.isEmpty() ? mime(declared) : ext;
    }

    public static String extensionMime(String uri) {
        try {
            String path = new URI(uri).getPath();
            if (path == null) return "";
            path = path.toLowerCase(Locale.US);
            if (path.endsWith(".m3u8")) return HLS;
            if (path.endsWith(".mpd")) return DASH;
        } catch (Exception ignored) { }
        return "";
    }

    public static Format inspect(byte[] bytes, String responseType, String finalUri) {
        String prefix = new String(bytes, StandardCharsets.UTF_8);
        if (prefix.startsWith("\uFEFF")) prefix = prefix.substring(1);
        prefix = prefix.trim();
        String lower = prefix.toLowerCase(Locale.US);
        if (prefix.startsWith("#EXTM3U")) return new Format("HLS", HLS);
        if (lower.startsWith("<!doctype html") || lower.startsWith("<html") || lower.startsWith("<head") || lower.startsWith("<body")) return new Format("HTML", "");
        if (prefix.startsWith("{") || prefix.startsWith("[")) return new Format("JSON", "");
        if (MPD.matcher(prefix).find()) return new Format("DASH", DASH);
        if (prefix.startsWith("<?xml") || prefix.startsWith("<")) return new Format("XML", "");
        if (bytes.length >= 8) {
            String box = new String(bytes, 4, 4, StandardCharsets.US_ASCII);
            if (box.equals("ftyp") || box.equals("styp") || box.equals("moof")) return new Format("MP4", "video/mp4");
        }
        if (bytes.length >= 3 && bytes[0] == 'F' && bytes[1] == 'L' && bytes[2] == 'V') return new Format("FLV", "video/x-flv");
        if (bytes.length > 188 && (bytes[0] & 255) == 0x47 && (bytes[188] & 255) == 0x47) return new Format("MPEG-TS", "video/mp2t");
        String type = mime(responseType);
        if (type.equals("text/html")) return new Format("HTML", "");
        if (type.equals("application/json") || type.endsWith("+json")) return new Format("JSON", "");
        if (type.equals(HLS)) return new Format("HLS-header", HLS);
        if (type.equals(DASH)) return new Format("DASH-header", DASH);
        String extension = extensionMime(finalUri);
        if (!extension.isEmpty()) return new Format("playlist-extension", extension);
        return new Format("UNKNOWN", "");
    }

    public static Result fetch(String original) throws IOException {
        URL next = new URL(original);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        boolean range = true;
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            int timeout = (int) Math.min(6000, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            if (timeout <= 0) throw new SocketTimeoutException("Format probe timeout");
            if (!(next.getProtocol().equals("http") || next.getProtocol().equals("https")) || next.getUserInfo() != null) throw new IOException("Unsupported redirect scheme");
            HttpURLConnection connection = (HttpURLConnection) next.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (range) connection.setRequestProperty("Range", "bytes=0-" + (LIMIT - 1));
            try {
                int code = connection.getResponseCode();
                String contentType = mime(connection.getContentType());
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || redirects == 5) throw new IOException("Too many redirects");
                    next = new URL(next, location);
                    continue;
                }
                // A server may reject partial GETs, while supporting an ordinary streamed GET.
                if (code == 416 && range) { range = false; redirects--; continue; }
                if (code < 200 || code >= 300) return new Result(code, contentType, new Format("HTTP-ERROR", ""), 0);
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                try (InputStream stream = connection.getInputStream()) {
                    byte[] buffer = new byte[1024];
                    while (data.size() < LIMIT) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                        int left = (int) Math.min(6000, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
                        if (left <= 0) throw new SocketTimeoutException("Format probe timeout");
                        connection.setReadTimeout(left);
                        int count = stream.read(buffer, 0, Math.min(buffer.length, LIMIT - data.size()));
                        if (count < 0) break;
                        data.write(buffer, 0, count);
                        // The opening signature is sufficient; do not wait for a long live body.
                        Format format = inspect(data.toByteArray(), "", "");
                        if (!format.kind.equals("UNKNOWN") && !format.kind.equals("XML")) return new Result(code, contentType, format, data.size());
                    }
                }
                return new Result(code, contentType, inspect(data.toByteArray(), contentType, next.toString()), data.size());
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many redirects");
    }
}
