package com.ffreader.api.service;

import com.ffreader.api.domain.Chapter;
import com.ffreader.api.domain.Fanfic;
import com.ffreader.api.domain.FanficStatus;
import com.ffreader.api.dto.CreateFanficRequest;
import com.ffreader.api.exception.FanficBusyException;
import com.ffreader.api.exception.FanficNotFoundException;
import com.ffreader.api.exception.PdfNotReadyException;
import com.ffreader.api.repository.ChapterRepository;
import com.ffreader.api.repository.FanficRepository;
import com.ffreader.api.service.scraper.FanficUrl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.core.task.TaskRejectedException;
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

    public Fanfic create(CreateFanficRequest request) {
        FanficUrl parsed = FanficUrl.parse(request.url());

        Fanfic existing = fanficRepository.findByExternalId(parsed.storyId()).orElse(null);
        if (existing == null) {
            return createAndQueue(request, parsed);
        }

        if (!parsingService.tryReserve(existing.getId())) {
            log.info("Fanfic {} is already being parsed; not queuing it again", parsed.storyId());
            return existing;
        }
        log.info("Fanfic {} already exists (status={}); re-queuing parse job", parsed.storyId(), existing.getStatus());
        try {
            existing.setStatus(FanficStatus.PENDING);
            existing.setErrorMessage(null);
            Fanfic saved = fanficRepository.save(existing);
            parsingService.parseAsync(saved.getId());
            return saved;
        } catch (RuntimeException e) {
            parsingService.release(existing.getId());
            throw e;
        }
    }

    /** A new row has no id until it is saved, so it can only be reserved afterwards. */
    private Fanfic createAndQueue(CreateFanficRequest request, FanficUrl parsed) {
        Fanfic fanfic = new Fanfic();
        fanfic.setSourceUrl(request.url());
        fanfic.setExternalId(parsed.storyId());
        fanfic.setStatus(FanficStatus.PENDING);
        fanfic = fanficRepository.save(fanfic);

        if (!parsingService.tryReserve(fanfic.getId())) {
            log.info("Fanfic {} was queued by another request; not queuing it again", parsed.storyId());
            return fanfic;
        }
        try {
            parsingService.parseAsync(fanfic.getId());
        } catch (TaskRejectedException e) {
            parsingService.release(fanfic.getId());
            throw e;
        }
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

        // A queued or running job would keep writing files for a story that no longer exists.
        // Holding the reservation also stops a re-submit from starting a job during the delete.
        if (!parsingService.tryReserve(id)) {
            throw new FanficBusyException(id);
        }
        try {
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
        } finally {
            parsingService.release(id);
        }
    }

    @Transactional(readOnly = true)
    public Path getPdfPath(UUID id) {
        Fanfic fanfic = get(id);
        if ((fanfic.getStatus() != FanficStatus.PARSED && fanfic.getStatus() != FanficStatus.PARSED_PARTIALLY) || fanfic.getPdfPath() == null) {
            throw new PdfNotReadyException(id);
        }
        Path path = Path.of(fanfic.getPdfPath());
        if (!Files.exists(path)) {
            throw new PdfNotReadyException(id);
        }
        return path;
    }
}
