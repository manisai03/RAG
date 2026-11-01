package com.aiinterview.assistant.controller;

import com.aiinterview.assistant.config.OllamaAiProperties;
import com.aiinterview.assistant.config.RagProperties;
import com.aiinterview.assistant.dto.ChatAskRequest;
import com.aiinterview.assistant.dto.ChatAskResponse;
import com.aiinterview.assistant.dto.ChatResponse;
import com.aiinterview.assistant.dto.ChatTurn;
import com.aiinterview.assistant.dto.ChunkSearchResult;
import com.aiinterview.assistant.dto.DocumentResponse;
import com.aiinterview.assistant.dto.RagAnswerResponse;
import com.aiinterview.assistant.dto.SourceReference;
import com.aiinterview.assistant.entity.User;
import com.aiinterview.assistant.service.AiChatService;
import com.aiinterview.assistant.service.DocumentService;
import com.aiinterview.assistant.service.RetrievalAugmentedGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final int MAX_UNCITED_SOURCES = 5;
    private static final int SNIPPET_LENGTH = 220;

    private final RetrievalAugmentedGenerationService retrievalAugmentedGenerationService;
    private final DocumentService documentService;
    private final AiChatService aiChatService;
    private final OllamaAiProperties ollamaAiProperties;
    private final RagProperties ragProperties;

    @PostMapping("/ask")
    public ResponseEntity<ChatAskResponse> ask(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChatAskRequest request
    ) {
        List<ChatTurn> history = recentHistory(request.getHistory());

        if (Boolean.FALSE.equals(request.getUseDocuments())) {
            return ResponseEntity.ok(answerWithGeneralChat(request.getQuestion(), history));
        }

        Optional<DocumentResponse> document = findDocumentOptional(user, request.getDocumentId());
        if (document.isEmpty()) {
            return ResponseEntity.ok(answerWithGeneralChat(request.getQuestion(), history));
        }

        DocumentResponse selected = document.get();
        RagAnswerResponse ragResponse = retrievalAugmentedGenerationService.ask(
                user,
                request.getQuestion(),
                selected.getId(),
                history
        );

        if (!ragResponse.isDocumentGrounded() && ragProperties.isFallbackToGeneral()) {
            log.info(
                    "RAG had no relevant PDF text for question; falling back to general model knowledge (document={})",
                    selected.getOriginalFileName()
            );
            return ResponseEntity.ok(answerWithGeneralFallback(request.getQuestion(), selected, history));
        }

        if (!ragResponse.isDocumentGrounded()) {
            return ResponseEntity.ok(ChatAskResponse.builder()
                    .answer(ragProperties.getNoContextAnswer().trim())
                    .documentId(selected.getId())
                    .documentFileName(selected.getOriginalFileName())
                    .generalChat(false)
                    .ragFallback(false)
                    .build());
        }

        return ResponseEntity.ok(ChatAskResponse.builder()
                .answer(ragResponse.getAnswer())
                .documentId(selected.getId())
                .documentFileName(selected.getOriginalFileName())
                .generalChat(false)
                .ragFallback(false)
                .sources(toSourceReferences(ragResponse))
                .build());
    }

    private ChatAskResponse answerWithGeneralChat(String question, List<ChatTurn> history) {
        ChatResponse aiResponse = aiChatService.generateResponse(
                question,
                ollamaAiProperties.getSystemPrompt(),
                null,
                history
        );
        return ChatAskResponse.builder()
                .answer(aiResponse.getResponse())
                .generalChat(true)
                .ragFallback(false)
                .build();
    }

    private ChatAskResponse answerWithGeneralFallback(String question, DocumentResponse document, List<ChatTurn> history) {
        // Earlier answers quoted from the PDF would be imitated as fake quotes, so only earlier questions are sent.
        List<ChatTurn> earlierQuestions = history.stream().filter(ChatTurn::isUser).toList();
        ChatResponse aiResponse = aiChatService.generateResponse(
                question,
                ollamaAiProperties.getSystemPrompt(),
                null,
                earlierQuestions
        );
        String preamble = ragProperties.getGeneralFallbackPreamble()
                .formatted(document.getOriginalFileName());
        return ChatAskResponse.builder()
                .answer(preamble + aiResponse.getResponse())
                .documentId(document.getId())
                .documentFileName(document.getOriginalFileName())
                .generalChat(true)
                .ragFallback(true)
                .build();
    }

    private List<ChatTurn> recentHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int maxTurns = ragProperties.getHistoryMaxTurns();
        int maxCharacters = ragProperties.getHistoryTurnMaxCharacters();
        return history.subList(Math.max(0, history.size() - maxTurns), history.size()).stream()
                .filter(turn -> turn != null && turn.getContent() != null && !turn.getContent().isBlank())
                .map(turn -> new ChatTurn(
                        turn.isUser() ? "user" : "assistant",
                        truncate(stripFallbackPreamble(turn.getContent()).trim(), maxCharacters)))
                .toList();
    }

    /** The model copies the "not in your PDF" preamble if it sees it in earlier turns, so remove it. */
    private String stripFallbackPreamble(String content) {
        String[] parts = ragProperties.getGeneralFallbackPreamble().trim().split("%s", -1);
        String pattern = "(?s)^\\s*(" + Arrays.stream(parts)
                .map(Pattern::quote)
                .collect(Collectors.joining(".*?")) + ")\\s*";
        String stripped = content;
        String previous;
        do {
            previous = stripped;
            stripped = stripped.replaceFirst(pattern, "");
        } while (!stripped.equals(previous));
        return stripped;
    }

    /** Cited passages when the answer has [Source N] citations, otherwise the first few passages used. */
    private List<SourceReference> toSourceReferences(RagAnswerResponse ragResponse) {
        List<ChunkSearchResult> chunks = ragResponse.getSources() != null ? ragResponse.getSources() : List.of();
        Set<Integer> cited = RetrievalAugmentedGenerationService.citedSourceNumbers(ragResponse.getAnswer());
        cited.removeIf(number -> number < 1 || number > chunks.size());

        List<SourceReference> references = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            int sourceNumber = index + 1;
            boolean include = cited.isEmpty() ? references.size() < MAX_UNCITED_SOURCES : cited.contains(sourceNumber);
            if (!include) {
                continue;
            }
            ChunkSearchResult chunk = chunks.get(index);
            String text = chunk.getContent() == null ? "" : chunk.getContent().replaceAll("\\s+", " ").trim();
            references.add(SourceReference.builder()
                    .sourceNumber(sourceNumber)
                    .pageNumber(chunk.getPageNumber())
                    .fileName(chunk.getFileName())
                    .snippet(truncate(text, SNIPPET_LENGTH))
                    .build());
        }
        return references;
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private Optional<DocumentResponse> findDocumentOptional(User user, Long documentId) {
        if (documentId != null) {
            return Optional.of(documentService.getDocument(user, documentId));
        }
        return documentService.findLatestDocument(user);
    }
}
