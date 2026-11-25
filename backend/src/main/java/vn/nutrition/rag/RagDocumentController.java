package vn.nutrition.rag;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.nutrition.auth.Account;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/rag-documents")
class RagDocumentController {
    private final RagDocuments documents;
    private final RagAi ai;

    RagDocumentController(RagDocuments documents, RagAi ai) {
        this.documents = documents;
        this.ai = ai;
    }

    @GetMapping
    List<RagDocuments.Summary> list() { return documents.list(); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Nạp PDF tối đa 20 MB để xử lý và duyệt",
        description = "Trang vật lý bắt đầu từ 1. Ví dụ pages=14,25-26. transcriptions là JSON ánh xạ số trang tới nội dung đã đối chiếu.")
    RagDocuments.Summary upload(@AuthenticationPrincipal Account account, @RequestPart MultipartFile file,
                                @RequestParam String title, @RequestParam String publisher, @RequestParam String version,
                                @RequestParam(required = false) Integer publicationYear,
                                @RequestParam(required = false) String sourceUrl,
                                @RequestParam(required = false) String pages,
                                @RequestParam(required = false) String transcriptions) {
        return documents.upload(account.id(), file,
            new RagDocuments.Upload(title, publisher, publicationYear, sourceUrl, version, pages, transcriptions));
    }

    @GetMapping("/{id}")
    RagDocuments.Details details(@PathVariable UUID id) { return documents.details(id); }

    @GetMapping(value = "/{id}/file", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<Resource> original(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=original.pdf")
            .body(documents.adminOriginal(id));
    }

    @PostMapping("/{id}/activate")
    RagDocuments.Summary activate(@PathVariable UUID id) { return documents.activate(id, ai.embeddingModel()); }

    @PostMapping("/{id}/revoke")
    RagDocuments.Summary revoke(@PathVariable UUID id) { return documents.revoke(id); }
}
