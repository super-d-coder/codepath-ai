package com.superd.infrastructure.gateway.infoflow;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在保持 Markdown/GFM 结构的前提下，补齐 GitHub README 中依赖仓库上下文的相对 URL。
 */
@Component
public class GitHubReadmeNormalizer {

    private static final Pattern MARKDOWN_TARGET = Pattern.compile("(!?\\[[^\\]]*])\\(([^)]+)\\)");
    private static final Pattern HTML_ATTRIBUTE = Pattern.compile("(?i)(\\b(?:src|href)\\s*=\\s*)([\"'])(.*?)(\\2)");
    private static final Set<String> SAFE_SCHEMES = Set.of("http", "https", "mailto", "data");

    public String normalize(String content, String fullName, String branch, String readmePath) {
        if (content == null || content.isEmpty()) return content;
        String normalized = normalizeMarkdownTargets(content, fullName, branch, readmePath);
        return normalizeHtmlAttributes(normalized, fullName, branch, readmePath);
    }

    private String normalizeMarkdownTargets(String content, String fullName, String branch, String readmePath) {
        Matcher matcher = MARKDOWN_TARGET.matcher(content);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            boolean image = matcher.group(1).startsWith("!");
            Target target = splitTarget(matcher.group(2));
            String normalizedUrl = normalizeUrl(target.url, fullName, branch, readmePath, image);
            String replacement = matcher.group(1) + "(" + normalizedUrl + target.suffix + ")";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String normalizeHtmlAttributes(String content, String fullName, String branch, String readmePath) {
        Matcher matcher = HTML_ATTRIBUTE.matcher(content);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            boolean image = matcher.group(1).toLowerCase(Locale.ROOT).contains("src");
            String normalizedUrl = normalizeUrl(matcher.group(3), fullName, branch, readmePath, image);
            String replacement = matcher.group(1) + matcher.group(2) + normalizedUrl + matcher.group(4);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String normalizeUrl(String value, String fullName, String branch, String readmePath, boolean image) {
        String url = value == null ? "" : value.trim();
        if (url.isEmpty() || url.startsWith("#")) return url;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("vbscript:") || lower.startsWith("file:")) return "#";
        if (hasScheme(url) || url.startsWith("//")) return isSafeScheme(url) ? url : "#";

        String pathPart = url;
        String fragment = "";
        int hash = url.indexOf('#');
        if (hash >= 0) {
            pathPart = url.substring(0, hash);
            fragment = url.substring(hash);
        }
        String baseDirectory = readmeDirectory(readmePath);
        String resolvedPath = resolvePath(baseDirectory, pathPart);
        if (image) {
            return "https://raw.githubusercontent.com/" + fullName + "/" + branch + "/" + resolvedPath + fragment;
        }
        return "https://github.com/" + fullName + "/blob/" + branch + "/" + resolvedPath + fragment;
    }

    private boolean hasScheme(String value) {
        return value.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*");
    }

    private boolean isSafeScheme(String value) {
        if (value.startsWith("//")) return true;
        try {
            String scheme = URI.create(value).getScheme();
            return scheme != null && SAFE_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT));
        } catch (Exception exception) {
            return false;
        }
    }

    private String readmeDirectory(String readmePath) {
        if (readmePath == null) return "";
        String path = readmePath.replace('\\', '/');
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash + 1);
    }

    private String resolvePath(String baseDirectory, String relativePath) {
        String path = relativePath.replace('\\', '/');
        if (path.startsWith("/")) path = path.substring(1);
        URI base = URI.create("https://github.invalid/" + baseDirectory);
        String resolved = base.resolve(path).getPath();
        return resolved.startsWith("/") ? resolved.substring(1) : resolved;
    }

    private Target splitTarget(String rawTarget) {
        String target = rawTarget.trim();
        if (target.startsWith("<")) {
            int end = target.indexOf('>');
            if (end > 0) return new Target(target.substring(1, end), target.substring(end + 1));
        }
        int whitespace = findWhitespace(target);
        return whitespace < 0 ? new Target(target, "") : new Target(target.substring(0, whitespace), target.substring(whitespace));
    }

    private int findWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) if (Character.isWhitespace(value.charAt(i))) return i;
        return -1;
    }

    private record Target(String url, String suffix) { }
}
