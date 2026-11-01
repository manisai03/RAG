package com.aiinterview.assistant.service;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Content words of a question (stop words and request verbs removed) and whole-word matching against text.
 */
final class QuestionTerms {

    private static final Pattern NON_WORD = Pattern.compile("\\W+");

    private static final Set<String> STOP_WORDS = Set.of(
            "what", "when", "where", "which", "who", "whom", "whose", "why", "how",
            "the", "and", "for", "from", "with", "about", "your", "this", "that", "these", "those",
            "are", "was", "were", "been", "being", "have", "has", "had", "does", "did", "will",
            "would", "could", "should", "may", "might", "shall", "can", "into", "onto", "upon",
            "explain", "describe", "summarize", "summarise", "summary", "tell", "give", "list", "define",
            "please", "you", "me", "its", "their", "there", "than", "then", "also", "some", "any",
            "document", "pdf", "according", "mentioned", "more", "detail", "details", "brief", "briefly",
            "under", "listed", "given", "many", "much", "not", "but", "all", "per"
    );

    private QuestionTerms() {
    }

    static Set<String> extract(String question) {
        if (!StringUtils.hasText(question)) {
            return Set.of();
        }
        return NON_WORD.splitAsStream(question.toLowerCase(Locale.ROOT))
                .filter(term -> term.length() >= 3)
                .filter(term -> !STOP_WORDS.contains(term))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static int countMatches(String content, Set<String> terms) {
        if (!StringUtils.hasText(content) || terms.isEmpty()) {
            return 0;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        int matches = 0;
        for (String term : terms) {
            if (containsWholeWord(lower, term)) {
                matches++;
            }
        }
        return matches;
    }

    /** Whole-word match that also accepts the singular/plural form ("committees" matches "committee"). */
    static boolean containsWholeWord(String text, String term) {
        return Pattern.compile("\\b" + Pattern.quote(singular(term)) + "(s|es)?\\b", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find();
    }

    private static String singular(String term) {
        if (term.length() > 4 && term.endsWith("ies")) {
            return term.substring(0, term.length() - 3) + "y";
        }
        if (term.length() > 4 && term.endsWith("s") && !term.endsWith("ss")) {
            return term.substring(0, term.length() - 1);
        }
        return term;
    }
}
