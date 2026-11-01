package com.aiinterview.assistant.service;

import com.aiinterview.assistant.config.OllamaAiProperties;
import com.aiinterview.assistant.config.QdrantProperties;
import com.aiinterview.assistant.config.RagProperties;
import com.aiinterview.assistant.dto.ChatTurn;
import com.aiinterview.assistant.dto.ChunkSearchResult;
import com.aiinterview.assistant.dto.RagAnswerResponse;
import com.aiinterview.assistant.dto.VectorSearchResponse;
import com.aiinterview.assistant.entity.User;
import com.aiinterview.assistant.exception.BadRequestException;
import com.aiinterview.assistant.exception.RagServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetrievalAugmentedGenerationService {

    private static final String SUMMARY_SYSTEM_PROMPT = """
            You summarize document excerpts. Use only the text provided.
            Write clear bullet points and keep names, numbers and terms exactly as written.
            Do not mention "excerpts" or "sources", do not refuse, and do not ask for clarification.
            """;

    private static final Pattern SUMMARY_REQUEST = Pattern.compile(
            "\\b(summari[sz]e|summary|overview|key points|main points|main topics|what is (this|the) (document|pdf|file) about)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern THEME_REQUEST = Pattern.compile(
            "\\btheme\\b|\\bdiversity\\b.*\\bearth\\b", Pattern.CASE_INSENSITIVE);

    /** Words that do not narrow a summary request to a topic ("summarize the whole document"). */
    private static final Set<String> GENERIC_SUMMARY_WORDS = Set.of(
            "whole", "entire", "full", "main", "key", "points", "overview", "topics", "topic", "chapter",
            "file", "content", "contents", "notes", "important", "everything", "all", "short", "simple",
            "words", "bullet", "bullets", "about", "covered", "uploaded"
    );

    private static final Pattern FOLLOW_UP_CUE = Pattern.compile(
            "\\b(it|its|this|that|they|them|their|those|these|he|she|his|her|more|elaborate|further|also|same|above|again)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CITATION = Pattern.compile("\\[Source\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern NOT_ENOUGH_INFO = Pattern.compile(
            "(do not|don't|does not|doesn't) (have|contain|provide) (enough|sufficient|any)? ?information|not enough information",
            Pattern.CASE_INSENSITIVE);

    /** Share of question words a passage must contain to be quoted when the model declines to answer. */
    private static final double STRONG_KEYWORD_SHARE = 0.6;

    private static final Pattern PASSAGE_SPLIT = Pattern.compile("\\n+|\\s[·•]\\s|(?<=[.;])\\s+");

    private static final Pattern CAPITALIZED_WORD = Pattern.compile("\\b[A-Z][a-z]{3,}\\b");

    private static final Set<String> COMMON_CAPITALIZED = Set.of(
            "source", "sources", "page", "according", "document", "however", "therefore", "also", "this",
            "that", "these", "those", "there", "here", "what", "when", "where", "which", "while", "with",
            "from", "they", "their", "then", "than", "some", "most", "many", "each", "both", "such",
            "first", "second", "third", "finally", "overall", "note", "example", "answer", "question",
            "based", "summary", "yes", "the", "and", "for", "in", "key", "main", "important", "other",
            "english", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "january", "february", "march", "april", "june", "july", "august", "september", "october",
            "november", "december", "earlier", "conversation", "excerpt", "excerpts", "text"
    );

    private final DocumentVectorStoreService documentVectorStoreService;
    private final AiChatService aiChatService;
    private final RagProperties ragProperties;
    private final QdrantProperties qdrantProperties;
    private final OllamaAiProperties ollamaAiProperties;

    public RagAnswerResponse ask(User user, String question, Long documentId) {
        return ask(user, question, documentId, List.of());
    }

    public RagAnswerResponse ask(User user, String question, Long documentId, List<ChatTurn> history) {
        if (!StringUtils.hasText(question)) {
            throw new BadRequestException("Question cannot be empty");
        }

        String trimmedQuestion = question.trim();
        List<ChatTurn> recentHistory = recentHistory(history);
        log.info("RAG request from user {}: {}", user.getId(), truncate(trimmedQuestion, 80));

        try {
            if (documentId != null && isWholeDocumentSummary(trimmedQuestion)) {
                Optional<RagAnswerResponse> summary = summarizeDocument(user, documentId, trimmedQuestion);
                if (summary.isPresent()) {
                    return summary.get();
                }
            }

            String retrievalQuery = buildRetrievalQuery(trimmedQuestion, recentHistory);
            VectorSearchResponse searchResponse = documentVectorStoreService.searchChunks(
                    user,
                    retrievalQuery,
                    documentId
            );

            List<ChunkSearchResult> retrievedChunks = searchResponse.getChunks();
            if (retrievedChunks.isEmpty()) {
                return notGrounded(trimmedQuestion, List.of());
            }

            if (!isContextRelevant(retrievalQuery, retrievedChunks)) {
                log.info("RAG: retrieved chunks do not match the question; document not grounded");
                return notGrounded(trimmedQuestion, List.of());
            }

            List<ChunkSearchResult> usedChunks = selectChunksForPrompt(retrievedChunks, ragProperties.getMaxContextCharacters());
            String context = formatContext(usedChunks);
            String answer = aiChatService.generateResponse(
                    buildAnswerPrompt(trimmedQuestion, context, recentHistory),
                    ragProperties.getSystemPrompt(),
                    ragProperties.getTemperature()
            ).getResponse().trim();

            if (isNotEnoughInformation(answer)) {
                if (bestKeywordShare(trimmedQuestion, usedChunks) < STRONG_KEYWORD_SHARE) {
                    log.info("RAG: model reported the excerpts do not answer the question");
                    return notGrounded(trimmedQuestion, usedChunks);
                }
                log.info("RAG: model declined but a passage matches the question closely; quoting it");
                answer = buildExtractiveAnswer(trimmedQuestion, usedChunks);
            }

            Set<String> unsupportedNames = findUnsupportedNames(answer, context + "\n" + trimmedQuestion);
            if (unsupportedNames.size() >= 2) {
                log.warn("RAG answer named {} not present in the excerpts; returning quoted text instead", unsupportedNames);
                answer = buildExtractiveAnswer(trimmedQuestion, usedChunks);
            }

            log.info("RAG answer mode: retrieval ({} chunks in prompt, documentId={}, best score={})",
                    usedChunks.size(), documentId, String.format("%.3f", bestVectorScore(retrievedChunks)));

            return RagAnswerResponse.builder()
                    .question(trimmedQuestion)
                    .answer(answer)
                    .model(ollamaAiProperties.getChatModel())
                    .chunksRetrieved(usedChunks.size())
                    .topK(qdrantProperties.getTopK())
                    .sources(usedChunks)
                    .build();
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RagServiceException("RAG pipeline failed: " + ex.getMessage(), ex);
        }
    }

    /** Practice questions drawn from across the whole document rather than from a top-K search. */
    public RagAnswerResponse generatePracticeQuestions(User user, Long documentId) {
        String instruction = ragProperties.getQuestionGenerationPrompt();
        List<ChunkSearchResult> chunks = documentVectorStoreService
                .loadAllDocumentChunks(user, documentId, false, instruction)
                .orElse(List.of());
        if (chunks.isEmpty()) {
            return notGrounded(instruction, List.of());
        }

        List<ChunkSearchResult> sample = sampleEvenly(chunks, ragProperties.getMaxContextCharacters());
        String prompt = """
                %s

                Document excerpts:
                %s
                """.formatted(instruction.trim(), formatContext(sample));

        String answer = aiChatService.generateResponse(
                prompt,
                SUMMARY_SYSTEM_PROMPT,
                ragProperties.getTemperature()
        ).getResponse().trim();

        return RagAnswerResponse.builder()
                .question(instruction)
                .answer(answer)
                .model(ollamaAiProperties.getChatModel())
                .chunksRetrieved(sample.size())
                .topK(qdrantProperties.getTopK())
                .sources(sample)
                .build();
    }

    private boolean isWholeDocumentSummary(String question) {
        if (THEME_REQUEST.matcher(question).find()) {
            return true;
        }
        if (!SUMMARY_REQUEST.matcher(question).find()) {
            return false;
        }
        return QuestionTerms.extract(question).stream().allMatch(GENERIC_SUMMARY_WORDS::contains);
    }

    private Optional<RagAnswerResponse> summarizeDocument(User user, Long documentId, String question) {
        boolean themeRequest = THEME_REQUEST.matcher(question).find();

        if (themeRequest) {
            Optional<Map<String, List<ChunkSearchResult>>> sections =
                    documentVectorStoreService.loadThemeOneSections(user, documentId, question);
            if (sections.isPresent()) {
                log.info("RAG answer mode: syllabus sections ({} sections, documentId={})",
                        sections.get().size(), documentId);
                List<ChunkSearchResult> sources = sections.get().values().stream()
                        .flatMap(List::stream)
                        .toList();
                return Optional.of(summaryResponse(question, summarizeSections(sections.get()), sources));
            }
        }

        Optional<List<ChunkSearchResult>> allChunks =
                documentVectorStoreService.loadAllDocumentChunks(user, documentId, themeRequest, question);
        if (allChunks.isEmpty()) {
            return Optional.empty();
        }

        List<ChunkSearchResult> chunks = allChunks.get();
        log.info("RAG answer mode: map-reduce summary ({} chunks, documentId={})", chunks.size(), documentId);
        return Optional.of(summaryResponse(question, mapReduceSummary(question, chunks), chunks));
    }

    private RagAnswerResponse summaryResponse(String question, String answer, List<ChunkSearchResult> sources) {
        return RagAnswerResponse.builder()
                .question(question)
                .answer(answer)
                .model(ollamaAiProperties.getChatModel())
                .chunksRetrieved(sources.size())
                .topK(qdrantProperties.getTopK())
                .sources(sources)
                .build();
    }

    /**
     * Summarizes the document in batches (map) and merges the partial summaries (reduce), so the model sees the
     * whole document rather than the few chunks a vector search would return. Very long documents are sampled
     * evenly to stay within {@code summaryMaxBatches}.
     */
    private String mapReduceSummary(String question, List<ChunkSearchResult> chunks) {
        int batchCharacters = ragProperties.getSummaryBatchCharacters();
        List<ChunkSearchResult> sampled = sampleEvenly(chunks, batchCharacters * ragProperties.getSummaryMaxBatches());
        List<List<ChunkSearchResult>> batches = splitIntoBatches(sampled, batchCharacters);

        List<String> partials = new ArrayList<>();
        for (int index = 0; index < batches.size(); index++) {
            log.debug("Summarizing batch {} of {}", index + 1, batches.size());
            String prompt = """
                    Summarize this part (%d of %d) of a document in 5 to 10 bullet points starting with "- ".
                    Keep headings, names, numbers and technical terms exactly as written.

                    Text:
                    %s
                    """.formatted(index + 1, batches.size(), joinContent(batches.get(index)));
            partials.add(callSummaryModel(prompt));
        }

        if (partials.size() == 1) {
            return partials.getFirst();
        }

        String prompt = """
                Below are summaries of consecutive parts of one document. Merge them into a single summary that
                answers this request: "%s"

                Group related points under short headings (## Heading) with bullet points. Remove repetition.
                Do not add anything that is not in the partial summaries.

                Partial summaries:
                %s
                """.formatted(question, String.join("\n\n", partials));
        return callSummaryModel(prompt);
    }

    private String summarizeSections(Map<String, List<ChunkSearchResult>> sections) {
        StringBuilder answer = new StringBuilder("## Theme I: Diversity on the Earth\n\n");
        for (Map.Entry<String, List<ChunkSearchResult>> section : sections.entrySet()) {
            String text = truncate(joinContent(section.getValue()), ragProperties.getSectionSummaryMaxContextCharacters());
            String prompt = """
                    Summarize ONLY this syllabus topic: %s
                    Write 5 to 10 bullet points starting with "- ". Copy important terms from the text.

                    Text:
                    %s
                    """.formatted(section.getKey(), text);
            answer.append("### ").append(section.getKey()).append("\n")
                    .append(callSummaryModel(prompt)).append("\n\n");
        }
        return answer.toString().trim();
    }

    private String callSummaryModel(String prompt) {
        return aiChatService.generateResponse(
                prompt,
                SUMMARY_SYSTEM_PROMPT,
                ragProperties.getTemperature()
        ).getResponse().trim();
    }

    private List<ChunkSearchResult> sampleEvenly(List<ChunkSearchResult> chunks, int maxCharacters) {
        int totalCharacters = chunks.stream().mapToInt(chunk -> length(chunk.getContent())).sum();
        if (totalCharacters <= maxCharacters) {
            return chunks;
        }
        int step = (int) Math.ceil((double) totalCharacters / maxCharacters);
        List<ChunkSearchResult> sampled = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index += step) {
            sampled.add(chunks.get(index));
        }
        log.debug("Sampled {} of {} chunks to fit {} characters", sampled.size(), chunks.size(), maxCharacters);
        return sampled;
    }

    private List<List<ChunkSearchResult>> splitIntoBatches(List<ChunkSearchResult> chunks, int batchCharacters) {
        List<List<ChunkSearchResult>> batches = new ArrayList<>();
        List<ChunkSearchResult> current = new ArrayList<>();
        int currentCharacters = 0;
        for (ChunkSearchResult chunk : chunks) {
            int size = length(chunk.getContent());
            if (!current.isEmpty() && currentCharacters + size > batchCharacters) {
                batches.add(current);
                current = new ArrayList<>();
                currentCharacters = 0;
            }
            current.add(chunk);
            currentCharacters += size;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    private String joinContent(List<ChunkSearchResult> chunks) {
        StringBuilder text = new StringBuilder();
        for (ChunkSearchResult chunk : chunks) {
            if (StringUtils.hasText(chunk.getContent())) {
                text.append(chunk.getContent().trim()).append("\n\n");
            }
        }
        return text.toString().trim();
    }

    private List<ChatTurn> recentHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int maxTurns = ragProperties.getHistoryMaxTurns();
        int maxCharacters = ragProperties.getHistoryTurnMaxCharacters();
        return history.stream()
                .filter(turn -> turn != null && StringUtils.hasText(turn.getContent()))
                .skip(Math.max(0, history.size() - maxTurns))
                .map(turn -> new ChatTurn(turn.isUser() ? "user" : "assistant",
                        truncate(turn.getContent().trim(), maxCharacters)))
                .toList();
    }

    /** Follow-ups like "explain it more" are searched together with the previous question. */
    private String buildRetrievalQuery(String question, List<ChatTurn> history) {
        Optional<String> previousQuestion = history.stream()
                .filter(ChatTurn::isUser)
                .map(ChatTurn::getContent)
                .reduce((first, second) -> second);
        if (previousQuestion.isEmpty()) {
            return question;
        }

        boolean followUp = question.split("\\s+").length <= 4 || FOLLOW_UP_CUE.matcher(question).find();
        if (!followUp) {
            return question;
        }
        log.debug("Treating question as a follow-up of: {}", truncate(previousQuestion.get(), 60));
        return previousQuestion.get() + " " + question;
    }

    private String buildAnswerPrompt(String question, String context, List<ChatTurn> history) {
        StringBuilder prompt = new StringBuilder();
        List<String> earlierQuestions = history.stream()
                .filter(ChatTurn::isUser)
                .map(ChatTurn::getContent)
                .toList();
        if (!earlierQuestions.isEmpty()) {
            prompt.append("Earlier questions in this chat (only to understand what the question refers to):\n");
            earlierQuestions.forEach(earlier -> prompt.append("- ").append(earlier).append("\n"));
            prompt.append("\n");
        }

        prompt.append("""
                Document excerpts:
                %s

                Question: %s

                Rules:
                - Answer using only the excerpts above. The document may be newer than your training data, so prefer it over what you remember.
                - Keep names, numbers, dates and terms exactly as written in the excerpts.
                - Cite the excerpts you used as [Source N].
                - If the excerpts do not contain the answer, reply only: "I do not have enough information in your indexed documents to answer this."
                """.formatted(context, question));
        return prompt.toString();
    }

    private List<ChunkSearchResult> selectChunksForPrompt(List<ChunkSearchResult> chunks, int maxCharacters) {
        List<ChunkSearchResult> selected = new ArrayList<>();
        int total = 0;
        for (ChunkSearchResult chunk : chunks) {
            int size = length(chunk.getContent()) + 40;
            if (!selected.isEmpty() && total + size > maxCharacters) {
                break;
            }
            selected.add(chunk);
            total += size;
        }
        return selected;
    }

    private String formatContext(List<ChunkSearchResult> chunks) {
        StringBuilder context = new StringBuilder();
        for (int index = 0; index < chunks.size(); index++) {
            ChunkSearchResult chunk = chunks.get(index);
            context.append("[Source ").append(index + 1);
            if (chunk.getPageNumber() != null) {
                context.append(", page ").append(chunk.getPageNumber());
            }
            context.append("]\n").append(chunk.getContent() == null ? "" : chunk.getContent().trim()).append("\n\n");
        }
        return context.toString().trim();
    }

    private boolean isNotEnoughInformation(String answer) {
        return !StringUtils.hasText(answer)
                || (answer.length() < 300 && NOT_ENOUGH_INFO.matcher(answer).find());
    }

    /**
     * Capitalized words in the answer (names, places) that never appear in the excerpts or the question;
     * a sign the model answered from memory.
     */
    private Set<String> findUnsupportedNames(String answer, String context) {
        String contextLower = context.toLowerCase(Locale.ROOT);
        Set<String> unsupported = new LinkedHashSet<>();
        Matcher matcher = CAPITALIZED_WORD.matcher(answer);
        while (matcher.find()) {
            String word = matcher.group();
            String lower = word.toLowerCase(Locale.ROOT);
            if (COMMON_CAPITALIZED.contains(lower) || isSentenceStart(answer, matcher.start())) {
                continue;
            }
            if (!QuestionTerms.containsWholeWord(contextLower, lower)) {
                unsupported.add(word);
            }
        }
        return unsupported;
    }

    private boolean isSentenceStart(String text, int position) {
        int index = position - 1;
        while (index >= 0 && (text.charAt(index) == ' ' || text.charAt(index) == '*' || text.charAt(index) == '#')) {
            index--;
        }
        if (index < 0) {
            return true;
        }
        char previous = text.charAt(index);
        return previous == '.' || previous == '\n' || previous == ':' || previous == '-'
                || previous == '!' || previous == '?' || previous == '"' || Character.isDigit(previous);
    }

    private String buildExtractiveAnswer(String question, List<ChunkSearchResult> chunks) {
        Set<String> terms = QuestionTerms.extract(question);
        List<Integer> ranked = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            ranked.add(index);
        }
        ranked.sort(Comparator.comparingInt(
                (Integer index) -> QuestionTerms.countMatches(chunks.get(index).getContent(), terms)).reversed());

        StringBuilder answer = new StringBuilder("Here is what your PDF says (quoted directly):\n\n");
        for (int position = 0; position < Math.min(2, ranked.size()); position++) {
            int index = ranked.get(position);
            ChunkSearchResult chunk = chunks.get(index);
            answer.append("> ").append(truncate(bestMatchingPassage(chunk.getContent(), terms), 500))
                    .append("\n\n[Source ").append(index + 1);
            if (chunk.getPageNumber() != null) {
                answer.append(", page ").append(chunk.getPageNumber());
            }
            answer.append("]\n\n");
        }
        return answer.toString().trim();
    }

    /** The two consecutive lines/sentences of a chunk that contain the most question words. */
    private String bestMatchingPassage(String content, Set<String> terms) {
        String[] segments = PASSAGE_SPLIT.split(content.trim());
        int bestStart = 0;
        int bestMatches = -1;
        for (int start = 0; start < segments.length; start++) {
            String window = segments[start] + (start + 1 < segments.length ? " " + segments[start + 1] : "");
            int matches = QuestionTerms.countMatches(window, terms);
            if (matches > bestMatches) {
                bestMatches = matches;
                bestStart = start;
            }
        }
        String passage = segments[bestStart]
                + (bestStart + 1 < segments.length ? " " + segments[bestStart + 1] : "");
        return passage.replaceAll("\\s+", " ").trim();
    }

    private RagAnswerResponse notGrounded(String question, List<ChunkSearchResult> chunks) {
        return RagAnswerResponse.builder()
                .question(question)
                .answer("")
                .model(ollamaAiProperties.getChatModel())
                .chunksRetrieved(chunks.size())
                .topK(qdrantProperties.getTopK())
                .sources(chunks)
                .documentGrounded(false)
                .build();
    }

    /**
     * Rejects excerpts that are not about the question: a weak best vector score, or no chunk sharing enough
     * whole-word terms with the question (e.g. "Java" vs "Janata", or "synchronization" in a politics PDF).
     */
    private boolean isContextRelevant(String question, List<ChunkSearchResult> chunks) {
        double bestScore = bestVectorScore(chunks);
        if (bestScore < qdrantProperties.getMinRelevanceScore()) {
            log.debug("Best vector score {} is below min-relevance-score", bestScore);
            return false;
        }
        if (bestScore >= qdrantProperties.getStrongRelevanceScore()) {
            return true;
        }

        Set<String> terms = QuestionTerms.extract(question);
        if (terms.isEmpty()) {
            return true;
        }

        boolean technicalQuestion = isTechnicalInterviewQuestion(question);
        for (ChunkSearchResult chunk : chunks) {
            if (!StringUtils.hasText(chunk.getContent())) {
                continue;
            }
            String content = chunk.getContent().toLowerCase(Locale.ROOT);
            int matches = QuestionTerms.countMatches(content, terms);
            if (matches == 0) {
                continue;
            }
            if (technicalQuestion && !chunkSupportsTechnicalQuestion(question, content)) {
                continue;
            }
            if (matches >= Math.min(2, terms.size())) {
                return true;
            }
        }
        return false;
    }

    private double bestKeywordShare(String question, List<ChunkSearchResult> chunks) {
        Set<String> terms = QuestionTerms.extract(question);
        if (terms.isEmpty()) {
            return 0.0;
        }
        return chunks.stream()
                .mapToInt(chunk -> QuestionTerms.countMatches(chunk.getContent(), terms))
                .max()
                .orElse(0) / (double) terms.size();
    }

    private double bestVectorScore(List<ChunkSearchResult> chunks) {
        return chunks.stream()
                .map(ChunkSearchResult::getScore)
                .filter(score -> score != null)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
    }

    private boolean isTechnicalInterviewQuestion(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        return containsAnyWholeWord(lower, "java", "python", "javascript", "typescript", "spring",
                "hashmap", "arraylist", "synchronization", "synchronisation", "thread", "multithreading",
                "jdbc", "jpa", "hibernate", "kubernetes", "docker", "microservice", "rest", "api",
                "sql", "database", "oop", "polymorphism", "inheritance", "encapsulation", "algorithm",
                "data structure", "singleton", "factory", "mvc", "react", "angular");
    }

    private boolean chunkSupportsTechnicalQuestion(String question, String content) {
        String lower = question.toLowerCase(Locale.ROOT);
        if (lower.contains("synchron")) {
            return containsAnyWholeWord(content, "thread", "threads", "synchronized", "synchronised",
                    "lock", "locking", "monitor", "mutex", "concurrent", "concurrency", "multithreading",
                    "java", "jvm", "object");
        }
        if (QuestionTerms.containsWholeWord(lower, "java") && !lower.contains("javascript")) {
            return containsAnyWholeWord(content, "java", "jdk", "jvm", "class", "interface", "spring",
                    "method", "bytecode", "programming");
        }
        if (containsAnyWholeWord(lower, "thread", "multithreading", "concurrent", "concurrency")) {
            return containsAnyWholeWord(content, "thread", "synchronized", "lock", "monitor", "java",
                    "concurrent", "runnable");
        }
        if (containsAnyWholeWord(lower, "hashmap", "arraylist", "collection")) {
            return containsAnyWholeWord(content, "hashmap", "hash map", "collection", "map", "list",
                    "java", "interface");
        }
        return containsAnyWholeWord(content, "programming", "software", "computer", "code", "class",
                "method", "algorithm", "database", "sql", "api", "object");
    }

    private boolean containsAnyWholeWord(String text, String... terms) {
        for (String term : terms) {
            if (QuestionTerms.containsWholeWord(text, term.trim())) {
                return true;
            }
        }
        return false;
    }

    public static Set<Integer> citedSourceNumbers(String answer) {
        Set<Integer> cited = new LinkedHashSet<>();
        if (answer == null) {
            return cited;
        }
        Matcher matcher = CITATION.matcher(answer);
        while (matcher.find()) {
            cited.add(Integer.parseInt(matcher.group(1)));
        }
        return cited;
    }

    private int length(String text) {
        return text == null ? 0 : text.length();
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
