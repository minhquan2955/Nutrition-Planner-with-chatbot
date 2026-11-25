package vn.nutrition.rag;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.nutrition.common.ApiException;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
class RagDocuments {
    private static final Logger LOG = LoggerFactory.getLogger(RagDocuments.class);
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final Pattern INTERNAL_PDF = Pattern.compile("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}\\.pdf");
    private static final String SUMMARIES = """
        SELECT d.id,d.title,d.publisher,d.publication_year,d.source_url,d.version,d.status,d.error_code,d.checksum,
            (SELECT count(*) FROM rag_chunks c WHERE c.document_id=d.id) AS chunk_count
        FROM rag_documents d
        """;
    private static final RowMapper<Summary> SUMMARY_ROW = (rs, row) -> new Summary(rs.getObject("id", UUID.class),
        rs.getString("title"), rs.getString("publisher"), rs.getObject("publication_year", Integer.class),
        rs.getString("source_url"), rs.getString("version"), rs.getString("status"), rs.getInt("chunk_count"),
        rs.getString("error_code"), rs.getString("checksum"));

    record Summary(UUID id, String title, String publisher, Integer publicationYear, String sourceUrl,
                   String version, String status, int chunkCount, String errorCode, String checksum) {}
    record Details(@JsonUnwrapped Summary summary, List<RagPdf.Chunk> chunks) {}
    record Upload(String title, String publisher, Integer publicationYear, String sourceUrl,
                  String version, String pages, String transcriptions) {}
    record Job(UUID id, UUID token, String filePath, String pages, String transcriptions) {}

    private final JdbcTemplate db;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;
    private final Path storage;

    RagDocuments(JdbcTemplate db, TransactionTemplate transaction, ObjectMapper json,
                 @Value("${app.rag-storage}") String storage) {
        this.db = db;
        this.transaction = transaction;
        this.json = json;
        this.storage = Path.of(storage).toAbsolutePath().normalize();
    }

    Summary upload(UUID admin, MultipartFile file, Upload input) {
        if (file.isEmpty()) throw ApiException.field("file", "Chọn một tệp PDF có nội dung.");
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ApiException(413, "PDF_TOO_LARGE", "Tệp PDF phải có kích thước tối đa 20 MB.");
        }
        String title = required(input.title(), "title", 250);
        String publisher = required(input.publisher(), "publisher", 200);
        String version = required(input.version(), "version", 80);
        Integer year = input.publicationYear();
        if (year != null && (year < 1 || year > 9999)) {
            throw ApiException.field("publicationYear", "Năm xuất bản phải nằm trong khoảng 1–9999.");
        }
        String source = sourceUrl(input.sourceUrl());
        String pages = bounded(input.pages(), "pages", 5_000);
        List<RagPdf.Range> ranges = RagPdf.ranges(pages);
        var reviewed = transcriptions(bounded(input.transcriptions(), "transcriptions", 64_000), ranges);
        String transcripts = json.valueToTree(reviewed).toString();

        UUID id = UUID.randomUUID();
        String fileName = id + ".pdf";
        Path path = storage.resolve(fileName);
        String checksum;
        try {
            Files.createDirectories(storage);
            var digest = MessageDigest.getInstance("SHA-256");
            try (var stream = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(stream, path);
            }
            checksum = HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            discard(path);
            throw new ApiException(503, "PDF_STORAGE_ERROR", "Chưa thể lưu bản gốc PDF. Vui lòng thử lại.");
        }

        var identity = Normalizer.normalize(title + "\n" + publisher, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        UUID key = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        try {
            db.update("""
                INSERT INTO rag_documents(id, document_key, title, publisher, publication_year, source_url,
                    version, checksum, file_path, status, selected_pages, verified_transcriptions, uploaded_by)
                VALUES (?,?,?,?,?,?,?,?,?,'QUEUED',?,CAST(? AS jsonb),?)
                """, id, key, title, publisher, year, source, version, checksum, fileName, pages,
                transcripts, admin);
        } catch (DuplicateKeyException e) {
            discard(path);
            throw new ApiException(409, "RAG_DOCUMENT_EXISTS", "Phiên bản tài liệu này đã tồn tại.");
        } catch (RuntimeException e) {
            discard(path);
            throw e;
        }
        return new Summary(id, title, publisher, year, source, version, "QUEUED", 0, null, checksum);
    }

    List<Summary> list() {
        return db.query(SUMMARIES + " ORDER BY d.created_at DESC, d.id DESC", SUMMARY_ROW);
    }

    Details details(UUID id) {
        return transaction.execute(status -> {
            // Use one DB snapshot so a simultaneous publication cannot mix pending status with ready chunks.
            db.query("SELECT id FROM rag_documents WHERE id=? FOR SHARE", (rs, row) -> rs.getObject(1, UUID.class), id);
            var summary = summary(id);
            var chunks = db.query("""
                SELECT page_start,page_end,content,extraction_method FROM rag_chunks WHERE document_id=?
                ORDER BY chunk_index LIMIT 60
                """, (rs, i) -> new RagPdf.Chunk(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4)), id);
            return new Details(summary, chunks);
        });
    }

    Summary activate(UUID id, String embeddingModel) {
        int updated = db.update("""
            UPDATE rag_documents d SET status='ACTIVE', activated_at=clock_timestamp()
            WHERE d.id=? AND d.status='READY'
              AND EXISTS (SELECT 1 FROM rag_chunks c WHERE c.document_id=d.id AND c.embedding_model=?)
              AND NOT EXISTS (SELECT 1 FROM rag_chunks c WHERE c.document_id=d.id AND c.embedding_model<>?)
            """, id, embeddingModel, embeddingModel);
        return transitioned(id, updated, "Chỉ có thể kích hoạt tài liệu đã xử lý với model embedding hiện hành.");
    }

    Summary revoke(UUID id) {
        int updated = db.update("""
            UPDATE rag_documents SET status='REVOKED', revoked_at=clock_timestamp() WHERE id=? AND status='ACTIVE'
            """, id);
        return transitioned(id, updated, "Chỉ có thể thu hồi tài liệu đang hoạt động.");
    }

    private Summary transitioned(UUID id, int updated, String message) {
        Summary summary = summary(id);
        if (updated == 0) throw new ApiException(409, "RAG_DOCUMENT_STATUS", message);
        return summary;
    }

    private Summary summary(UUID id) {
        return db.query(SUMMARIES + " WHERE d.id=?", SUMMARY_ROW, id).stream().findFirst().orElseThrow(RagDocuments::missing);
    }

    Resource adminOriginal(UUID id) {
        String file = db.query("SELECT file_path FROM rag_documents WHERE id=?", (rs, row) -> rs.getString(1), id)
            .stream().findFirst().orElseThrow(RagDocuments::missing);
        return original(file);
    }

    Resource original(String filePath) {
        if (filePath == null || !INTERNAL_PDF.matcher(filePath).matches()) throw missing();
        Path file = storage.resolve(filePath);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(file)) throw missing();
        return new FileSystemResource(file);
    }

    Job claim() {
        UUID token = UUID.randomUUID();
        return db.query("""
            UPDATE rag_documents d SET status='PROCESSING', processing_token=?,
                processing_until=clock_timestamp()+interval '5 minutes'
            WHERE d.id=(SELECT id FROM rag_documents
                WHERE status='QUEUED' OR (status='PROCESSING' AND processing_until <= clock_timestamp())
                ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED)
            RETURNING id, file_path, selected_pages, verified_transcriptions::text
            """, (rs, row) -> new Job(rs.getObject(1, UUID.class), token, rs.getString(2), rs.getString(3),
                rs.getString(4)), token).stream().findFirst().orElse(null);
    }

    void complete(Job job, List<RagPdf.Chunk> chunks, List<String> vectors, String model) {
        transaction.executeWithoutResult(status -> {
            if (finish(job, "READY", null) == 0) return;
            db.batchUpdate("""
                INSERT INTO rag_chunks(document_id,chunk_index,content,page_start,page_end,embedding,
                    embedding_model,extraction_method) VALUES (?,?,?,?,?,CAST(? AS public.vector),?,?)
                """, new BatchPreparedStatementSetter() {
                    @Override public int getBatchSize() { return chunks.size(); }

                    @Override public void setValues(PreparedStatement statement, int index) throws SQLException {
                        RagPdf.Chunk chunk = chunks.get(index);
                        statement.setObject(1, job.id());
                        statement.setInt(2, index);
                        statement.setString(3, chunk.content());
                        statement.setInt(4, chunk.pageStart());
                        statement.setInt(5, chunk.pageEnd());
                        statement.setString(6, vectors.get(index));
                        statement.setString(7, model);
                        statement.setString(8, chunk.extractionMethod());
                    }
                });
        });
    }

    void fail(Job job, String code) { finish(job, "FAILED", code); }

    private int finish(Job job, String status, String error) {
        return db.update("""
            UPDATE rag_documents SET status=?, error_code=?, processed_at=clock_timestamp(),
                processing_token=NULL, processing_until=NULL
            WHERE id=? AND status='PROCESSING' AND processing_token=? AND processing_until>clock_timestamp()
            """, status, error, job.id(), job.token());
    }

    Map<Integer, String> transcriptions(String text, List<RagPdf.Range> ranges) {
        if (text.isBlank()) return Map.of();
        try {
            JsonNode node = json.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(text);
            if (!node.isObject()) throw new IllegalArgumentException();
            Map<Integer, String> result = new HashMap<>();
            for (var entry : node.properties()) {
                int page = Integer.parseInt(entry.getKey());
                String content = entry.getValue().asText("");
                if (page < 1 || !Integer.toString(page).equals(entry.getKey()) || !entry.getValue().isTextual()
                    || !RagPdf.usable(content) || content.length() > 8_000
                    || (!ranges.isEmpty() && ranges.stream().noneMatch(range -> range.includes(page)))) {
                    throw new IllegalArgumentException();
                }
                result.put(page, RagPdf.clean(content));
            }
            return result;
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw ApiException.field("transcriptions", "Nhập JSON ánh xạ số trang đã chọn với nội dung đối chiếu, tối đa 8.000 ký tự mỗi trang.");
        }
    }

    private static String sourceUrl(String input) {
        String value = bounded(input, "sourceUrl", 2_000);
        if (value.isEmpty()) return null;
        try {
            URI uri = URI.create(value);
            if (uri.getHost() != null && uri.getUserInfo() == null
                && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) return value;
        } catch (IllegalArgumentException ignored) {
            // Return the same field error for malformed and unsupported URLs.
        }
        throw ApiException.field("sourceUrl", "Nhập URL bắt đầu bằng http:// hoặc https://.");
    }

    private static String required(String input, String field, int limit) {
        String value = bounded(input, field, limit);
        if (value.isEmpty() || value.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiException.field(field, "Nhập thông tin tài liệu hợp lệ.");
        }
        return value;
    }

    private static String bounded(String input, String field, int limit) {
        String value = input == null ? "" : input.strip();
        if (value.length() > limit) throw ApiException.field(field, "Tối đa " + limit + " ký tự.");
        return value;
    }

    private static void discard(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.warn("Could not remove unregistered RAG PDF ({})", e.getClass().getSimpleName());
        }
    }

    private static ApiException missing() {
        return new ApiException(404, "RAG_DOCUMENT_NOT_FOUND", "Không tìm thấy tài liệu PDF.");
    }
}
