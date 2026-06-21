package com.agentplatform.tool;

import java.net.InetAddress;
import java.net.URI;

/**
 * Shared SSRF guard for any server-side outbound URL (HTTP tools, MCP servers).
 * Unless explicitly allowed, rejects URLs that resolve to loopback / link-local
 * (incl. 169.254.169.254 cloud metadata) / private / any-local / multicast addresses.
 */
public final class SsrfGuard {

    private SsrfGuard() {}

    /** Returns an error message if the URL should be blocked, or null if it is allowed. */
    public static String check(String url, boolean allowPrivateNetwork) {
        if (allowPrivateNetwork) return null;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
                return "unsupported url scheme";
            }
            String host = uri.getHost();
            if (host == null) return "invalid url host";
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()
                        || addr.isMulticastAddress()) {
                    return "blocked internal address: " + host;
                }
            }
            return null;
        } catch (Exception e) {
            return "url resolution failed: " + e.getMessage();
        }
    }
}
