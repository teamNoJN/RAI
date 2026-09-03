package com.rai.chat.repository;

import com.rai.chat.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SourceRepository extends JpaRepository<Source, UUID> {
    List<Source> findByRequestId(String requestId);
}
