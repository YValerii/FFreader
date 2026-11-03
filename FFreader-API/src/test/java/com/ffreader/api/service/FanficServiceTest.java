package com.ffreader.api.service;

import com.ffreader.api.domain.Chapter;
import com.ffreader.api.domain.Fanfic;
import com.ffreader.api.domain.FanficStatus;
import com.ffreader.api.dto.CreateFanficRequest;
import com.ffreader.api.exception.FanficBusyException;
import com.ffreader.api.exception.FanficNotFoundException;
import com.ffreader.api.exception.FanficScrapeException;
import com.ffreader.api.exception.PdfNotReadyException;
import com.ffreader.api.repository.ChapterRepository;
import com.ffreader.api.repository.FanficRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FanficServiceTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String URL = "https://www.fanfiction.net/s/123/1/Story";

    @Mock FanficRepository fanficRepository;
    @Mock ChapterRepository chapterRepository;
    @Mock ExtractionFileService extractionFileService;
    @Mock FanficParsingService parsingService;

    @InjectMocks FanficService service;

    // ---- create ---------------------------------------------------------

    @Test
    void createStoresANewStoryAndStartsParsing() {
        when(fanficRepository.findByExternalId("123")).thenReturn(Optional.empty());
        saveAssignsId();
        when(parsingService.tryReserve(ID)).thenReturn(true);

        Fanfic created = service.create(new CreateFanficRequest(URL));

        assertThat(created.getId()).isEqualTo(ID);
        assertThat(created.getSourceUrl()).isEqualTo(URL);
        assertThat(created.getExternalId()).isEqualTo("123");
        assertThat(created.getStatus()).isEqualTo(FanficStatus.PENDING);
        verify(parsingService).parseAsync(ID);
    }

    @Test
    void createRequeuesAFinishedStory() {
        Fanfic existing = fanfic(FanficStatus.FAILED);
        existing.setErrorMessage("old failure");
        when(fanficRepository.findByExternalId("123")).thenReturn(Optional.of(existing));
        saveAssignsId();
        when(parsingService.tryReserve(ID)).thenReturn(true);

        Fanfic result = service.create(new CreateFanficRequest(URL));

        assertThat(result).isSameAs(existing);
        assertThat(result.getStatus()).isEqualTo(FanficStatus.PENDING);
        assertThat(result.getErrorMessage()).isNull();
        verify(parsingService).parseAsync(ID);
    }

    @Test
    void createMatchesExistingStoriesByIdWhateverTheChapterOrSlug() {
        Fanfic existing = fanfic(FanficStatus.PARSED);
        when(fanficRepository.findByExternalId("123")).thenReturn(Optional.of(existing));
        when(parsingService.tryReserve(ID)).thenReturn(false);

        service.create(new CreateFanficRequest("http://fanfiction.net/s/123/7/Other-Slug"));

        verify(fanficRepository).findByExternalId("123");
    }

    @Test
    void createLeavesAStoryAloneWhileItIsBeingParsed() {
        Fanfic running = fanfic(FanficStatus.PARSING);
        when(fanficRepository.findByExternalId("123")).thenReturn(Optional.of(running));
        when(parsingService.tryReserve(ID)).thenReturn(false);

        Fanfic result = service.create(new CreateFanficRequest(URL));

        assertThat(result.getStatus()).isEqualTo(FanficStatus.PARSING);
        verify(fanficRepository, never()).save(any());
        verify(parsingService, never()).parseAsync(any());
    }

    @Test
    void createGivesBackTheReservationWhenTheQueueIsFull() {
        when(fanficRepository.findByExternalId("123")).thenReturn(Optional.empty());
        saveAssignsId();
        when(parsingService.tryReserve(ID)).thenReturn(true);
        doThrow(new TaskRejectedException("full")).when(parsingService).parseAsync(ID);

        assertThatThrownBy(() -> service.create(new CreateFanficRequest(URL)))
                .isInstanceOf(TaskRejectedException.class);

        verify(parsingService).release(ID);
    }

    @Test
    void createGivesBackTheReservationWhenTheQueueIsFullForAnExistingStory() {
        when(fanficRepository.findByExternalId("123")).thenReturn(Optional.of(fanfic(FanficStatus.FAILED)));
        saveAssignsId();
        when(parsingService.tryReserve(ID)).thenReturn(true);
        doThrow(new TaskRejectedException("full")).when(parsingService).parseAsync(ID);

        assertThatThrownBy(() -> service.create(new CreateFanficRequest(URL)))
                .isInstanceOf(TaskRejectedException.class);

        verify(parsingService).release(ID);
    }

    @Test
    void createRejectsUnparseableUrlsBeforeTouchingAnything() {
        assertThatThrownBy(() -> service.create(new CreateFanficRequest("https://example.com/s/1")))
                .isInstanceOf(FanficScrapeException.class);

        verifyNoInteractions(fanficRepository, parsingService);
    }

    @Test
    void createDoesNotStartASecondJobWhenTheReservationIsLost() {
        when(fanficRepository.findByExternalId("123")).thenReturn(Optional.empty());
        saveAssignsId();
        when(parsingService.tryReserve(ID)).thenReturn(false);

        service.create(new CreateFanficRequest(URL));

        verify(parsingService, never()).parseAsync(ID);
        // The reservation belongs to the other request's job.
        verify(parsingService, never()).release(ID);
    }

    @Test
    void createGivesBackTheReservationWhenSavingFails() {
        when(fanficRepository.findByExternalId("123")).thenReturn(Optional.of(fanfic(FanficStatus.FAILED)));
        when(parsingService.tryReserve(ID)).thenReturn(true);
        when(fanficRepository.save(any())).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> service.create(new CreateFanficRequest(URL)))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(parsingService).release(ID);
    }

    // ---- read -----------------------------------------------------------

    @Test
    void listReturnsTheRepositoryPage() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<Fanfic> page = new PageImpl<>(List.of(fanfic(FanficStatus.PARSED)));
        when(fanficRepository.findAll(pageable)).thenReturn(page);

        assertThat(service.list(pageable)).isSameAs(page);
    }

    @Test
    void getReturnsTheFanfic() {
        Fanfic fanfic = fanfic(FanficStatus.PARSED);
        when(fanficRepository.findById(ID)).thenReturn(Optional.of(fanfic));

        assertThat(service.get(ID)).isSameAs(fanfic);
    }

    @Test
    void getThrowsForAnUnknownId() {
        when(fanficRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ID))
                .isInstanceOf(FanficNotFoundException.class)
                .hasMessageContaining(ID.toString());
    }

    // ---- delete ---------------------------------------------------------

    @Test
    void deleteRemovesFilesPdfAndRow(@TempDir Path dir) throws IOException {
        Path pdf = Files.createFile(dir.resolve("story.pdf"));
        Fanfic fanfic = fanfic(FanficStatus.PARSED);
        fanfic.setPdfPath(pdf.toString());
        when(fanficRepository.findById(ID)).thenReturn(Optional.of(fanfic));
        when(chapterRepository.findByFanficOrderByChapterNumberAsc(fanfic))
                .thenReturn(List.of(chapter("one.txt"), chapter("two.txt")));
        when(parsingService.tryReserve(ID)).thenReturn(true);

        service.delete(ID);

        verify(parsingService).release(ID);
        verify(extractionFileService).deleteChapter("one.txt");
        verify(extractionFileService).deleteChapter("two.txt");
        assertThat(pdf).doesNotExist();
        verify(fanficRepository).delete(fanfic);
    }

    @Test
    void deleteWorksWithoutAPdf() {
        Fanfic fanfic = fanfic(FanficStatus.FAILED);
        when(fanficRepository.findById(ID)).thenReturn(Optional.of(fanfic));
        when(chapterRepository.findByFanficOrderByChapterNumberAsc(fanfic)).thenReturn(List.of());
        when(parsingService.tryReserve(ID)).thenReturn(true);

        service.delete(ID);

        verify(fanficRepository).delete(fanfic);
    }

    @Test
    void deleteStillRemovesTheRowWhenThePdfCannotBeDeleted(@TempDir Path dir) throws IOException {
        Path undeletable = Files.createDirectory(dir.resolve("story.pdf"));
        Files.createFile(undeletable.resolve("child"));
        Fanfic fanfic = fanfic(FanficStatus.PARSED);
        fanfic.setPdfPath(undeletable.toString());
        when(fanficRepository.findById(ID)).thenReturn(Optional.of(fanfic));
        when(chapterRepository.findByFanficOrderByChapterNumberAsc(fanfic)).thenReturn(List.of());
        when(parsingService.tryReserve(ID)).thenReturn(true);

        service.delete(ID);

        verify(fanficRepository).delete(fanfic);
    }

    @Test
    void deleteThrowsForAnUnknownIdWithoutDeletingAnything() {
        when(fanficRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(ID)).isInstanceOf(FanficNotFoundException.class);

        verify(fanficRepository, never()).delete(any());
        verifyNoInteractions(extractionFileService);
    }

    @Test
    void deleteRefusesAStoryWithAQueuedOrRunningJob() {
        when(fanficRepository.findById(ID)).thenReturn(Optional.of(fanfic(FanficStatus.PARSING)));
        when(parsingService.tryReserve(ID)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(ID)).isInstanceOf(FanficBusyException.class);

        verify(fanficRepository, never()).delete(any());
        verifyNoInteractions(extractionFileService);
        // The reservation belongs to the running job.
        verify(parsingService, never()).release(ID);
    }

    @Test
    void deleteUsesTheReservationNotTheStatus() {
        // A story left in PARSING by a restart has no job, and must stay deletable.
        Fanfic stuck = fanfic(FanficStatus.PARSING);
        when(fanficRepository.findById(ID)).thenReturn(Optional.of(stuck));
        when(chapterRepository.findByFanficOrderByChapterNumberAsc(stuck)).thenReturn(List.of());
        when(parsingService.tryReserve(ID)).thenReturn(true);

        service.delete(ID);

        verify(fanficRepository).delete(stuck);
    }

    @Test
    void deleteGivesBackTheReservationWhenItFails() {
        Fanfic fanfic = fanfic(FanficStatus.PARSED);
        when(fanficRepository.findById(ID)).thenReturn(Optional.of(fanfic));
        when(parsingService.tryReserve(ID)).thenReturn(true);
        when(chapterRepository.findByFanficOrderByChapterNumberAsc(fanfic))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> service.delete(ID)).isInstanceOf(DataAccessResourceFailureException.class);

        verify(parsingService).release(ID);
    }

    // ---- getPdfPath -----------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = FanficStatus.class, names = {"PARSED", "PARSED_PARTIALLY"})
    void pdfPathIsAvailableOnceParsed(FanficStatus status, @TempDir Path dir) throws IOException {
        Path pdf = Files.createFile(dir.resolve("story.pdf"));
        Fanfic fanfic = fanfic(status);
        fanfic.setPdfPath(pdf.toString());
        when(fanficRepository.findById(ID)).thenReturn(Optional.of(fanfic));

        assertThat(service.getPdfPath(ID)).isEqualTo(pdf);
    }

    @ParameterizedTest
    @EnumSource(value = FanficStatus.class, names = {"PENDING", "PARSING", "FAILED"})
    void pdfPathIsNotReadyBeforeParsingFinishes(FanficStatus status, @TempDir Path dir) throws IOException {
        Fanfic fanfic = fanfic(status);
        fanfic.setPdfPath(Files.createFile(dir.resolve("story.pdf")).toString());
        when(fanficRepository.findById(ID)).thenReturn(Optional.of(fanfic));

        assertThatThrownBy(() -> service.getPdfPath(ID)).isInstanceOf(PdfNotReadyException.class);
    }

    @Test
    void pdfPathIsNotReadyWhenNoneWasGenerated() {
        when(fanficRepository.findById(ID)).thenReturn(Optional.of(fanfic(FanficStatus.PARSED_PARTIALLY)));

        assertThatThrownBy(() -> service.getPdfPath(ID)).isInstanceOf(PdfNotReadyException.class);
    }

    @Test
    void pdfPathIsNotReadyWhenTheFileIsGone(@TempDir Path dir) {
        Fanfic fanfic = fanfic(FanficStatus.PARSED);
        fanfic.setPdfPath(dir.resolve("gone.pdf").toString());
        when(fanficRepository.findById(ID)).thenReturn(Optional.of(fanfic));

        assertThatThrownBy(() -> service.getPdfPath(ID)).isInstanceOf(PdfNotReadyException.class);
    }

    private void saveAssignsId() {
        when(fanficRepository.save(any())).thenAnswer(call -> {
            Fanfic fanfic = call.getArgument(0);
            fanfic.setId(ID);
            return fanfic;
        });
    }

    private static Fanfic fanfic(FanficStatus status) {
        Fanfic fanfic = new Fanfic();
        fanfic.setId(ID);
        fanfic.setExternalId("123");
        fanfic.setSourceUrl(URL);
        fanfic.setStatus(status);
        return fanfic;
    }

    private static Chapter chapter(String fileName) {
        Chapter chapter = new Chapter();
        chapter.setExtractionFileName(fileName);
        return chapter;
    }
}
