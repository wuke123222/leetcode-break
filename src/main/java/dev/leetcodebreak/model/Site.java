package dev.leetcodebreak.model;

import java.net.URI;
import java.util.Locale;

public enum Site {
    CN("力扣中国", "leetcode.cn"), GLOBAL("LeetCode 国际", "leetcode.com");
    public final String label;
    public final String host;
    Site(String label, String host) { this.label = label; this.host = host; }
    public String origin() { return "https://" + host; }
    public String problemSet() { return origin() + "/problemset/"; }
    public String login() { return origin() + "/accounts/login/"; }
    public String problem(String slug) {
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw new IllegalArgumentException("Invalid problem slug");
        return origin() + "/problems/" + slug + "/description/";
    }
    public boolean ownsDomain(String domain) {
        if (domain == null) return false;
        String normalized = domain.toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) normalized = normalized.substring(1);
        return normalized.equals(host) || normalized.endsWith("." + host);
    }
    public static boolean isHttps(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }
    @Override public String toString() { return label; }
}
