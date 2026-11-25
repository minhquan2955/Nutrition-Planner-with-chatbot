package vn.nutrition.rag;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.nutrition.auth.Account;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
class ChatController {
    private final ChatConversations conversations;
    private final RagDocuments documents;

    record NewConversation(String title) {}
    record Question(String content, UUID requestId) {}

    ChatController(ChatConversations conversations, RagDocuments documents) {
        this.conversations = conversations;
        this.documents = documents;
    }

    @GetMapping
    public List<ChatConversations.Summary> list(@AuthenticationPrincipal Account account) {
        return conversations.list(account.id());
    }

    @PostMapping
    public ResponseEntity<ChatConversations.Summary> create(@AuthenticationPrincipal Account account,
                                                          @RequestBody NewConversation input) {
        var conversation = conversations.create(account.id(), input.title());
        return ResponseEntity.created(URI.create("/api/v1/conversations/" + conversation.id())).body(conversation);
    }

    @GetMapping("/{id}")
    public ChatConversations.Detail detail(@AuthenticationPrincipal Account account, @PathVariable UUID id) {
        return conversations.detail(account.id(), id);
    }

    @PostMapping("/{id}/messages")
    public ChatConversations.Detail send(@AuthenticationPrincipal Account account, @PathVariable UUID id,
                                         @RequestBody Question question) {
        return conversations.send(account, id, question.content(), question.requestId());
    }

    @GetMapping("/{id}/sources/{sourceId}/file")
    public ResponseEntity<Resource> file(@AuthenticationPrincipal Account account, @PathVariable UUID id,
                                         @PathVariable UUID sourceId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"source.pdf\"")
            .header("X-Content-Type-Options", "nosniff")
            .body(documents.original(conversations.sourceFile(account.id(), id, sourceId)));
    }
}
