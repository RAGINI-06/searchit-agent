package com.searchengine.service;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class CliRunnerService {

    public record CliResult(String status, int exitCode, String stdout, String stderr,
                            String outputJson, long durationMs, boolean timedOut) {}

    private static final int MAX_PAGES_CAP = 10;
    private static final int MAX_LOG_CHARS = 4000;

    public CliResult run(String url, int maxPages, int timeoutSec) {
        long start = System.currentTimeMillis();
        try {
            validateUrl(url);
        } catch (IllegalArgumentException e) {
            return new CliResult("failed", 2, "", e.getMessage(), null, 0, false);
        }
        maxPages = Math.max(1, Math.min(maxPages, MAX_PAGES_CAP));

        Path outFile = null;
        try {
            outFile = Files.createTempFile("crawl-", ".json");
            ProcessBuilder pb = new ProcessBuilder(buildCommand(url, maxPages, outFile));
            // least privilege: do not hand secrets (DB password, API keys) to the child process
            pb.environment().keySet().removeIf(k -> {
                String u = k.toUpperCase();
                return u.startsWith("SPRING_") || u.contains("KEY") || u.contains("SECRET")
                        || u.contains("PASSWORD") || u.contains("TOKEN");
            });

            Process p = pb.start();
            CompletableFuture<String> out = read(p.getInputStream());
            CompletableFuture<String> err = read(p.getErrorStream());

            boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                p.waitFor(2, TimeUnit.SECONDS);
                return new CliResult("failed", -1, trim(out.getNow("")),
                        "Timed out after " + timeoutSec + "s and the process was killed.",
                        null, System.currentTimeMillis() - start, true);
            }

            int code = p.exitValue();
            String json = Files.exists(outFile) && Files.size(outFile) > 0
                    ? Files.readString(outFile, StandardCharsets.UTF_8) : null;
            String status = switch (code) {
                case 0 -> "success";
                case 4 -> "partial";
                default -> "failed";
            };
            if (json == null && !status.equals("failed")) status = "failed"; // never claim success without evidence
            return new CliResult(status, code, trim(out.get(2, TimeUnit.SECONDS)),
                    trim(err.get(2, TimeUnit.SECONDS)), json, System.currentTimeMillis() - start, false);
        } catch (Exception e) {
            return new CliResult("failed", -2, "", "Could not run CLI: " + e.getMessage(),
                    null, System.currentTimeMillis() - start, false);
        } finally {
            if (outFile != null) try { Files.deleteIfExists(outFile); } catch (Exception ignored) {}
        }
    }

    /** Re-launch the same JVM with the same classpath, so it works in IntelliJ, mvn and the Docker jar. */
    private List<String> buildCommand(String url, int maxPages, Path outFile) {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String cp = System.getProperty("java.class.path");
        List<String> cmd = new ArrayList<>(List.of(javaBin, "-Xmx256m"));
        if (cp.endsWith(".jar") && !cp.contains(java.io.File.pathSeparator)) {
            // fat jar (Docker/production): go through the Spring Boot launcher
            cmd.add("-Dloader.main=com.searchengine.cli.CrawlerCli");
            cmd.addAll(List.of("-cp", cp, "org.springframework.boot.loader.launch.PropertiesLauncher"));
        } else {
            // IDE / mvn: plain classpath
            cmd.addAll(List.of("-cp", cp, "com.searchengine.cli.CrawlerCli"));
        }
        cmd.addAll(List.of("--url", url, "--max-pages", String.valueOf(maxPages),
                "--timeout-ms", "10000", "--out", outFile.toString()));
        return cmd;
    }
    private static final String File_PATH_SEP = java.io.File.pathSeparator;

    private static void validateUrl(String url) {
        try {
            URI u = URI.create(url == null ? "" : url.trim());
            String s = u.getScheme();
            if (u.getHost() == null || s == null || !(s.equals("http") || s.equals("https")))
                throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL: only http/https URLs are allowed.");
        }
    }

    private static CompletableFuture<String> read(InputStream in) {
        return CompletableFuture.supplyAsync(() -> {
            try { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
            catch (Exception e) { return ""; }
        });
    }

    private static String trim(String s) {
        return s.length() > MAX_LOG_CHARS ? s.substring(0, MAX_LOG_CHARS) + "…[truncated]" : s;
    }
}