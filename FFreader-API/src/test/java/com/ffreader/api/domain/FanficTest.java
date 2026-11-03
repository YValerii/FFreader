package com.ffreader.api.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FanficTest {

    @Test
    void startsPendingWithACreationTimeAndNoChapters() {
        Fanfic fanfic = new Fanfic();

        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.PENDING);
        assertThat(fanfic.getCreatedAt()).isNotNull();
        assertThat(fanfic.getUpdatedAt()).isNull();
        assertThat(fanfic.getChapters()).isEmpty();
    }

    @Test
    void preUpdateCallbackStampsTheUpdateTime() {
        Fanfic fanfic = new Fanfic();

        fanfic.onUpdate();

        assertThat(fanfic.getUpdatedAt()).isNotNull();
    }

    @Test
    void everyStatusNameFitsTheDatabaseColumn() {
        // fanfics.status is VARCHAR(16) in V1__init.sql.
        for (FanficStatus status : FanficStatus.values()) {
            assertThat(status.name().length()).isLessThanOrEqualTo(16);
        }
    }
}
