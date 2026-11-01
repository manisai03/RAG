package com.aiinterview.assistant.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cuts syllabus PDF text to a single theme block (e.g. Theme I only) so RAG does not
 * retrieve Theme II economics content for geography questions.
 */
@Service
public class SyllabusThemeTextService {

    private static final String[] THEME_I_STARTS = {
            "theme - i:", "theme - i :", "theme-i:", "theme – i:", "theme – i :",
            "theme i:", "theme i :", "theme-i :", "theme - i", "theme-i", "theme – i"
    };

    private static final String[] THEME_II_STARTS = {
            "theme - ii:", "theme - ii :", "theme-ii:", "theme – ii:", "theme – ii :",
            "theme ii:", "theme ii :", "theme-ii :", "theme - ii", "theme-ii", "theme – ii"
    };

    private static final String[] THEME_III_STARTS = {
            "theme - iii:", "theme-iii:", "theme iii:", "theme - iii", "theme-iii"
    };

    /** Theme I sub-topics in syllabus order (PGT Social Studies). */
    private static final List<ThemeSubSectionMarker> THEME_ONE_SUBSECTIONS = List.of(
            new ThemeSubSectionMarker(
                    "Maps, directions, scale and atlas",
                    "reading, making and analysis of maps",
                    "making and analysis of maps",
                    "analysis of maps",
                    "different types of maps",
                    "conventional symbols use in maps",
                    "contour lines"
            ),
            new ThemeSubSectionMarker(
                    "Universe, Sun and Earth",
                    "our universe, the sun and the earth",
                    "our universe",
                    "the sun and the earth",
                    "evolution of the earth"
            ),
            new ThemeSubSectionMarker(
                    "Lithosphere",
                    "lithosphere",
                    "first order' landforms",
                    "plate tectonics"
            ),
            new ThemeSubSectionMarker(
                    "Hydrosphere",
                    "hydrosphere",
                    "hydrological cycle",
                    "rivers in india"
            ),
            new ThemeSubSectionMarker(
                    "Atmosphere",
                    "atmosphere",
                    "insolation and heat budget",
                    "planetary winds"
            ),
            new ThemeSubSectionMarker(
                    "Biosphere",
                    "biosphere",
                    "natural vegetation",
                    "forests in india"
            )
    );

    public int resolveThemeNumber(String query) {
        if (!StringUtils.hasText(query)) {
            return 1;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "theme iii", "theme 3", "theme-iii", "theme - iii")) {
            return 3;
        }
        if (containsAny(lower, "theme ii", "theme 2", "theme-ii", "theme - ii", "livelihood", "production")) {
            return 2;
        }
        return 1;
    }

    public boolean isThemeOneQuery(String query) {
        return resolveThemeNumber(query) == 1;
    }

    /**
     * Returns only the text for the requested theme, or the original text if markers are not found.
     */
    public String narrowToTheme(String fullText, String query) {
        if (!StringUtils.hasText(fullText)) {
            return fullText;
        }
        return extractThemeSection(fullText, resolveThemeNumber(query));
    }

    /**
     * Splits Theme I text at syllabus sub-headings (maps, universe, lithosphere, etc.)
     * so each section gets the correct paragraphs instead of arbitrary chunk slices.
     */
    public Map<String, String> splitThemeOneIntoSections(String themeOneText) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (!StringUtils.hasText(themeOneText)) {
            return sections;
        }

        String lower = themeOneText.toLowerCase(Locale.ROOT);
        List<LocatedSection> located = new ArrayList<>();

        for (ThemeSubSectionMarker marker : THEME_ONE_SUBSECTIONS) {
            int position = indexOfEarliest(lower, marker.startPatterns());
            if (position >= 0) {
                located.add(new LocatedSection(marker.title(), position));
            }
        }

        if (located.size() < 3) {
            return sections;
        }

        located.sort(Comparator.comparingInt(LocatedSection::position));

        for (int index = 0; index < located.size(); index++) {
            LocatedSection current = located.get(index);
            int end = index + 1 < located.size()
                    ? located.get(index + 1).position()
                    : themeOneText.length();
            String sectionText = themeOneText.substring(current.position(), end).trim();
            if (StringUtils.hasText(sectionText)) {
                sections.put(current.title(), sectionText);
            }
        }

        return sections;
    }

    public String extractThemeSection(String fullText, int themeNumber) {
        String lower = fullText.toLowerCase(Locale.ROOT);

        String[] startPatterns = switch (themeNumber) {
            case 2 -> THEME_II_STARTS;
            case 3 -> THEME_III_STARTS;
            default -> THEME_I_STARTS;
        };
        String[] endPatterns = switch (themeNumber) {
            case 1 -> THEME_II_STARTS;
            case 2 -> THEME_III_STARTS;
            default -> new String[0];
        };

        int start = indexOfEarliest(lower, startPatterns);
        if (start < 0) {
            return fullText;
        }

        if (endPatterns.length == 0) {
            return fullText.substring(start).trim();
        }

        int searchFrom = Math.min(start + 20, lower.length());
        String tail = lower.substring(searchFrom);
        int relativeEnd = indexOfEarliest(tail, endPatterns);
        if (relativeEnd < 0) {
            return fullText.substring(start).trim();
        }

        int end = searchFrom + relativeEnd;
        return fullText.substring(start, end).trim();
    }

    private int indexOfEarliest(String text, String[] patterns) {
        int earliest = -1;
        for (String pattern : patterns) {
            int index = text.indexOf(pattern);
            if (index >= 0 && (earliest < 0 || index < earliest)) {
                earliest = index;
            }
        }
        return earliest;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private record ThemeSubSectionMarker(String title, String... startPatterns) {}

    private record LocatedSection(String title, int position) {}
}
