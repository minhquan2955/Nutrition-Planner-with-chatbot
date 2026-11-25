package vn.nutrition.rag;

import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import vn.nutrition.common.ApiException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class RagPdf {
    private static final int PAGE_LIMIT = 2_000;
    private static final int PAGE_TEXT_LIMIT = 150_000;
    private static final int CHUNK_LIMIT = 5_000;
    private static final int CHUNK_TOKENS = 500;
    private static final int OVERLAP_TOKENS = 100;
    private static final PdfDocumentReaderConfig READER_CONFIG = PdfDocumentReaderConfig.builder()
        .withPagesPerDocument(1)
        .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
            .withNumberOfTopTextLinesToDelete(0).withNumberOfBottomTextLinesToDelete(0).build())
        .build();

    record Chunk(int pageStart, int pageEnd, String content, String extractionMethod) {}
    record Range(int first, int last) {
        boolean includes(int page) { return page >= first && page <= last; }
    }
    private record Line(String text, int tokens) {}
    static final class Failure extends RuntimeException {
        Failure(String code) { super(code); }
    }

    static List<Range> ranges(String pages) {
        if (pages.isBlank()) return List.of();
        List<Range> ranges = new ArrayList<>();
        for (String part : pages.split(",", -1)) {
            String[] edges = part.strip().split("\\s*-\\s*", -1);
            try {
                int first = Integer.parseInt(edges[0]);
                int last = edges.length == 2 ? Integer.parseInt(edges[1]) : first;
                if (edges.length > 2 || first < 1 || last < first) throw new NumberFormatException();
                ranges.add(new Range(first, last));
            } catch (NumberFormatException e) {
                throw ApiException.field("pages", "Nhập số trang hoặc khoảng trang, ví dụ 14,25-26.");
            }
        }
        return ranges;
    }

    static List<Chunk> read(Resource file, List<Range> ranges, Map<Integer, String> transcriptions) {
        List<Document> pages;
        int pageCount;
        try (var input = file.getInputStream(); var reader = new Reader(input, file.getFilename())) {
            pageCount = reader.pageCount();
            if (pageCount > PAGE_LIMIT) throw new Failure("PDF_TOO_COMPLEX");
            if (ranges.stream().anyMatch(range -> range.last() > pageCount)
                || transcriptions.keySet().stream().anyMatch(page -> page > pageCount)) {
                throw new Failure("PDF_PAGE_OUT_OF_RANGE");
            }
            pages = reader.get();
        } catch (Failure e) {
            throw e;
        } catch (RuntimeException | IOException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof InvalidPasswordException) throw new Failure("PDF_PASSWORD_REQUIRED");
            }
            throw new Failure("PDF_INVALID");
        }

        var selected = new HashSet<Integer>();
        for (Range range : ranges) {
            for (int page = range.first(); page <= range.last(); page++) selected.add(page);
        }
        var raw = pages.stream().collect(Collectors.toMap(
            page -> ((Number) page.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER)).intValue(),
            page -> clean(page.getText())));
        if (raw.values().stream().noneMatch(RagPdf::usable)) throw new Failure("PDF_NO_TEXT");

        List<Chunk> chunks = new ArrayList<>();
        for (int page = 1; page <= pageCount; page++) {
            if (!selected.isEmpty() && !selected.contains(page)) continue;
            String excerpt = clean(transcriptions.getOrDefault(page, raw.getOrDefault(page, "")));
            if (!usable(excerpt)) {
                if (!selected.isEmpty()) throw new Failure("PDF_NO_TEXT");
                continue;
            }
            if (excerpt.length() > PAGE_TEXT_LIMIT) throw new Failure("PDF_TOO_COMPLEX");
            String method = transcriptions.containsKey(page) ? "VERIFIED_TRANSCRIPTION" : "PDF_TEXT";
            for (String content : split(excerpt)) {
                chunks.add(new Chunk(page, page, content, method));
            }
            if (chunks.size() > CHUNK_LIMIT) throw new Failure("PDF_TOO_COMPLEX");
        }
        if (chunks.isEmpty()) throw new Failure("PDF_NO_TEXT");
        return chunks;
    }

    static String clean(String text) {
        return text.replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "").strip();
    }

    static boolean usable(String text) {
        // Page numbers or punctuation alone are common in scanned documents.
        return text.codePoints().filter(Character::isLetter).limit(3).count() == 3;
    }

    private static List<String> split(String text) {
        List<Line> lines = new ArrayList<>();
        text.lines().map(String::stripTrailing).filter(line -> !line.isBlank()).forEach(line -> addLine(line, lines));
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int end = 0;
        int tokens = 0;
        while (end < lines.size()) {
            while (start < end && tokens + lines.get(end).tokens() > CHUNK_TOKENS) {
                tokens -= lines.get(start++).tokens();
            }
            while (end < lines.size() && tokens + lines.get(end).tokens() <= CHUNK_TOKENS) {
                tokens += lines.get(end++).tokens();
            }
            chunks.add(lines.subList(start, end).stream().map(Line::text).collect(Collectors.joining("\n")).strip());
            int previousStart = start;
            start = end;
            tokens = 0;
            while (start > previousStart && tokens + lines.get(start - 1).tokens() <= OVERLAP_TOKENS) {
                tokens += lines.get(--start).tokens();
            }
        }
        return chunks;
    }

    private static void addLine(String line, List<Line> lines) {
        int tokens = tokenCount(line);
        if (tokens <= CHUNK_TOKENS) {
            lines.add(new Line(line, tokens));
            return;
        }
        // Oversized lines have no usable row boundary. Split on Unicode character boundaries.
        int start = 0;
        int remaining = line.codePointCount(0, line.length());
        while (remaining > 0) {
            int low = 1;
            int high = Math.min(2_000, remaining);
            int count = 1;
            while (low <= high) {
                int middle = (low + high) / 2;
                int end = line.offsetByCodePoints(start, middle);
                if (tokenCount(line.substring(start, end)) <= OVERLAP_TOKENS) {
                    count = middle;
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            int end = line.offsetByCodePoints(start, count);
            String part = line.substring(start, end);
            lines.add(new Line(part, tokenCount(part)));
            start = end;
            remaining -= count;
        }
    }

    private static int tokenCount(String line) {
        return RagAi.EMBEDDING_TOKENIZER.countTokensOrdinary(line + "\n");
    }

    private static final class Reader extends PagePdfDocumentReader implements AutoCloseable {
        Reader(InputStream input, String filename) {
            super(new InputStreamResource(input) {
                @Override public String getFilename() { return filename; }
            }, READER_CONFIG);
        }

        int pageCount() { return document.getNumberOfPages(); }

        @Override public void close() throws IOException { document.close(); }
    }
}
