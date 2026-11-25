package vn.nutrition.rag;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.nutrition.auth.Account;
import vn.nutrition.common.ApiException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
class ChatConversations {
    private static final Logger LOG = LoggerFactory.getLogger(ChatConversations.class);
    private static final int MAX_QUERY_TOKENS = 8000;
    private static final String NO_EVIDENCE =
        "Chưa đủ căn cứ từ các tài liệu đang hoạt động để trả lời câu hỏi này.";
    private static final String PROMPT_VERSION = "rag-answer-v1";
    private static final Pattern EMAIL = Pattern.compile(
        "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");
    private static final Pattern SECRET = Pattern.compile(
        "(?iu)(?:mật\\s+khẩu|password|api[ _-]?key|khóa\\s+api|access[ _-]?token)\\s*(?:[:=]|là\\b|is\\b)[^\\r\\n]+");
    private static final Pattern API_TOKEN = Pattern.compile("\\b(?:sk-[a-zA-Z0-9_-]{12,}|Bearer\\s+[a-zA-Z0-9._~+/=-]{12,})\\b");

    record Summary(UUID id, String title, OffsetDateTime createdAt) {}
    record Detail(UUID id, String title, OffsetDateTime createdAt, List<Message> messages) {}
    record Message(UUID id, String role, String content, OffsetDateTime createdAt, List<Source> sources) {}
    record Source(UUID id, String title, String version, int pageStart, int pageEnd, String quotedText,
                  boolean available) {}
    private record MessageRow(UUID id, String role, String content, OffsetDateTime createdAt) {}
    private record Chunk(UUID id, String content, double score) {}
    private record Answer(RagAi.Result result, List<Chunk> sources) {
        boolean supported() { return !sources.isEmpty(); }
    }

    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final RagAi ai;
    private final long timeoutMs;
    private final double minimumSimilarity;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 8, 30, TimeUnit.SECONDS,
        new SynchronousQueue<>(), Thread.ofVirtual().name("rag-chat-", 0).factory());

    ChatConversations(JdbcTemplate db, TransactionTemplate transactions, RagAi ai,
                      @Value("${app.rag-chat-timeout-ms:30000}") long timeoutMs,
                      @Value("${app.rag-minimum-similarity:0.30}") double minimumSimilarity) {
        this.db = db;
        this.transactions = transactions;
        this.ai = ai;
        if (timeoutMs <= 0 || timeoutMs > 30_000 || !Double.isFinite(minimumSimilarity)
            || minimumSimilarity < 0 || minimumSimilarity > 1) {
            throw new IllegalArgumentException("Invalid RAG chat configuration");
        }
        this.timeoutMs = timeoutMs;
        this.minimumSimilarity = minimumSimilarity;
    }

    @PreDestroy
    void stop() {
        workers.shutdownNow();
    }

    List<Summary> list(UUID owner) {
        return db.query("""
            SELECT id,title,created_at FROM chat_conversations WHERE user_id=?
            ORDER BY created_at DESC,id
            """, (rs, row) -> summary(rs), owner);
    }

    Summary create(UUID owner, String title) {
        String cleaned = title == null || title.isBlank() ? "Hội thoại mới" : title.strip();
        if (cleaned.length() > 255) throw ApiException.field("title", "Tiêu đề tối đa 255 ký tự.");
        return db.queryForObject("""
            INSERT INTO chat_conversations(user_id,title) VALUES (?,?) RETURNING id,title,created_at
            """, (rs, row) -> summary(rs), owner, cleaned);
    }

    Detail detail(UUID owner, UUID id) {
        Summary conversation = owned(owner, id);
        var messages = db.query("""
            SELECT id,role,content,created_at FROM chat_messages
            WHERE conversation_id=? ORDER BY turn_index,CASE role WHEN 'USER' THEN 0 ELSE 1 END
            """, (rs, row) -> new MessageRow(rs.getObject("id", UUID.class), rs.getString("role"),
            rs.getString("content"), rs.getObject("created_at", OffsetDateTime.class)), id);
        Map<UUID, List<Source>> sources = db.query("""
            SELECT s.id,s.message_id,d.title,d.version,c.page_start,c.page_end,s.quoted_text_snapshot,
                   d.status='ACTIVE' AS available
            FROM chat_sources s JOIN chat_messages m ON m.id=s.message_id
            JOIN rag_chunks c ON c.id=s.chunk_id JOIN rag_documents d ON d.id=c.document_id
            WHERE m.conversation_id=? ORDER BY m.turn_index,s.citation_index
            """, (rs, row) -> Map.entry(rs.getObject("message_id", UUID.class),
            new Source(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("version"),
                rs.getInt("page_start"), rs.getInt("page_end"), rs.getString("quoted_text_snapshot"),
                rs.getBoolean("available"))), id)
            .stream().collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return new Detail(conversation.id(), conversation.title(), conversation.createdAt(), messages.stream()
            .map(m -> new Message(m.id(), m.role(), m.content(), m.createdAt(), sources.getOrDefault(m.id(), List.of())))
            .toList());
    }

    String sourceFile(UUID owner, UUID conversationId, UUID sourceId) {
        return db.query("""
            SELECT d.file_path FROM chat_sources s JOIN chat_messages m ON m.id=s.message_id
            JOIN chat_conversations conv ON conv.id=m.conversation_id JOIN rag_chunks c ON c.id=s.chunk_id
            JOIN rag_documents d ON d.id=c.document_id
            WHERE conv.id=? AND conv.user_id=? AND s.id=? AND d.status='ACTIVE'
            """, (rs, row) -> rs.getString("file_path"), conversationId, owner, sourceId).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "CHAT_SOURCE_NOT_FOUND", "Nguồn PDF hiện không mở được."));
    }

    Detail send(Account account, UUID id, String content, UUID requestId) {
        long start = System.nanoTime();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        long deadline = start + timeoutNanos;
        OffsetDateTime leaseDeadline = OffsetDateTime.now(ZoneOffset.UTC).plusNanos(timeoutNanos);
        owned(account.id(), id);
        if (content == null || content.isBlank() || content.length() > 4000) {
            throw ApiException.field("content", "Nhập câu hỏi từ 1 đến 4.000 ký tự.");
        }
        String question = content.strip();
        if (alreadySaved(id, requestId, question)) return detail(account.id(), id);
        UUID token = UUID.randomUUID();
        boolean acquired = false;
        try {
            acquired = Boolean.TRUE.equals(transactions.execute(status -> {
                limitStatements(deadline);
                return db.update("""
                    UPDATE chat_conversations SET pending_id=?,pending_until=?
                    WHERE id=? AND user_id=? AND (pending_until IS NULL OR pending_until<=clock_timestamp())
                    """, token, leaseDeadline, id, account.id()) == 1;
            }));
            remaining(deadline);
            if (!acquired) {
                throw new ApiException(409, "CHAT_TURN_PENDING", "Hội thoại đang xử lý câu hỏi trước. Vui lòng thử lại.");
            }
            // A previous attempt may have committed between the first check and acquiring the lease.
            if (alreadySaved(id, requestId, question)) return detail(account.id(), id);
            Answer answer = awaitAnswer(account, id, question, deadline);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            storeTurn(id, token, requestId, question, answer, elapsed, deadline);
            acquired = false;
            LOG.info("RAG turn {} finished in {} ms (cited={})", token, elapsed, answer.supported());
        } catch (QueryTimeoutException e) {
            throw traced(timeout(), token);
        } catch (ApiException e) {
            throw traced(e, token);
        } finally {
            if (acquired) {
                db.update("UPDATE chat_conversations SET pending_id=NULL,pending_until=NULL WHERE id=? AND pending_id=?",
                    id, token);
            }
        }
        return detail(account.id(), id);
    }

    private boolean alreadySaved(UUID id, UUID requestId, String content) {
        if (requestId == null) return false;
        var stored = db.queryForList("""
            SELECT content FROM chat_messages WHERE conversation_id=? AND request_id=?
            """, String.class, id, requestId);
        if (stored.isEmpty()) return false;
        if (!stored.getFirst().equals(content)) {
            throw new ApiException(409, "CHAT_REQUEST_CHANGED", "Mã lượt gửi đã được dùng cho câu hỏi khác.");
        }
        return true;
    }

    private Answer awaitAnswer(Account account, UUID id, String question, long deadline) {
        Future<Answer> work;
        try {
            work = workers.submit(() -> generate(account, id, question, deadline));
        } catch (RejectedExecutionException busy) {
            throw new ApiException(503, "CHAT_BUSY", "Chatbot đang bận. Vui lòng thử lại.");
        }
        try {
            return work.get(remaining(deadline).toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            throw timeout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw timeout();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RagAi.Timeout) throw timeout();
            if (e.getCause() instanceof ApiException error) throw error;
            throw new ApiException(503, "CHAT_AI_UNAVAILABLE", "Chatbot chưa phản hồi. Vui lòng thử lại.");
        } finally {
            if (!work.isDone()) work.cancel(true);
        }
    }

    private Answer generate(Account account, UUID id, String question, long deadline) {
        String model = ai.embeddingModel();
        boolean ready = Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM rag_chunks c JOIN rag_documents d ON d.id=c.document_id
            WHERE d.status='ACTIVE' AND c.embedding_model=? AND c.embedding_dimensions=1536)
            """, Boolean.class, model));
        if (!ready) return new Answer(null, List.of());
        String text = sanitize(question, account);
        if (text.isBlank()) return new Answer(null, List.of());
        var history = db.queryForList("""
            SELECT content FROM (SELECT content,turn_index FROM chat_messages
            WHERE conversation_id=? AND role='USER' ORDER BY turn_index DESC LIMIT 3) recent ORDER BY turn_index
            """, String.class, id).stream().map(value -> sanitize(value, account)).filter(value -> !value.isBlank()).toList();
        // Put the current question and newest context first so truncation drops older context.
        String query = text + (history.isEmpty() ? "" : "\n" + String.join("\n", history.reversed()));
        var encoding = RagAi.EMBEDDING_TOKENIZER;
        var encoded = encoding.encodeOrdinary(query, MAX_QUERY_TOKENS);
        if (encoded.isTruncated()) query = encoding.decode(encoded.getTokens());
        String vector = RagAi.vector(ai.embed(List.of(query), remaining(deadline)).getFirst());
        var chunks = db.query("""
            SELECT id,content,score FROM (
              SELECT c.id,c.content,1-(c.embedding OPERATOR(public.<=>) CAST(? AS public.vector)) AS score
              FROM rag_chunks c JOIN rag_documents d ON d.id=c.document_id
              WHERE d.status='ACTIVE' AND c.embedding_model=? AND c.embedding_dimensions=1536
            ) ranked WHERE score>=? ORDER BY score DESC,id LIMIT 5
            """, (rs, row) -> new Chunk(rs.getObject("id", UUID.class), rs.getString("content"),
            rs.getDouble("score")), vector, model, minimumSimilarity);
        if (chunks.isEmpty()) return new Answer(null, List.of());
        var result = ai.answer(text, history, chunks.stream().map(c -> new RagAi.Context(c.id(), c.content())).toList(),
            remaining(deadline));
        if (!result.supported() || result.content() == null || result.content().isBlank()
            || result.citationIds() == null || result.citationIds().isEmpty()) {
            return new Answer(result, List.of());
        }
        Map<UUID, Chunk> retrieved = new LinkedHashMap<>();
        chunks.forEach(chunk -> retrieved.put(chunk.id(), chunk));
        if (!retrieved.keySet().containsAll(result.citationIds())) return new Answer(result, List.of());
        return new Answer(result, result.citationIds().stream().distinct().map(retrieved::get).toList());
    }

    private void storeTurn(UUID id, UUID token, UUID requestId, String question, Answer answer, long elapsed, long deadline) {
        transactions.executeWithoutResult(status -> {
            limitStatements(deadline);
            int turn = db.query("""
                UPDATE chat_conversations SET turn_count=turn_count+1
                WHERE id=? AND pending_id=? AND pending_until>clock_timestamp() RETURNING turn_count
                """, (rs, row) -> rs.getInt(1), id, token).stream().findFirst().orElseThrow(ChatConversations::timeout);
            var result = answer.result();
            db.update("""
                INSERT INTO chat_messages(conversation_id,turn_index,role,content,request_id) VALUES (?,?,'USER',?,?)
                """, id, turn, question, requestId);
            UUID message = db.queryForObject("""
                INSERT INTO chat_messages(conversation_id,turn_index,role,content,status,model_name,prompt_version,
                                          response_time_ms,input_tokens,output_tokens,trace_id)
                VALUES (?,?,'ASSISTANT',?,?,?,?,?,?,?,?) RETURNING id
                """, UUID.class, id, turn, answer.supported() ? result.content() : NO_EVIDENCE,
                answer.supported() ? "COMPLETE" : "INSUFFICIENT_EVIDENCE", result == null ? null : result.modelName(),
                result == null ? null : PROMPT_VERSION, elapsed, result == null ? null : result.inputTokens(),
                result == null ? null : result.outputTokens(), token);
            int index = 0;
            for (Chunk source : answer.sources()) {
                db.update("""
                    INSERT INTO chat_sources(chunk_id,message_id,citation_index,relevance_score,quoted_text_snapshot)
                    VALUES (?,?,?,?,?)
                    """, source.id(), message, ++index, source.score(), source.content());
            }
            limitStatements(deadline);
            int completed = db.update("""
                UPDATE chat_conversations SET pending_id=NULL,pending_until=NULL
                WHERE id=? AND pending_id=? AND pending_until>clock_timestamp()
                """, id, token);
            if (completed != 1) throw timeout();
            remaining(deadline);
        });
    }

    private void limitStatements(long deadline) {
        String budget = Math.max(1, remaining(deadline).toMillis()) + "ms";
        db.queryForObject("SELECT set_config('statement_timeout', ?, true)", String.class, budget);
    }

    private static ApiException traced(ApiException error, UUID traceId) {
        return new ApiException(error.status, error.code, error.getMessage(), error.fieldErrors, traceId);
    }

    private Summary owned(UUID user, UUID id) {
        return db.query("""
            SELECT id,title,created_at FROM chat_conversations WHERE id=? AND user_id=?
            """, (rs, row) -> summary(rs), id, user).stream().findFirst()
            .orElseThrow(() -> new ApiException(404, "CHAT_NOT_FOUND", "Không tìm thấy hội thoại."));
    }

    private static Summary summary(ResultSet rs) throws SQLException {
        return new Summary(rs.getObject("id", UUID.class), rs.getString("title"),
            rs.getObject("created_at", OffsetDateTime.class));
    }

    static String sanitize(String text, Account account) {
        text = EMAIL.matcher(text).replaceAll("[đã ẩn]");
        text = SECRET.matcher(text).replaceAll("[đã ẩn]");
        text = API_TOKEN.matcher(text).replaceAll("[đã ẩn]");
        text = text.replaceAll("(?i)" + account.id(), "[đã ẩn]");
        text = text.replaceAll("(?i)" + account.sessionContext(), "[đã ẩn]");
        String name = account.displayName();
        if (name != null && !name.isBlank()) {
            text = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}])")
                .matcher(text).replaceAll("[đã ẩn]");
        }
        return text.strip();
    }

    private static Duration remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw timeout();
        return Duration.ofNanos(nanos);
    }

    private static ApiException timeout() {
        return new ApiException(504, "CHAT_TIMEOUT", "Chatbot đã hết thời gian chờ. Vui lòng thử lại.");
    }
}
