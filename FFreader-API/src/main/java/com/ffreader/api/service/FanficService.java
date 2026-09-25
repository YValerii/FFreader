package com.ffreader.api.service;

import com.ffreader.api.domain.Chapter;
import com.ffreader.api.domain.Fanfic;
import com.ffreader.api.domain.FanficStatus;
import com.ffreader.api.dto.CreateFanficRequest;
import com.ffreader.api.exception.FanficNotFoundException;
import com.ffreader.api.exception.PdfNotReadyException;
import com.ffreader.api.repository.ChapterRepository;
import com.ffreader.api.repository.FanficRepository;
import com.ffreader.api.service.scraper.FanficUrl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
public class FanficService {

    private final FanficRepository fanficRepository;
    private final ChapterRepository chapterRepository;
    private final ExtractionFileService extractionFileService;
    private final FanficParsingService parsingService;

    public FanficService(FanficRepository fanficRepository,
                          ChapterRepository chapterRepository,
                          ExtractionFileService extractionFileService,
                          FanficParsingService parsingService) {
        this.fanficRepository = fanficRepository;
        this.chapterRepository = chapterRepository;
        this.extractionFileService = extractionFileService;
        this.parsingService = parsingService;
    }

    @Transactional
    public Fanfic create(CreateFanficRequest request) {
        FanficUrl parsed = FanficUrl.parse(request.url());

        Fanfic fanfic = fanficRepository.findByExternalId(parsed.storyId()).orElse(null);
        if (fanfic != null) {
            log.info("Fanfic {} already exists (status={}); re-queuing parse job", parsed.storyId(), fanfic.getStatus());
            fanfic.setStatus(FanficStatus.PENDING);
            fanfic.setErrorMessage(null);
        } else {
            fanfic = new Fanfic();
            fanfic.setSourceUrl(request.url());
            fanfic.setExternalId(parsed.storyId());
            fanfic.setStatus(FanficStatus.PENDING);
        }
        fanfic = fanficRepository.save(fanfic);

        parsingService.parseAsync(fanfic.getId());
        return fanfic;
    }

    @Transactional(readOnly = true)
    public Page<Fanfic> list(Pageable pageable) {
        return fanficRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Fanfic get(UUID id) {
        return fanficRepository.findById(id).orElseThrow(() -> new FanficNotFoundException(id));
    }

    @Transactional
    public void delete(UUID id) {
        Fanfic fanfic = get(id);

        for (Chapter chapter : chapterRepository.findByFanficOrderByChapterNumberAsc(fanfic)) {
            extractionFileService.deleteChapter(chapter.getExtractionFileName());
        }

        if (fanfic.getPdfPath() != null) {
            try {
                Files.deleteIfExists(Path.of(fanfic.getPdfPath()));
            } catch (IOException e) {
                log.warn("Could not delete PDF file {}: {}", fanfic.getPdfPath(), e.getMessage());
            }
        }

        fanficRepository.delete(fanfic);
    }

    @Transactional(readOnly = true)
    public Path getPdfPath(UUID id) {
        Fanfic fanfic = get(id);
        if (fanfic.getStatus() != FanficStatus.PARSED || fanfic.getPdfPath() == null) {
            throw new PdfNotReadyException(id);
        }
        Path path = Path.of(fanfic.getPdfPath());
        if (!Files.exists(path)) {
            throw new PdfNotReadyException(id);
        }
        return path;
    }
}
