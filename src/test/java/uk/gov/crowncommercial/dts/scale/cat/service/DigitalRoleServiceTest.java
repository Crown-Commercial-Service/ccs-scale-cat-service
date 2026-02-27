package uk.gov.crowncommercial.dts.scale.cat.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DigitalRole;
import uk.gov.crowncommercial.dts.scale.cat.repo.DigitalRoleRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DigitalRoleServiceTest {

  @InjectMocks private DigitalRoleService underTest;
  @Mock private DigitalRoleRepository digitalRoleRepository;

  @Test
  void testValidFindById() {
    // Given
    final Long id = 1L;
    final DigitalRole input =
        DigitalRole.builder()
            .id(id)
            .jobFamily("Architecture roles")
            .role("Business architect")
            .level("Trainee business architect")
            .count(10)
            .projectId("12345")
            .eventId("ocds-pfhb7i-25306")
            .build();
    when(digitalRoleRepository.findById(id)).thenReturn(Optional.of(input));

    // When
    final Optional<DigitalRole> entity = underTest.findById(id);

    // Then
    assertTrue(entity.isPresent());
    assertEquals(input, entity.get());
  }

  @Test
  void testInvalidFindById() {
    // Given
    final Long id = 1L;

    // When
    final Optional<DigitalRole> entity = underTest.findById(id);

    // Then
    assertFalse(entity.isPresent());
  }

  @Test
  void testValidFindAllByProjectIdAndEventId() {
    // Given
    final Long id = 1L;
    final String projectId = "12345";
    final String eventId = "ocds-pfhb7i-25306";
    final DigitalRole input =
        DigitalRole.builder()
            .id(id)
            .jobFamily("Architecture roles")
            .role("Business architect")
            .level("Trainee business architect")
            .count(10)
            .projectId(projectId)
            .eventId(eventId)
            .build();
    when(digitalRoleRepository.findAllByProjectIdAndEventIdOrderByJobFamilyAscRoleAscLevelAsc(
            projectId, eventId))
        .thenReturn(List.of(input));

    // When
    final List<DigitalRole> result = underTest.findAllByProjectIdAndEventId(projectId, eventId);

    // Then
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(List.of(input), result);
  }

  @Test
  void testInvalidFindAllByProjectIdAndEventId() {
    // Given
    final String projectId = "12345";
    final String eventId = "ocds-pfhb7i-25306";

    // When
    final List<DigitalRole> result = underTest.findAllByProjectIdAndEventId(projectId, eventId);

    // Then
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void testValidSave() {
    // Given
    final DigitalRole input =
        DigitalRole.builder()
            .id(1L)
            .jobFamily("Architecture roles")
            .role("Business architect")
            .level("Trainee business architect")
            .count(10)
            .projectId("12345")
            .eventId("ocds-pfhb7i-25306")
            .build();
    when(digitalRoleRepository.save(input)).thenReturn(input);

    // When
    final DigitalRole result = underTest.save(input);

    // Then
    assertNotNull(result);
    assertEquals(input, result);
  }

  @Test
  void testValidSaveAll() {
    // Given
    final List<DigitalRole> input =
        List.of(
            DigitalRole.builder()
                .id(1L)
                .jobFamily("Architecture roles")
                .role("Business architect")
                .level("Trainee business architect")
                .count(10)
                .projectId("12345")
                .eventId("ocds-pfhb7i-25306")
                .build());
    when(digitalRoleRepository.saveAll(input)).thenReturn(input);

    // When
    final List<DigitalRole> result = underTest.saveAll(input);

    // Then
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(input, result);
  }

  @Test
  void testValidDelete() {
    // Given
    final DigitalRole input =
        DigitalRole.builder()
            .id(1L)
            .jobFamily("Architecture roles")
            .role("Business architect")
            .level("Trainee business architect")
            .count(10)
            .projectId("12345")
            .eventId("ocds-pfhb7i-25306")
            .build();
    doNothing().when(digitalRoleRepository).delete(input);

    // When
    underTest.delete(input);

    verify(digitalRoleRepository, times(1)).delete(input);
  }

  @Test
  void testValidDeleteAll() {
    // Given
    final DigitalRole input =
        DigitalRole.builder()
            .id(1L)
            .jobFamily("Architecture roles")
            .role("Business architect")
            .level("Trainee business architect")
            .count(10)
            .projectId("12345")
            .eventId("ocds-pfhb7i-25306")
            .build();
    doNothing().when(digitalRoleRepository).deleteAllById(anyList());

    // When
    underTest.deleteAll(List.of(input));

    verify(digitalRoleRepository, times(1)).deleteAllById(anyList());
  }
}
