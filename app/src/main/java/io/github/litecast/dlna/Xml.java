/*
 * Copyright (C) 2026 小狗便利店投屏 contributors
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Ported from puppy-store-cast (小狗便利店投屏) — core/Xml.java.
 * Changes: tolerate a leading BOM / whitespace before the XML declaration.
 */

package io.github.litecast.dlna;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public final class Xml {
    private Xml() {}

    private static final int MAX_DEPTH = 32;

    public static String escape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < 0x20 && c != '\t' && c != '\n' && c != '\r') || c == 0xFFFE || c == 0xFFFF) continue;
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static void checkNestingDepth(String xml) {
        int depth = 0;
        boolean inComment = false;
        boolean inCdata = false;
        int len = xml.length();
        for (int i = 0; i < len; i++) {
            if (inComment) {
                if (i + 2 < len && xml.charAt(i) == '-' && xml.charAt(i + 1) == '-' && xml.charAt(i + 2) == '>') {
                    inComment = false;
                    i += 2;
                }
                continue;
            }
            if (inCdata) {
                if (i + 2 < len && xml.charAt(i) == ']' && xml.charAt(i + 1) == ']' && xml.charAt(i + 2) == '>') {
                    inCdata = false;
                    i += 2;
                }
                continue;
            }
            if (xml.charAt(i) != '<' || i + 1 >= len) continue;
            char next = xml.charAt(i + 1);
            if (next == '!') {
                if (xml.startsWith("<!--", i)) { inComment = true; i += 3; }
                else if (xml.startsWith("<![CDATA[", i)) { inCdata = true; i += 8; }
                continue;
            }
            if (next == '?') continue;
            if (next == '/') {
                if (depth > 0) depth--;
                continue;
            }
            // Opening tag: scan to closing '>' to check if self-closing.
            int close = i + 1;
            char quote = 0;
            while (close < len) {
                char ch = xml.charAt(close);
                if (quote != 0) {
                    if (ch == quote) quote = 0;
                } else if (ch == '"' || ch == '\'') {
                    quote = ch;
                } else if (ch == '>') {
                    break;
                }
                close++;
            }
            if (close < len && xml.charAt(close - 1) != '/') {
                if (++depth > MAX_DEPTH) throw new IllegalArgumentException("XML nesting too deep");
            }
            i = close;
        }
    }

    public static Document parse(String value) throws Exception {
        String text = value == null ? "" : value;
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        text = text.trim();
        // No DTDs at all: closes off entity expansion and external fetches on every parser.
        if (text.contains("<!DOCTYPE") || text.contains("<!ENTITY")) throw new IllegalArgumentException("DTD disabled");
        checkNestingDepth(text);
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) { }
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) { }
        try { f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); } catch (Exception ignored) { /* Android parser may not expose it. */ }
        var b = f.newDocumentBuilder();
        b.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        b.setErrorHandler(new DefaultHandler() {
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
        });
        return b.parse(new InputSource(new StringReader(text)));
    }

    public static String local(Node node) { return node.getLocalName() == null ? node.getNodeName() : node.getLocalName(); }

    public static Element soapAction(String body) throws Exception {
        var nodes = parse(body).getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        if (nodes.getLength() != 1) throw new IllegalArgumentException("SOAP Body required");
        Element action = null;
        for (Node n = nodes.item(0).getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                if (action != null) throw new IllegalArgumentException("One action required");
                action = (Element) n;
            }
        }
        if (action == null) throw new IllegalArgumentException("Action required");
        return action;
    }

    public static Map<String, String> arguments(Element action) {
        Map<String, String> args = new LinkedHashMap<>();
        for (Node n = action.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) args.put(local(n), n.getTextContent());
        }
        return args;
    }

    public static String first(String xml, String tag) {
        try {
            var nodes = parse(xml).getElementsByTagNameNS("*", tag);
            return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
        } catch (Exception ignored) { return ""; }
    }

    public static String resourceMime(String metadata) {
        return resourceMime(metadata, null);
    }

    public static String resourceMime(String metadata, String preferredUri) {
        try {
            var nodes = parse(metadata).getElementsByTagNameNS("*", "res");
            String firstAny = "";
            String firstMedia = "";
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String[] info = el.getAttribute("protocolInfo").split(":", 4);
                if (info.length != 4 || !info[2].contains("/")) continue;
                String mime = info[2];
                if (preferredUri != null && !preferredUri.isEmpty() && preferredUri.equals(el.getTextContent().trim())) {
                    return mime;
                }
                if (firstAny.isEmpty()) firstAny = mime;
                String lower = mime.toLowerCase(java.util.Locale.ROOT);
                if (firstMedia.isEmpty() && !lower.startsWith("image/") && !lower.startsWith("text/")) {
                    firstMedia = mime;
                }
            }
            return firstMedia.isEmpty() ? firstAny : firstMedia;
        } catch (Exception ignored) { }
        return "";
    }
}
