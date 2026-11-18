package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_filter_sessions")
@Data
public class SearchFilterSession {

    @Id
    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "filters", columnDefinition = "TEXT", nullable = false)
    private String filters;

    @Column(name = "project_id", nullable = false)
    private Integer projectId;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "created_by", length = 2000, nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}