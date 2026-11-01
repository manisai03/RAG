package com.aiinterview.assistant.service;

import com.aiinterview.assistant.config.TextChunkingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TextChunkingService {

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LIST_ITEM = Pattern.compile("^([-•*▪●◦]|\\d{1,3}[.)]|[a-zA-Z][.)])\\s+");
    private static final Pattern NUMBERED_HEADING = Pattern.compile(
            "^((\\d+(\\.\\d+)*)|([IVXLC]+[.)])|(chapter|unit|theme|section|part|module)\\b)",
            Pattern.CASE_INSENSITIVE);

    private final TextChunkingProperties textChunkingProperties;

    public record TextChunk(String text, Integer pageNumber) {}

    /**
     * Chunks page texts while keeping paragraphs, list items and headings intact. A new chunk starts at a
     * heading once the current chunk is at least half full; otherwise chunks split by size and carry an
     * overlap from the previous chunk.
     */
    public List<TextChunk> chunkPages(List<String> pages) {
        int chunkSize = textChunkingProperties.getChunkSize();
        int overlap = Math.max(0, Math.min(textChunkingProperties.getChunkOverlap(), chunkSize / 2));

        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Integer currentPage = null;

        for (Block block : toBlocks(pages)) {
            List<String> pieces = block.text().length() > chunkSize
                    ? splitOversizedSegment(block.text(), chunkSize - overlap)
                    : List.of(block.text());

            for (String piece : pieces) {
                boolean sectionBreak = block.heading() && current.length() >= chunkSize / 2;
                boolean overflow = !current.isEmpty() && current.length() + 1 + piece.length() > chunkSize;

                if (sectionBreak || overflow) {
                    String finished = current.toString().trim();
                    chunks.add(new TextChunk(finished, currentPage));
                    current.setLength(0);
                    currentPage = null;
                    if (!sectionBreak) {
                        current.append(overlapTail(finished, overlap));
                    }
                }

                if (currentPage == null) {
                    currentPage = block.page();
                }
                if (!current.isEmpty()) {
                    current.append('\n');
                }
                current.append(piece);
            }
        }

        if (!current.toString().isBlank()) {
            chunks.add(new TextChunk(current.toString().trim(), currentPage));
        }

        log.debug("Split {} pages into {} chunks (size {}, overlap {})", pages.size(), chunks.size(), chunkSize, overlap);
        return List.copyOf(chunks);
    }

    private List<Block> toBlocks(List<String> pages) {
        List<Block> blocks = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            String pageText = pages.get(pageIndex);
            if (!StringUtils.hasText(pageText)) {
                continue;
            }
            int page = pageIndex + 1;
            StringBuilder paragraph = new StringBuilder();
            String previousLine = "";

            for (String rawLine : pageText.split("\n")) {
                String line = WHITESPACE.matcher(rawLine.trim()).replaceAll(" ");
                if (line.isEmpty()) {
                    flushParagraph(blocks, paragraph, page);
                    previousLine = "";
                    continue;
                }

                boolean heading = looksLikeHeading(line);
                if (heading) {
                    flushParagraph(blocks, paragraph, page);
                    blocks.add(new Block(line, page, true));
                    previousLine = "";
                    continue;
                }

                boolean startsNewBlock = paragraph.isEmpty()
                        || LIST_ITEM.matcher(line).find()
                        || endsSentence(previousLine);
                if (startsNewBlock) {
                    flushParagraph(blocks, paragraph, page);
                    paragraph.append(line);
                } else {
                    paragraph.append(' ').append(line);
                }
                previousLine = line;
            }
            flushParagraph(blocks, paragraph, page);
        }
        return blocks;
    }

    private void flushParagraph(List<Block> blocks, StringBuilder paragraph, int page) {
        if (!paragraph.isEmpty()) {
            blocks.add(new Block(paragraph.toString(), page, false));
            paragraph.setLength(0);
        }
    }

    private boolean looksLikeHeading(String line) {
        if (line.length() > 80 || line.length() < 3) {
            return false;
        }
        char last = line.charAt(line.length() - 1);
        if (last == '.' || last == ',' || last == ';') {
            return false;
        }
        if (last == ':') {
            return true;
        }
        long letters = line.chars().filter(Character::isLetter).count();
        boolean allCaps = letters >= 3 && line.equals(line.toUpperCase());
        return allCaps || (NUMBERED_HEADING.matcher(line).find() && line.split(" ").length <= 10);
    }

    private boolean endsSentence(String line) {
        if (line.isEmpty()) {
            return false;
        }
        char last = line.charAt(line.length() - 1);
        return last == '.' || last == '!' || last == '?' || last == ':';
    }

    private String overlapTail(String text, int overlap) {
        if (overlap <= 0 || text.length() <= overlap) {
            return "";
        }
        String tail = text.substring(text.length() - overlap);
        int firstSpace = tail.indexOf(' ');
        return firstSpace >= 0 ? tail.substring(firstSpace + 1).trim() : tail.trim();
    }

    private record Block(String text, int page, boolean heading) {}

    public List<String> chunkText(String text) {
        return chunkText(text, textChunkingProperties.getChunkSize());
    }

    public List<String> chunkText(String text, int chunkSize) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be greater than zero");
        }

        String normalizedText = normalizeText(text);
        if (normalizedText.length() <= chunkSize) {
            return List.of(normalizedText);
        }

        List<String> sentences = splitIntoSentences(normalizedText);
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            if (sentence.length() > chunkSize) {
                appendCurrentChunk(chunks, currentChunk);
                chunks.addAll(splitOversizedSegment(sentence, chunkSize));
                continue;
            }

            if (currentChunk.isEmpty()) {
                currentChunk.append(sentence);
                continue;
            }

            int projectedLength = currentChunk.length() + 1 + sentence.length();
            if (projectedLength <= chunkSize) {
                currentChunk.append(' ').append(sentence);
            } else {
                appendCurrentChunk(chunks, currentChunk);
                currentChunk.append(sentence);
            }
        }

        appendCurrentChunk(chunks, currentChunk);
        log.debug("Split text of {} characters into {} chunks (max size: {})",
                normalizedText.length(), chunks.size(), chunkSize);

        return List.copyOf(chunks);
    }

    private List<String> splitIntoSentences(String text) {
        String[] parts = SENTENCE_BOUNDARY.split(text.trim());
        List<String> sentences = new ArrayList<>();

        for (String part : parts) {
            String sentence = part.trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }

        if (sentences.isEmpty()) {
            sentences.add(text.trim());
        }

        return sentences;
    }

    private List<String> splitOversizedSegment(String segment, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        String remaining = segment.trim();

        while (remaining.length() > chunkSize) {
            int splitIndex = findSplitIndex(remaining, chunkSize);
            chunks.add(remaining.substring(0, splitIndex).trim());
            remaining = remaining.substring(splitIndex).trim();
        }

        if (!remaining.isEmpty()) {
            chunks.add(remaining);
        }

        return chunks;
    }

    private int findSplitIndex(String text, int chunkSize) {
        if (text.length() <= chunkSize) {
            return text.length();
        }

        int sentenceBoundary = findLastSentenceBoundary(text, chunkSize);
        if (sentenceBoundary > 0) {
            return sentenceBoundary;
        }

        int wordBoundary = findLastWordBoundary(text, chunkSize);
        if (wordBoundary > 0) {
            return wordBoundary;
        }

        return chunkSize;
    }

    private int findLastSentenceBoundary(String text, int maxLength) {
        int searchStart = Math.max(0, maxLength - (maxLength / 4));
        for (int index = maxLength - 1; index >= searchStart; index--) {
            char character = text.charAt(index);
            if (character == '.' || character == '!' || character == '?') {
                return index + 1;
            }
        }
        return -1;
    }

    private int findLastWordBoundary(String text, int maxLength) {
        int searchStart = Math.max(0, maxLength - (maxLength / 5));
        for (int index = maxLength - 1; index >= searchStart; index--) {
            if (Character.isWhitespace(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private void appendCurrentChunk(List<String> chunks, StringBuilder currentChunk) {
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
            currentChunk.setLength(0);
        }
    }

    private String normalizeText(String text) {
        return WHITESPACE.matcher(text.trim()).replaceAll(" ");
    }
}
