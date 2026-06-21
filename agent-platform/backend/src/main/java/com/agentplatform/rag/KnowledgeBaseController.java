package com.agentplatform.rag;

import com.agentplatform.common.ApiResponse;
import com.agentplatform.rag.dto.DocumentResponse;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import com.agentplatform.rag.dto.KnowledgeBaseResponse;
import com.agentplatform.rag.dto.RetrieveResult;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;
    private final DocumentService documentService;
    private final RagRetriever ragRetriever;

    public KnowledgeBaseController(KnowledgeBaseService service,
                                   DocumentService documentService,
                                   RagRetriever ragRetriever) {
        this.service = service;
        this.documentService = documentService;
        this.ragRetriever = ragRetriever;
    }

    // ===== 知识库 CRUD =====

    @PostMapping
    public ApiResponse<KnowledgeBaseResponse> create(@Valid @RequestBody KnowledgeBaseRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseResponse>> listAll() {
        return ApiResponse.ok(service.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    // ===== 文档 =====

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentResponse> upload(@PathVariable Long id,
                                                @RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String type = filename != null && filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1) : "txt";
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        return ApiResponse.ok(documentService.upload(id, filename, type, content));
    }

    @GetMapping("/{id}/documents")
    public ApiResponse<List<DocumentResponse>> listDocs(@PathVariable Long id) {
        return ApiResponse.ok(documentService.list(id));
    }

    @DeleteMapping("/{id}/documents/{docId}")
    public ApiResponse<Void> deleteDoc(@PathVariable Long id, @PathVariable Long docId) {
        documentService.delete(docId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/documents/{docId}/reprocess")
    public ApiResponse<Void> reprocessDoc(@PathVariable Long id, @PathVariable Long docId) {
        documentService.reprocess(docId);
        return ApiResponse.ok(null);
    }

    // ===== 检索测试 =====

    public record RetrieveRequest(String query, Integer topK) {}

    @PostMapping("/{id}/retrieve")
    public ApiResponse<List<RetrieveResult>> retrieve(@PathVariable Long id,
                                                      @RequestBody RetrieveRequest req) {
        int k = req.topK() != null ? req.topK() : 3;
        return ApiResponse.ok(ragRetriever.retrieve(List.of(id), req.query(), k));
    }
}
