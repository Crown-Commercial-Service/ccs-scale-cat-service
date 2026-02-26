package uk.gov.crowncommercial.dts.scale.cat.model;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DigitalRole;

@Data
@Builder
public class DigitalRoleDTO {

  public Long id;

  @NotNull(message = "Job Family can't be null")
  @NotBlank(message = "Job Family can't be empty")
  public String jobFamily;

  @NotNull(message = "Role can't be null")
  @NotBlank(message = "Role can't be empty")
  public String role;

  @NotNull(message = "Level can't be null")
  @NotBlank(message = "Level can't be empty")
  public String level;

  @NotNull(message = "Count can't be null")
  public Integer count;

  @NotNull(message = "Project Id can't be null")
  @NotBlank(message = "Project Id can't be empty")
  public String projectId;

  @NotNull(message = "Event Id can't be null")
  @NotBlank(message = "Event Id can't be empty")
  public String eventId;

  public String createdBy;
  public LocalDateTime createdAt;
  public String updatedBy;
  public LocalDateTime updatedAt;

  public static DigitalRoleDTO toDTO(final DigitalRole entity) {
    return DigitalRoleDTO.builder()
        .id(entity.getId())
        .jobFamily(entity.getJobFamily())
        .role(entity.getRole())
        .level(entity.getLevel())
        .count(entity.getCount())
        .projectId(entity.getProjectId())
        .eventId(entity.getEventId())
        .createdBy(entity.getCreatedBy())
        .createdAt(entity.getCreatedAt())
        .updatedBy(entity.getUpdatedBy())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  public static DigitalRole toEntity(final DigitalRoleDTO dto) {
    return DigitalRole.builder()
        .id(dto.getId())
        .jobFamily(dto.getJobFamily())
        .role(dto.getRole())
        .level(dto.getLevel())
        .count(dto.getCount())
        .projectId(dto.getProjectId())
        .eventId(dto.getEventId())
        .createdBy(dto.getCreatedBy())
        .createdAt(dto.getCreatedAt())
        .updatedBy(dto.getUpdatedBy())
        .updatedAt(dto.getUpdatedAt())
        .build();
  }
}
