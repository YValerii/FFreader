package com.ffreader.api.controller;

import com.ffreader.api.domain.Fanfic;
import com.ffreader.api.dto.CreateFanficRequest;
import com.ffreader.api.dto.FanficResponse;
import com.ffreader.api.dto.PageResponse;
import com.ffreader.api.service.FanficService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/fanfics")
public class FanficController {

    private final FanficService fanficService;

    public FanficController(FanficService fanficService) {
        this.fanficService = fanficService;
    }

    /** Endpoint #1: submit a fanfiction.net URL. Kicks off async parsing and returns immediately. */
    @PostMapping
    public ResponseEntity<FanficResponse> create(@Valid @RequestBody CreateFanficRequest request) {
        Fanfic fanfic = fanficService.create(request);
        return ResponseEntity.accepted().body(FanficResponse.from(fanfic));
    }

    /** Endpoint #8: paginated list of all loaded fanfics. */
    @GetMapping
    public PageResponse<FanficResponse> list(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<FanficResponse> page = fanficService.list(pageable).map(FanficResponse::from);
        return PageResponse.from(page);
    }

    /** Convenience endpoint to poll a single fanfic's parsing status. */
    @GetMapping("/{id}")
    public FanficResponse get(@PathVariable UUID id) {
        return FanficResponse.from(fanficService.get(id));
    }

    /** Endpoint #9: download the fully parsed fanfic as a PDF. */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        Path pdfPath = fanficService.getPdfPath(id);
        Resource resource = new FileSystemResource(pdfPath);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(pdfPath.getFileName().toString(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    /** Delete functionality: removes the fanfic, its chapters, extraction files and PDF. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        fanficService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
