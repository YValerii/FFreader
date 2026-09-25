package com.ffreader.api.repository;

import com.ffreader.api.domain.Chapter;
import com.ffreader.api.domain.Fanfic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    List<Chapter> findByFanficOrderByChapterNumberAsc(Fanfic fanfic);

    List<Chapter> findByFanficIdOrderByChapterNumberAsc(UUID fanficId);
}
