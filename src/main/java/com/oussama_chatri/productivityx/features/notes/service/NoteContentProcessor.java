package com.oussama_chatri.productivityx.features.notes.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Strips Markdown syntax to produce the plain-text mirror stored in
 * {@code plain_text_content}. Used for search previews, word count, and
 * PostgreSQL FTS indexing via the {@code search_vector} trigger.
 */
@Component
class NoteContentProcessor {

    private static final int    WORDS_PER_MINUTE    = 200;
    private static final Pattern MARKDOWN_SYNTAX    = Pattern.compile(
            "(!?\\[([^\\]]*)]\\([^)]*\\))" +   // links and images
                    "|(```[\\s\\S]*?```)" +              // fenced code blocks
                    "|(`.+?`)" +                         // inline code
                    "|(^#{1,6}\\s)" +                   // headings
                    "|(\\*{1,3}|_{1,3})" +              // bold/italic markers
                    "|(~~.+?~~)" +                       // strikethrough
                    "|(^[-*+]\\s|^\\d+\\.\\s)" +        // list markers
                    "|(^>+\\s?)" +                       // blockquotes
                    "|(^---+$|^===+$)" +                // horizontal rules
                    "|(\\n{2,})"                         // multiple newlines
    );
    private static final Pattern EXTRA_WHITESPACE   = Pattern.compile("\\s{2,}");

    String toPlainText(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        String stripped = MARKDOWN_SYNTAX.matcher(markdown).replaceAll(" ");
        return EXTRA_WHITESPACE.matcher(stripped).replaceAll(" ").trim();
    }

    int wordCount(String plainText) {
        if (plainText == null || plainText.isBlank()) return 0;
        return plainText.split("\\s+").length;
    }

    int readingTimeSeconds(int wordCount) {
        if (wordCount == 0) return 0;
        double minutes = (double) wordCount / WORDS_PER_MINUTE;
        return (int) Math.max(1, Math.ceil(minutes * 60));
    }
}