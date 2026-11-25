package vn.nutrition.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.nutrition.common.ApiException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
class RagDocumentWorker {
    private static final Logger LOG = LoggerFactory.getLogger(RagDocumentWorker.class);
    private static final Duration EMBEDDING_BUDGET = Duration.ofMinutes(3);
    private static final int EMBEDDING_BATCH = 32;

    private final RagDocuments documents;
    private final RagAi ai;
    private final AtomicBoolean busy = new AtomicBoolean();

    RagDocumentWorker(RagDocuments documents, RagAi ai) {
        this.documents = documents;
        this.ai = ai;
    }

    // ponytail: one document per backend instance. The database claim prevents simultaneous work on the same PDF.
    @Scheduled(fixedDelay = 500)
    void poll() {
        if (!busy.compareAndSet(false, true)) return;
        Thread.startVirtualThread(() -> {
            try {
                var job = documents.claim();
                if (job != null) process(job);
            } catch (RuntimeException e) {
                LOG.warn("RAG document queue error ({})", e.getClass().getSimpleName());
            } finally {
                busy.set(false);
            }
        });
    }

    private void process(RagDocuments.Job job) {
        try {
            var file = documents.original(job.filePath());
            var ranges = RagPdf.ranges(job.pages());
            var chunks = RagPdf.read(file, ranges, documents.transcriptions(job.transcriptions(), ranges));
            String model = ai.embeddingModel();
            if (model == null || model.isBlank()) throw new RagPdf.Failure("RAG_EMBEDDING_UNAVAILABLE");
            documents.complete(job, chunks, embed(chunks), model);
        } catch (RagPdf.Failure e) {
            documents.fail(job, e.getMessage());
        } catch (ApiException e) {
            documents.fail(job, e.status == 404 ? "PDF_STORAGE_ERROR" : "RAG_DOCUMENT_ERROR");
        } catch (RuntimeException e) {
            LOG.warn("RAG document {} failed internally ({})", job.id(), e.getClass().getSimpleName());
            documents.fail(job, "RAG_DOCUMENT_ERROR");
        }
    }

    private List<String> embed(List<RagPdf.Chunk> chunks) {
        long deadline = System.nanoTime() + EMBEDDING_BUDGET.toNanos();
        List<String> vectors = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += EMBEDDING_BATCH) {
            var batch = chunks.subList(start, Math.min(start + EMBEDDING_BATCH, chunks.size()));
            List<float[]> response;
            try {
                response = ai.embed(batch.stream().map(RagPdf.Chunk::content).toList(),
                    Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
            } catch (RuntimeException e) {
                throw new RagPdf.Failure("RAG_EMBEDDING_UNAVAILABLE");
            }
            if (response == null || response.size() != batch.size()) {
                throw new RagPdf.Failure("RAG_EMBEDDING_INVALID");
            }
            try {
                response.stream().map(RagAi::vector).forEach(vectors::add);
            } catch (RuntimeException e) {
                throw new RagPdf.Failure("RAG_EMBEDDING_INVALID");
            }
        }
        if (System.nanoTime() > deadline) throw new RagPdf.Failure("RAG_EMBEDDING_UNAVAILABLE");
        return vectors;
    }
}
