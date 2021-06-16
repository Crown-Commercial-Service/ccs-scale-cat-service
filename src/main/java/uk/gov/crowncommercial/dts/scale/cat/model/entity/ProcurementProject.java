package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.time.Instant;
import java.util.Set;
import javax.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;

/**
 * JPA entity representing a mapping between an internal ID, CA/Lot and Jaggaer internal project
 * code
 */
@Entity
@Table(name = "procurement_projects")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProcurementProject {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "project_id")
  Integer id;

  @OneToMany(fetch = FetchType.EAGER, mappedBy = "project")
  Set<ProcurementEvent> procurementEvents;

  @Column(name = "commercial_agreement_number")
  String caNumber;

  @Column(name = "lot_number")
  String lotNumber;

  @Column(name = "external_project_id")
  String externalProjectId;

  @Column(name = "external_reference_id")
  String externalReferenceId;

  @Column(name = "project_name")
  String projectName;

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_by")
  String updatedBy;

  @Column(name = "updated_at")
  Instant updatedAt;

  /**
   * Builds an instance from basic details
   *
   * @param agreementDetails
   * @param jaggaerProjectId The tender project id
   * @param jaggaerReferenceId The tender reference code
   * @param projectName aka project title
   * @param principal
   * @return a procurement project
   */
  public static ProcurementProject of(AgreementDetails agreementDetails, String externalProjectId,
      String externalReferenceCode, String projectName, String principal) {
    var procurementProject = new ProcurementProject();
    procurementProject.setCaNumber(agreementDetails.getAgreementID());
    procurementProject.setLotNumber(agreementDetails.getLotID());
    procurementProject.setExternalProjectId(externalProjectId);
    procurementProject.setExternalReferenceId(externalReferenceCode);
    procurementProject.setProjectName(projectName);
    procurementProject.setCreatedBy(principal); // Or Jaggaer user ID?
    procurementProject.setCreatedAt(Instant.now());
    procurementProject.setUpdatedBy(principal); // Or Jaggaer user ID?
    procurementProject.setUpdatedAt(Instant.now());
    return procurementProject;
  }

}
