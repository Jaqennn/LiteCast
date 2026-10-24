/*
 * Copyright (C) 2026 小狗便利店投屏 contributors
 * Copyright (C) 2026 LiteCast contributors
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Ported from puppy-store-cast (小狗便利店投屏),
 * https://github.com/leiz56638111-create/puppy-store-cast — app/src/main/java/cn/local/livingroomcast/core/Dlna.java
 * Changes: configurable friendly name/model, tolerant defaults for missing arguments.
 */

package io.github.litecast.dlna;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Deliberately small UPnP AV MediaRenderer description and SOAP helpers for a single session. */
public final class Dlna {
    private Dlna() {}

    public static final String DEVICE = "urn:schemas-upnp-org:device:MediaRenderer:1";
    public static final String AV = "AVTransport", RC = "RenderingControl", CM = "ConnectionManager";
    public static final List<String> SERVICES = List.of(AV, RC, CM);
    public static final String PROTOCOLS = "http-get:*:video/mp4:*,http-get:*:video/mpeg:*,http-get:*:video/mp2t:*,"
            + "http-get:*:video/x-flv:*,http-get:*:video/x-matroska:*,http-get:*:video/webm:*,"
            + "http-get:*:application/vnd.apple.mpegurl:*,http-get:*:application/x-mpegurl:*,"
            + "http-get:*:application/dash+xml:*,http-get:*:audio/mpeg:*,http-get:*:audio/mp4:*,"
            + "http-get:*:audio/aac:*,http-get:*:audio/flac:*,http-get:*:image/jpeg:*,http-get:*:image/png:*";

    public interface Controller {
        Map<String, String> action(String service, String action, Map<String, String> args) throws Exception;
        Map<String, String> event(String service) throws Exception;
    }

    public static final class Fault extends Exception {
        public final int code;
        public Fault(int code, String message) { super(message); this.code = code; }
    }

    public record Arg(String name, String direction, String variable) {}
    public record Action(String name, List<Arg> args) {}
    public record Variable(String name, String type, String defaultValue, String values, boolean evented) {}

    public static String type(String service) { return "urn:schemas-upnp-org:service:" + service + ":1"; }
    private static Arg in(String name, String variable) { return new Arg(name, "in", variable); }
    private static Arg out(String name, String variable) { return new Arg(name, "out", variable); }
    private static Action a(String name, Arg... args) { return new Action(name, List.of(args)); }
    private static Arg id() { return in("InstanceID", "A_ARG_TYPE_InstanceID"); }
    private static Arg channel() { return in("Channel", "A_ARG_TYPE_Channel"); }

    public static List<Action> actions(String s) {
        return switch (s) {
            case AV -> List.of(
                a("SetAVTransportURI", id(), in("CurrentURI", "AVTransportURI"), in("CurrentURIMetaData", "AVTransportURIMetaData")),
                a("SetNextAVTransportURI", id(), in("NextURI", "NextAVTransportURI"), in("NextURIMetaData", "NextAVTransportURIMetaData")),
                a("GetMediaInfo", id(), out("NrTracks", "NumberOfTracks"), out("MediaDuration", "CurrentMediaDuration"), out("CurrentURI", "AVTransportURI"), out("CurrentURIMetaData", "AVTransportURIMetaData"), out("NextURI", "NextAVTransportURI"), out("NextURIMetaData", "NextAVTransportURIMetaData"), out("PlayMedium", "PlaybackStorageMedium"), out("RecordMedium", "RecordStorageMedium"), out("WriteStatus", "RecordMediumWriteStatus")),
                a("GetTransportInfo", id(), out("CurrentTransportState", "TransportState"), out("CurrentTransportStatus", "TransportStatus"), out("CurrentSpeed", "TransportPlaySpeed")),
                a("GetPositionInfo", id(), out("Track", "CurrentTrack"), out("TrackDuration", "CurrentTrackDuration"), out("TrackMetaData", "CurrentTrackMetaData"), out("TrackURI", "CurrentTrackURI"), out("RelTime", "RelativeTimePosition"), out("AbsTime", "AbsoluteTimePosition"), out("RelCount", "RelativeCounterPosition"), out("AbsCount", "AbsoluteCounterPosition")),
                a("GetDeviceCapabilities", id(), out("PlayMedia", "PossiblePlaybackStorageMedia"), out("RecMedia", "PossibleRecordStorageMedia"), out("RecQualityModes", "PossibleRecordQualityModes")),
                a("GetTransportSettings", id(), out("PlayMode", "CurrentPlayMode"), out("RecQualityMode", "CurrentRecordQualityMode")),
                a("GetCurrentTransportActions", id(), out("Actions", "CurrentTransportActions")),
                a("Play", id(), in("Speed", "TransportPlaySpeed")), a("Pause", id()), a("Stop", id()),
                a("Seek", id(), in("Unit", "A_ARG_TYPE_SeekMode"), in("Target", "A_ARG_TYPE_SeekTarget")),
                a("Next", id()), a("Previous", id()));
            case RC -> List.of(a("ListPresets", id(), out("CurrentPresetNameList", "PresetNameList")), a("SelectPreset", id(), in("PresetName", "A_ARG_TYPE_PresetName")),
                a("GetVolume", id(), channel(), out("CurrentVolume", "Volume")), a("SetVolume", id(), channel(), in("DesiredVolume", "Volume")),
                a("GetMute", id(), channel(), out("CurrentMute", "Mute")), a("SetMute", id(), channel(), in("DesiredMute", "Mute")));
            case CM -> List.of(a("GetProtocolInfo", out("Source", "SourceProtocolInfo"), out("Sink", "SinkProtocolInfo")),
                a("GetCurrentConnectionIDs", out("ConnectionIDs", "CurrentConnectionIDs")),
                a("GetCurrentConnectionInfo", in("ConnectionID", "A_ARG_TYPE_ConnectionID"), out("RcsID", "A_ARG_TYPE_RcsID"), out("AVTransportID", "A_ARG_TYPE_AVTransportID"), out("ProtocolInfo", "A_ARG_TYPE_ProtocolInfo"), out("PeerConnectionManager", "A_ARG_TYPE_ConnectionManager"), out("PeerConnectionID", "A_ARG_TYPE_ConnectionID"), out("Direction", "A_ARG_TYPE_Direction"), out("Status", "A_ARG_TYPE_ConnectionStatus")));
            default -> List.of();
        };
    }

    /**
     * Arguments that senders in the wild omit. Filling them in keeps sloppy controllers working
     * instead of answering with "402 Invalid Args".
     */
    public static String defaultArgument(String name) {
        return switch (name) {
            case "InstanceID", "ConnectionID" -> "0";
            case "Channel" -> "Master";
            case "Speed" -> "1";
            case "CurrentURIMetaData", "NextURIMetaData", "NextURI" -> "";
            case "Unit" -> "REL_TIME";
            case "PresetName" -> "FactoryDefaults";
            default -> null;
        };
    }

    public static List<Variable> variables(String s) {
        Map<String, Variable> vars = new LinkedHashMap<>();
        for (Action a : actions(s)) for (Arg arg : a.args) {
            String n = arg.variable;
            String t = switch (n) {
                case "A_ARG_TYPE_InstanceID", "NumberOfTracks", "CurrentTrack" -> "ui4";
                case "A_ARG_TYPE_ConnectionID", "A_ARG_TYPE_RcsID", "A_ARG_TYPE_AVTransportID", "RelativeCounterPosition", "AbsoluteCounterPosition" -> "i4";
                case "Volume" -> "ui2"; case "Mute" -> "boolean";
                case "AVTransportURI", "NextAVTransportURI", "CurrentTrackURI" -> "uri";
                default -> "string";
            };
            String values = switch (n) {
                case "TransportState" -> "STOPPED,PLAYING,PAUSED_PLAYBACK,TRANSITIONING,NO_MEDIA_PRESENT";
                case "TransportStatus" -> "OK,ERROR_OCCURRED";
                case "TransportPlaySpeed" -> "1/2,3/4,1,5/4,3/2,2";
                case "PlaybackStorageMedium", "PossiblePlaybackStorageMedia" -> "NETWORK";
                case "RecordStorageMedium", "RecordMediumWriteStatus", "PossibleRecordStorageMedia", "PossibleRecordQualityModes", "CurrentRecordQualityMode" -> "NOT_IMPLEMENTED";
                case "CurrentPlayMode" -> "NORMAL";
                case "A_ARG_TYPE_Channel" -> "Master";
                case "A_ARG_TYPE_PresetName" -> "FactoryDefaults";
                case "A_ARG_TYPE_SeekMode" -> "REL_TIME,ABS_TIME";
                case "A_ARG_TYPE_Direction" -> "Input,Output";
                case "A_ARG_TYPE_ConnectionStatus" -> "OK,ContentFormatMismatch,InsufficientBandwidth,UnreliableChannel,Unknown";
                default -> "";
            };
            vars.putIfAbsent(n, new Variable(n, t, "", values, s.equals(CM) && (n.equals("SourceProtocolInfo") || n.equals("SinkProtocolInfo") || n.equals("CurrentConnectionIDs"))));
        }
        if (!s.equals(CM)) vars.put("LastChange", new Variable("LastChange", "string", "", "", true));
        return List.copyOf(vars.values());
    }

    public static String description(String uuid, String base, String friendlyName, String version) {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><root xmlns=\"urn:schemas-upnp-org:device-1-0\" xmlns:dlna=\"urn:schemas-dlna-org:device-1-0\"><specVersion><major>1</major><minor>0</minor></specVersion><URLBase>")
                .append(Xml.escape(base)).append("/</URLBase><device><deviceType>").append(DEVICE).append("</deviceType><friendlyName>")
                .append(Xml.escape(friendlyName)).append("</friendlyName><manufacturer>LiteCast</manufacturer><manufacturerURL>https://github.com/</manufacturerURL><modelDescription>LiteCast media renderer</modelDescription><modelName>LiteCast</modelName><modelNumber>")
                .append(Xml.escape(version)).append("</modelNumber><serialNumber>").append(Xml.escape(uuid)).append("</serialNumber><UDN>uuid:").append(Xml.escape(uuid))
                .append("</UDN><dlna:X_DLNADOC>DMR-1.50</dlna:X_DLNADOC><serviceList>");
        for (String s : SERVICES) x.append("<service><serviceType>").append(type(s)).append("</serviceType><serviceId>urn:upnp-org:serviceId:").append(s).append("</serviceId><SCPDURL>/service/").append(s).append(".xml</SCPDURL><controlURL>/control/").append(s).append("</controlURL><eventSubURL>/event/").append(s).append("</eventSubURL></service>");
        return x.append("</serviceList><presentationURL>/</presentationURL></device></root>").toString();
    }

    public static String scpd(String s) {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><scpd xmlns=\"urn:schemas-upnp-org:service-1-0\"><specVersion><major>1</major><minor>0</minor></specVersion><actionList>");
        for (Action a : actions(s)) {
            x.append("<action><name>").append(a.name).append("</name>");
            if (!a.args.isEmpty()) {
                x.append("<argumentList>");
                for (Arg arg : a.args) x.append("<argument><name>").append(arg.name).append("</name><direction>").append(arg.direction).append("</direction><relatedStateVariable>").append(arg.variable).append("</relatedStateVariable></argument>");
                x.append("</argumentList>");
            }
            x.append("</action>");
        }
        x.append("</actionList><serviceStateTable>");
        for (Variable v : variables(s)) {
            x.append("<stateVariable sendEvents=\"").append(v.evented ? "yes" : "no").append("\"><name>").append(v.name).append("</name><dataType>").append(v.type).append("</dataType>");
            if (!v.values.isEmpty()) {
                x.append("<allowedValueList>");
                for (String val : v.values.split(",")) x.append("<allowedValue>").append(val).append("</allowedValue>");
                x.append("</allowedValueList>");
            }
            if (v.name.equals("Volume")) x.append("<allowedValueRange><minimum>0</minimum><maximum>100</maximum><step>1</step></allowedValueRange>");
            x.append("</stateVariable>");
        }
        return x.append("</serviceStateTable></scpd>").toString();
    }

    public static String response(String service, String action, Map<String, String> values) {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:" + action + "Response xmlns:u=\"" + type(service) + "\">");
        values.forEach((k, v) -> x.append('<').append(k).append('>').append(Xml.escape(v)).append("</").append(k).append('>'));
        return x.append("</u:").append(action).append("Response></s:Body></s:Envelope>").toString();
    }

    public static String fault(int code, String message) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\"><errorCode>" + code + "</errorCode><errorDescription>" + Xml.escape(message) + "</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>";
    }

    public static String eventXml(String service, Map<String, String> values) {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">");
        if (service.equals(CM)) values.forEach((k, v) -> x.append("<e:property><").append(k).append('>').append(Xml.escape(v)).append("</").append(k).append("></e:property>"));
        else {
            String ns = service.equals(AV) ? "AVT" : "RCS";
            StringBuilder change = new StringBuilder("<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/" + ns + "/\"><InstanceID val=\"0\">");
            values.forEach((k, v) -> change.append('<').append(k).append(service.equals(RC) ? " channel=\"Master\"" : "").append(" val=\"").append(Xml.escape(v)).append("\"/>"));
            change.append("</InstanceID></Event>");
            x.append("<e:property><LastChange>").append(Xml.escape(change.toString())).append("</LastChange></e:property>");
        }
        return x.append("</e:propertyset>").toString();
    }

    public static Map<String, String> values(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }

    public static String time(long millis) {
        long s = Math.max(0, millis / 1000);
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }

    /** Parses H+:MM:SS[.fff] as well as the sloppy forms senders actually send (MM:SS, plain seconds). */
    public static long millis(String value) throws Fault {
        try {
            String v = value.trim();
            String[] p = v.split(":");
            double seconds;
            if (p.length == 3) seconds = Integer.parseInt(p[0].trim()) * 3600.0 + Integer.parseInt(p[1].trim()) * 60.0 + Double.parseDouble(p[2].trim());
            else if (p.length == 2) seconds = Integer.parseInt(p[0].trim()) * 60.0 + Double.parseDouble(p[1].trim());
            else if (p.length == 1) seconds = Double.parseDouble(p[0]);
            else throw new IllegalArgumentException();
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > 9999 * 3600.0) throw new IllegalArgumentException();
            return (long) (seconds * 1000);
        } catch (Exception e) { throw new Fault(711, "Illegal seek target"); }
    }
}
