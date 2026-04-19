package com.oussama_chatri.productivityx.core.util;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.regex.Pattern;

@Component
public class StringUtils {

    private static final Pattern MARKDOWN_TAGS = Pattern.compile(
            "(\\*{1,3}|_{1,3}|~{2}|`{1,3}|#{1,6}\\s|\\[|\\]\\(.*?\\)|!\\[.*?\\]\\(.*?\\)|>\\s|[-*+]\\s|\\d+\\.\\s|---+|===+)");
    private static final Pattern NON_LATIN    = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE   = Pattern.compile("[\\s]+");

    public String toSlug(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input.toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "");
        String withHyphens = WHITESPACE.matcher(normalized).replaceAll("-");
        return NON_LATIN.matcher(withHyphens).replaceAll("");
    }

    public String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 1).trim() + "…";
    }

    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String local  = parts[0];
        String domain = parts[1];
        if (local.length() <= 1) return "*@" + domain;
        return local.charAt(0) + "*".repeat(local.length() - 1) + "@" + domain;
    }

    public String capitalizeName(String name) {
        if (name == null || name.isBlank()) return "";
        return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase();
    }

    public String stripMarkdown(String markdown) {
        if (markdown == null) return "";
        return MARKDOWN_TAGS.matcher(markdown).replaceAll("").trim();
    }
}
