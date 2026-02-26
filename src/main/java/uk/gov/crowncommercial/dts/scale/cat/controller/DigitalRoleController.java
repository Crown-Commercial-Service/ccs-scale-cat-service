package uk.gov.crowncommercial.dts.scale.cat.controller;

import java.util.*;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.DigitalRoleDTO;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DigitalRole;
import uk.gov.crowncommercial.dts.scale.cat.service.DigitalRoleService;

@RestController
@RequestMapping(path = "/digitalRole")
@RequiredArgsConstructor
@Slf4j
public class DigitalRoleController extends AbstractRestController {

  private final DigitalRoleService digitalRoleService;

  @GetMapping("/{projectId}/{eventId}")
  @TrackExecutionTime
  public ResponseEntity<List<DigitalRoleDTO>> findAllByProjectIdAndEventId(
      @PathVariable @NonNull final String projectId, @PathVariable @NonNull final String eventId) {
    return ResponseEntity.ok(
        digitalRoleService.findAllByProjectIdAndEventId(projectId, eventId).stream()
            .map(DigitalRoleDTO::toDTO)
            .toList());
  }

  @PostMapping
  @TrackExecutionTime
  public ResponseEntity<DigitalRoleDTO> save(
      @RequestBody @Valid final DigitalRoleDTO dto, final JwtAuthenticationToken authentication) {
    final String user = getPrincipalFromJwt(authentication);
    dto.setCreatedBy(user);
    dto.setUpdatedBy(user);
    final DigitalRole entity = digitalRoleService.save(DigitalRoleDTO.toEntity(dto));
    return ResponseEntity.ok(DigitalRoleDTO.toDTO(entity));
  }

  @PatchMapping
  @TrackExecutionTime
  public ResponseEntity<List<DigitalRoleDTO>> patch(
      @RequestBody @Valid final List<DigitalRoleDTO> digitalRoleDTOs,
      final JwtAuthenticationToken authentication) {
    final String user = getPrincipalFromJwt(authentication);
    final List<DigitalRole> entities =
        digitalRoleDTOs.stream()
            .map(DigitalRoleDTO::toEntity)
            .map(
                entity -> {
                  entity.setUpdatedBy(user);
                  return entity;
                })
            .toList();
    final List<DigitalRole> result = digitalRoleService.saveAll(entities);
    return ResponseEntity.ok(result.stream().map(DigitalRoleDTO::toDTO).toList());
  }

  @DeleteMapping("/{id}")
  @TrackExecutionTime
  public ResponseEntity<Void> delete(@PathVariable @NonNull final Long id) {
    final DigitalRole entity =
        digitalRoleService.findById(id).orElseThrow(EntityNotFoundException::new);
    digitalRoleService.delete(entity);
    return ResponseEntity.noContent().build();
  }
}
