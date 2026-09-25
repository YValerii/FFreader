package com.ffreader.api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "chapters", uniqueConstraints = @UniqueConstraint(columnNames = {"fanfic_id", "chapter_number"}))
@Getter
@Setter
@NoArgsConstructor
public class Chapter {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fanfic_id", nullable = false)
    private Fanfic fanfic;

    @Column(name = "chapter_number", nullable = false)
    private Integer chapterNumber;

    private String title;

    /** Raw HTML as extracted from the storytext div */
    @Column(columnDefinition = "TEXT")
    private String rawHtml;

    /** Name of the temporary extraction file: {uuid}_{storyName}_{chapterNumber}.txt */
    private String extractionFileName;
}
