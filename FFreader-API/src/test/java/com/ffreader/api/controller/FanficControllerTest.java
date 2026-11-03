package com.ffreader.api.controller;

import com.ffreader.api.domain.Fanfic;
import com.ffreader.api.domain.FanficStatus;
import com.ffreader.api.dto.CreateFanficRequest;
import com.ffreader.api.exception.FanficBusyException;
import com.ffreader.api.exception.FanficNotFoundException;
import com.ffreader.api.exception.FanficScrapeException;
import com.ffreader.api.exception.GlobalExceptionHandler;
import com.ffreader.api.exception.PdfNotReadyException;
import com.ffreader.api.service.FanficService;
import com.ffreader.api.service.scraper.FanficUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises routing, validation, JSON and error mapping without a Spring context or a database. */
@ExtendWith(MockitoExtension.class)
class FanficControllerTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String VALID_URL = "https://www.fanfiction.net/s/123/1/Story";

    @Mock FanficService fanficService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FanficController(fanficService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void createReturnsAcceptedWithTheNewFanfic() throws Exception {
        when(fanficService.create(new CreateFanficRequest(VALID_URL))).thenReturn(fanfic(FanficStatus.PENDING));

        mvc.perform(post("/api/fanfics").contentType(MediaType.APPLICATION_JSON).content(urlJson(VALID_URL)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.downloadReady").value(false));
    }

    @Test
    void createRejectsUrlsFromOtherSites() throws Exception {
        mvc.perform(post("/api/fanfics").contentType(MediaType.APPLICATION_JSON)
                        .content(urlJson("https://example.com/s/123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("url: url must be a valid fanfiction.net")))
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(fanficService);
    }

    @Test
    void createRejectsAMissingUrl() throws Exception {
        mvc.perform(post("/api/fanfics").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("url must not be blank")));
    }

    @Test
    void listDefaultsToTenNewestFirst() throws Exception {
        when(fanficService.list(any())).thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(0), 0));

        mvc.perform(get("/api/fanfics")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(fanficService).list(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void listAppliesRequestedPageAndWrapsTheResult() throws Exception {
        Pageable requested = PageRequest.of(1, 2);
        when(fanficService.list(any())).thenReturn(
                new PageImpl<>(List.of(fanfic(FanficStatus.PARSED)), requested, 3));

        mvc.perform(get("/api/fanfics").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void getReturnsTheFanfic() throws Exception {
        when(fanficService.get(ID)).thenReturn(fanfic(FanficStatus.PARSING));

        mvc.perform(get("/api/fanfics/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARSING"));
    }

    @Test
    void getMapsAMissingFanficTo404() throws Exception {
        when(fanficService.get(ID)).thenThrow(new FanficNotFoundException(ID));

        mvc.perform(get("/api/fanfics/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Fanfic not found: " + ID));
    }

    @Test
    void downloadStreamsThePdfAsAnAttachment(@TempDir Path dir) throws Exception {
        Path pdf = pdfFile(dir, "Story.pdf");
        when(fanficService.getPdfPath(ID)).thenReturn(pdf);

        mvc.perform(get("/api/fanfics/{id}/download", ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("Story.pdf")))
                .andExpect(content().bytes(Files.readAllBytes(pdf)));
    }

    @Test
    void downloadEncodesNonAsciiFileNames(@TempDir Path dir) throws Exception {
        when(fanficService.getPdfPath(ID)).thenReturn(pdfFile(dir, "Заголовок.pdf"));

        mvc.perform(get("/api/fanfics/{id}/download", ID))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, not(containsString("Заголовок"))));
    }

    @Test
    void downloadMapsAnUnfinishedPdfTo409() throws Exception {
        when(fanficService.getPdfPath(ID)).thenThrow(new PdfNotReadyException(ID));

        mvc.perform(get("/api/fanfics/{id}/download", ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PDF for fanfic " + ID + " is not ready yet"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mvc.perform(delete("/api/fanfics/{id}", ID)).andExpect(status().isNoContent());

        verify(fanficService).delete(ID);
    }

    @Test
    void deletingAStoryThatIsBeingParsedIs409() throws Exception {
        doThrow(new FanficBusyException(ID)).when(fanficService).delete(ID);

        mvc.perform(delete("/api/fanfics/{id}", ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("still being parsed")));
    }

    @Test
    void scrapeErrorsMapTo502() throws Exception {
        when(fanficService.get(ID)).thenThrow(new FanficScrapeException("blocked"));

        mvc.perform(get("/api/fanfics/{id}", ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("blocked"));
    }

    @Test
    void unexpectedErrorsMapTo500() throws Exception {
        when(fanficService.get(ID)).thenThrow(new IllegalStateException("boom"));

        mvc.perform(get("/api/fanfics/{id}", ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }

    // ---- Client errors keep their proper status and the common error body ----

    @Test
    void malformedIdIsABadRequest() throws Exception {
        mvc.perform(get("/api/fanfics/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not-a-uuid")));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(post("/api/fanfics").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedMethodIs405() throws Exception {
        mvc.perform(put("/api/fanfics"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists(HttpHeaders.ALLOW))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unsupportedContentTypeIs415() throws Exception {
        mvc.perform(post("/api/fanfics").contentType(MediaType.TEXT_PLAIN).content(VALID_URL))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void fullParseQueueIs503() throws Exception {
        when(fanficService.create(any())).thenThrow(new TaskRejectedException("queue full"));

        mvc.perform(post("/api/fanfics").contentType(MediaType.APPLICATION_JSON).content(urlJson(VALID_URL)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Too many stories are queued. Try again later."));
    }

    @Test
    void unexpectedErrorsDoNotLeakInternalDetails() throws Exception {
        when(fanficService.get(ID)).thenThrow(new IllegalStateException("jdbc:postgresql://db/secret failed"));

        mvc.perform(get("/api/fanfics/{id}", ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }

    @Test
    void urlWithAnOverflowingChapterNumberIsABadRequest() throws Exception {
        // Runs the real URL parser, so this covers its exception through to the response.
        when(fanficService.create(any()))
                .thenAnswer(call -> FanficUrl.parse(call.<CreateFanficRequest>getArgument(0).url()));

        mvc.perform(post("/api/fanfics").contentType(MediaType.APPLICATION_JSON)
                        .content(urlJson("https://www.fanfiction.net/s/123/99999999999/Story")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Not a recognizable")));
    }

    @Test
    void unknownRouteIs404() throws Exception {
        mvc.perform(get("/api/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void malformedJsonDoesNotEchoParserDetails() throws Exception {
        mvc.perform(post("/api/fanfics").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(jsonPath("$.message").value("Failed to read request"));
    }

    private static String urlJson(String url) {
        return "{\"url\":\"" + url + "\"}";
    }

    private static Path pdfFile(Path dir, String name) throws IOException {
        return Files.write(dir.resolve(name), new byte[]{'%', 'P', 'D', 'F'});
    }

    private static Fanfic fanfic(FanficStatus status) {
        Fanfic fanfic = new Fanfic();
        fanfic.setId(ID);
        fanfic.setSourceUrl(VALID_URL);
        fanfic.setExternalId("123");
        fanfic.setStatus(status);
        return fanfic;
    }
}
