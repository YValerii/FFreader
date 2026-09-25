package com.ffreader.api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "fanfics")
@Getter
@Setter
@NoArgsConstructor
public class Fanfic {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 2048)
    private String sourceUrl;

    /** The numeric id fanfiction.net assigns to the story, e.g. 14585136 */
    @Column(nullable = false, unique = true)
    private String externalId;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String rating;
    private String language;
    private String genre;
    private String characters;

    private Integer chaptersCount;
    private Integer words;
    private Integer reviews;
    private Integer favs;
    private Integer follows;

    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FanficStatus status = FanficStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /** Absolute path to the generated PDF once status == PARSED */
    private String pdfPath;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    @OneToMany(mappedBy = "fanfic", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Chapter> chapters = new ArrayList<>();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
