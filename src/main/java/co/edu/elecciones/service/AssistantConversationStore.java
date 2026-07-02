package co.edu.elecciones.service;

import co.edu.elecciones.domain.AssistantMessage;
import co.edu.elecciones.domain.AssistantSession;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.dto.Responses.ChatHistory;
import co.edu.elecciones.dto.Responses.ChatHistoryMessage;
import co.edu.elecciones.repository.AssistantMessageRepository;
import co.edu.elecciones.repository.AssistantSessionRepository;
import co.edu.elecciones.repository.ElectionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class AssistantConversationStore {

    private final AssistantSessionRepository sessions;
    private final AssistantMessageRepository messages;
    private final ElectionRepository elections;
    private final boolean persistenceEnabled;
    private final int historyLimit;

    public AssistantConversationStore(
            AssistantSessionRepository sessions,
            AssistantMessageRepository messages,
            ElectionRepository elections,
            @Value("${app.gemini.persist-history:true}") boolean persistenceEnabled,
            @Value("${app.gemini.history-limit:10}") int historyLimit) {
        this.sessions = sessions;
        this.messages = messages;
        this.elections = elections;
        this.persistenceEnabled = persistenceEnabled;
        this.historyLimit = Math.max(2, Math.min(historyLimit, 30));
    }

    @Transactional
    public SessionHandle resolve(UUID requestedSessionId, Long electionId, String provider, String model) {
        UUID effectiveKey = requestedSessionId == null ? UUID.randomUUID() : requestedSessionId;
        if (!persistenceEnabled) {
            return new SessionHandle(effectiveKey, null);
        }

        AssistantSession session = requestedSessionId == null
                ? null
                : sessions.selectActiveBySessionKey(requestedSessionId).orElse(null);

        if (session == null) {
            session = new AssistantSession();
            session.sessionKey = requestedSessionId == null ? effectiveKey : UUID.randomUUID();
            effectiveKey = session.sessionKey;
        }

        if (electionId != null) {
            Election election = elections.selectById(electionId)
                    .orElseThrow(() -> new IllegalArgumentException("La elección indicada no existe"));
            session.election = election;
        }
        session.provider = provider;
        session.model = model;
        session.status = "ACTIVE";
        session.lastActivityAt = Instant.now();
        return new SessionHandle(effectiveKey, sessions.save(session));
    }

    @Transactional(readOnly = true)
    public List<AssistantMessage> recent(UUID sessionId) {
        if (!persistenceEnabled || sessionId == null) return List.of();
        List<AssistantMessage> newest = new ArrayList<>(messages.selectRecentBySessionKey(
                sessionId, PageRequest.of(0, historyLimit)));
        Collections.reverse(newest);
        return newest;
    }

    @Transactional
    public Long saveMessage(SessionHandle handle,
                            String role,
                            String content,
                            String provider,
                            String model,
                            String intent,
                            List<String> toolsUsed,
                            boolean fallback,
                            Long responseTimeMs) {
        if (!persistenceEnabled || handle.session() == null) return null;

        AssistantSession session = handle.session();
        AssistantMessage message = new AssistantMessage();
        message.session = session;
        message.role = role;
        message.content = content;
        message.provider = provider;
        message.model = model;
        message.intent = intent;
        message.toolsUsed = toolsUsed == null ? null : String.join(",", toolsUsed);
        message.fallback = fallback;
        message.responseTimeMs = responseTimeMs;
        AssistantMessage saved = messages.save(message);

        session.messageCount = (session.messageCount == null ? 0 : session.messageCount) + 1;
        session.lastActivityAt = Instant.now();
        session.updatedAt = Instant.now();
        sessions.save(session);
        return saved.id;
    }

    @Transactional(readOnly = true)
    public ChatHistory history(UUID sessionId) {
        if (sessionId == null) return new ChatHistory(null, null, List.of());
        AssistantSession session = persistenceEnabled
                ? sessions.selectActiveBySessionKey(sessionId).orElse(null)
                : null;
        if (session == null) return new ChatHistory(sessionId, null, List.of());

        List<AssistantMessage> recent = new ArrayList<>(messages.selectRecentBySessionKey(
                sessionId, PageRequest.of(0, 100)));
        Collections.reverse(recent);
        List<ChatHistoryMessage> mapped = recent.stream().map(message -> new ChatHistoryMessage(
                message.id,
                message.role,
                message.content,
                message.provider,
                message.model,
                Boolean.TRUE.equals(message.fallback),
                message.helpful,
                message.createdAt
        )).toList();
        return new ChatHistory(sessionId, session.election == null ? null : session.election.id, mapped);
    }

    @Transactional
    public boolean feedback(UUID sessionId, Long messageId, Boolean helpful, String comment) {
        if (!persistenceEnabled) return false;
        String safeComment = comment == null || comment.isBlank() ? null : comment.trim();
        return messages.updateFeedback(sessionId, messageId, helpful, safeComment) == 1;
    }

    @Transactional
    public void close(UUID sessionId) {
        if (!persistenceEnabled || sessionId == null) return;
        messages.deleteBySessionKey(sessionId);
        sessions.closeBySessionKey(sessionId);
    }

    public boolean persistenceEnabled() {
        return persistenceEnabled;
    }

    public int historyLimit() {
        return historyLimit;
    }

    public record SessionHandle(UUID sessionKey, AssistantSession session) {
    }
}
