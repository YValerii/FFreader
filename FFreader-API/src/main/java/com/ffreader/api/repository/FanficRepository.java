package com.ffreader.api.repository;

import com.ffreader.api.domain.Fanfic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FanficRepository extends JpaRepository<Fanfic, UUID> {

    Optional<Fanfic> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);
}
