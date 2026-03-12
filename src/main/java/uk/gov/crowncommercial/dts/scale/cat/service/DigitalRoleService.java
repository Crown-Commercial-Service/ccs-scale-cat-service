package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DigitalRole;
import uk.gov.crowncommercial.dts.scale.cat.repo.DigitalRoleRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DigitalRoleService {

  private final DigitalRoleRepository digitalRoleRepository;

  @Transactional(readOnly = true)
  public Optional<DigitalRole> findById(final Long id) {
    return digitalRoleRepository.findById(id);
  }

  @Transactional(readOnly = true)
  public List<DigitalRole> findByIdIn(final List<Long> ids) {
    return digitalRoleRepository.findByIdIn(ids);
  }

  @Transactional(readOnly = true)
  public List<DigitalRole> findAllByProjectIdAndEventId(
      final String projectId, final String eventId) {
    return digitalRoleRepository.findAllByProjectIdAndEventIdOrderByJobFamilyAscRoleAscLevelAsc(
        projectId, eventId);
  }

  @Transactional(readOnly = true)
  public List<DigitalRole> findByProjectId(final String projectId) {
    return digitalRoleRepository.findByProjectId(projectId);
  }

  @Transactional
  public DigitalRole save(final DigitalRole entity) {
    return digitalRoleRepository.save(entity);
  }

  @Transactional
  public List<DigitalRole> saveAll(final List<DigitalRole> entities) {
    return digitalRoleRepository.saveAll(entities);
  }

  @Transactional
  public void delete(final DigitalRole entity) {
    digitalRoleRepository.delete(entity);
  }

  @Transactional
  public void deleteAll(final List<DigitalRole> entities) {
    digitalRoleRepository.deleteAllById(entities.stream().map(DigitalRole::getId).toList());
  }
}
