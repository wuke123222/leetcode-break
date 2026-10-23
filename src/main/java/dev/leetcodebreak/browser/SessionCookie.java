package dev.leetcodebreak.browser;

import dev.leetcodebreak.model.Site;
import java.io.*;
import java.util.*;

/** Values live only in Password Safe; never in plugin XML, logs, or project files. */
public record SessionCookie(String name, String value, String domain, String path,
                            boolean secure, boolean httpOnly, long expiresAt) {
    public static final Set<String> AUTH_NAMES = Set.of("LEETCODE_SESSION", "csrftoken");
    public boolean validFor(Site site, long now) {
        return AUTH_NAMES.contains(name) && value != null && !value.isEmpty() && value.length() <= 16384
            && site.ownsDomain(domain) && path != null && path.startsWith("/") && expiresAt > now;
    }
    public static String encode(List<SessionCookie> cookies) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(1); out.writeInt(cookies.size());
            for (SessionCookie c : cookies) {
                out.writeUTF(c.name); out.writeUTF(c.value); out.writeUTF(c.domain); out.writeUTF(c.path);
                out.writeBoolean(c.secure); out.writeBoolean(c.httpOnly); out.writeLong(c.expiresAt);
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    public static List<SessionCookie> decode(String value, Site site, long now) throws IOException {
        if (value == null) return List.of();
        if (value.length() > 256_000) throw new IOException("Session data exceeds limit");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(value); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid session encoding"); }
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) throw new IOException("Unknown session version");
            int count = in.readInt();
            if (count < 0 || count > 32) throw new IOException("Invalid cookie count");
            var result = new ArrayList<SessionCookie>();
            for (int i = 0; i < count; i++) {
                var c = new SessionCookie(in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readBoolean(), in.readBoolean(), in.readLong());
                if (c.validFor(site, now)) result.add(c);
            }
            if (in.available() != 0) throw new IOException("Trailing session data");
            return List.copyOf(result);
        }
    }
}
