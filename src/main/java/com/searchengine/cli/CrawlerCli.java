
package com.searchengine.cli;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CrawlerCli {

    static final String UA = "SearchITBot/1.0";

    /*
     * Exit codes:
     * 0 = Success
     * 2 = Invalid or blocked input
     * 3 = No pages fetched
     * 4 = Partial success
     */

    public static void main(String[] args) throws Exception {

        Map<String, String> options = new HashMap<>();

        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i], args[i + 1]);
        }

        String url = options.get("--url");

        int maxPages = Math.max(
                1,
                Math.min(
                        Integer.parseInt(options.getOrDefault("--max-pages", "5")),
                        10
                )
        );

        int timeout = Math.max(
                1000,
                Integer.parseInt(options.getOrDefault("--timeout-ms", "10000"))
        );

        Path output = Path.of(
                options.getOrDefault("--out", "result.json")
        );

        URI startUri;

        // Validate starting URL
        try {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException();
            }

            startUri = URI.create(url);

            if (startUri.getHost() == null ||
                    startUri.getScheme() == null ||
                    !(startUri.getScheme().equalsIgnoreCase("http") ||
                            startUri.getScheme().equalsIgnoreCase("https"))) {

                throw new IllegalArgumentException();
            }

            // Block private and local IP addresses
            InetAddress[] addresses =
                    InetAddress.getAllByName(startUri.getHost());

            for (InetAddress ip : addresses) {

                if (ip.isLoopbackAddress()
                        || ip.isSiteLocalAddress()
                        || ip.isLinkLocalAddress()
                        || ip.isAnyLocalAddress()
                        || ip.isMulticastAddress()) {

                    System.err.println(
                            "Blocked: private or local address"
                    );

                    System.exit(2);
                    return;
                }
            }

        } catch (Exception e) {

            System.err.println("Invalid URL");
            System.exit(2);
            return;
        }

        // Read robots.txt rules
        List<String> disallowed = robots(startUri, timeout);

        List<String> pages = new ArrayList<>();

        Deque<String> queue = new ArrayDeque<>();
        queue.add(url);

        Set<String> seen = new HashSet<>();

        int failures = 0;

        // Breadth-first crawling
        while (!queue.isEmpty() && pages.size() < maxPages) {

            String currentUrl = queue.poll();

            if (!seen.add(currentUrl)) {
                continue;
            }

            URI currentUri;

            try {
                currentUri = URI.create(currentUrl);
            } catch (Exception e) {
                failures++;
                continue;
            }

            String path = currentUri.getPath();

            // Respect robots.txt disallow rules
            if (path != null &&
                    disallowed.stream().anyMatch(path::startsWith)) {

                System.err.println(
                        "Skipped (robots.txt): " + currentUrl
                );

                continue;
            }

            try {

                Document document = Jsoup.connect(currentUrl)
                        .userAgent(UA)
                        .timeout(timeout)
                        .get();

                String text = document.body().text();

                List<String> headings =
                        document.select("h1,h2,h3").eachText();

                List<String> limitedHeadings =
                        headings.subList(
                                0,
                                Math.min(headings.size(), 15)
                        );

                int wordCount = text.isBlank()
                        ? 0
                        : text.trim().split("\\s+").length;

                String excerpt = text.substring(
                        0,
                        Math.min(1500, text.length())
                );

                // Build page JSON
                String pageJson =
                        "{"
                                + "\"url\":" + q(currentUrl)
                                + ",\"title\":" + q(document.title())
                                + ",\"description\":" +
                                q(document.select(
                                        "meta[name=description]"
                                ).attr("content"))
                                + ",\"headings\":" + arr(limitedHeadings)
                                + ",\"wordCount\":" + wordCount
                                + ",\"textExcerpt\":" + q(excerpt)
                                + "}";

                pages.add(pageJson);

                System.out.println(
                        "Fetched: " + currentUrl
                );

                // Discover same-domain links
                for (var link : document.select("a[href]")) {

                    String absoluteUrl = link.absUrl("href");

                    try {

                        if (absoluteUrl.isBlank()) {
                            continue;
                        }

                        URI linkUri = URI.create(absoluteUrl);

                        if (linkUri.getHost() != null
                                && linkUri.getScheme() != null
                                && (linkUri.getScheme().equalsIgnoreCase("http")
                                || linkUri.getScheme().equalsIgnoreCase("https"))
                                && startUri.getHost().equalsIgnoreCase(
                                linkUri.getHost()
                        )) {

                            String cleanUrl = linkUri
                                    .toString()
                                    .split("#", 2)[0];

                            if (!seen.contains(cleanUrl)) {
                                queue.add(cleanUrl);
                            }
                        }

                    } catch (Exception ignored) {
                    }
                }

            } catch (Exception e) {

                failures++;

                System.err.println(
                        "Failed: " + currentUrl
                                + " - " + e.getMessage()
                );
            }
        }

        // No pages fetched
        if (pages.isEmpty()) {

            System.err.println("No pages fetched");

            System.exit(3);
            return;
        }

        // Build final JSON
        String json =
                "{"
                        + "\"startUrl\":" + q(url)
                        + ",\"pagesCrawled\":" + pages.size()
                        + ",\"failures\":" + failures
                        + ",\"pages\":[" + String.join(",", pages) + "]"
                        + "}";

        // Save result
        if (output.toAbsolutePath().getParent() != null) {
            Files.createDirectories(
                    output.toAbsolutePath().getParent()
            );
        }

        Files.writeString(output, json);

        System.out.println(
                "OK " + pages.size() + " pages"
        );

        // Return appropriate exit code
        System.exit(failures > 0 ? 4 : 0);
    }

    // Read robots.txt
    static List<String> robots(URI uri, int timeout) {

        List<String> rules = new ArrayList<>();

        try {

            String robotsUrl =
                    uri.getScheme() + "://" +
                            uri.getRawAuthority() +
                            "/robots.txt";

            String content = Jsoup.connect(robotsUrl)
                    .userAgent(UA)
                    .timeout(timeout)
                    .ignoreContentType(true)
                    .execute()
                    .body();

            boolean applies = false;

            for (String line : content.split("\\R")) {

                String trimmed = line.trim();

                if (trimmed.toLowerCase().startsWith("user-agent:")) {

                    applies = trimmed.substring(11)
                            .trim()
                            .equals("*");

                } else if (applies &&
                        trimmed.toLowerCase().startsWith("disallow:")) {

                    String path = trimmed.substring(9).trim();

                    if (!path.isEmpty()) {
                        rules.add(path);
                    }
                }
            }

        } catch (IOException ignored) {
        }

        return rules;
    }

    // Escape strings for valid JSON
    static String q(String value) {

        StringBuilder builder = new StringBuilder("\"");

        if (value == null) {
            value = "";
        }

        for (char c : value.toCharArray()) {

            switch (c) {

                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");

                default -> {

                    if (c < 0x20) {

                        builder.append("\\u");

                        String hex = Integer.toHexString(c);

                        builder.append(
                                "0".repeat(4 - hex.length())
                        );

                        builder.append(hex);

                    } else {
                        builder.append(c);
                    }
                }
            }
        }

        return builder.append('"').toString();
    }

    // Convert list into JSON array
    static String arr(List<String> values) {

        StringBuilder builder = new StringBuilder("[");

        for (int i = 0; i < values.size(); i++) {

            if (i > 0) {
                builder.append(',');
            }

            builder.append(q(values.get(i)));
        }

        return builder.append(']').toString();
    }
}
