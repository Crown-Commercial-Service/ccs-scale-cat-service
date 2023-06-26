package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefineEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Set;

import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.*;

/**
 * JPA entity representing a mapping between a project event OCID (authority + prefix + internal ID)
 * and Jaggaer internal event code
 */
@Entity
@Table(name = "procurement_events")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(exclude = {"project","capabilityAssessmentSuppliers"})
public class ProcurementEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "event_id")
  Integer id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "project_id")
  ProcurementProject project;

  @OneToMany(mappedBy = "procurementEvent", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
  Set<SupplierSelection> capabilityAssessmentSuppliers;

  @Column(name = "ocds_authority_name")
  String ocdsAuthorityName;

  @Column(name = "ocid_prefix")
  String ocidPrefix;

  @Column(name = "external_event_id")
  String externalEventId;

  @Column(name = "external_reference_id")
  String externalReferenceId;

  @Column(name = "event_name")
  String eventName;

  @Column(name = "event_type")
  String eventType;

  @Column(name = "down_selected_suppliers_ind")
  Boolean downSelectedSuppliers;

  @Column(name = "refresh_suppliers_ind")
  private Boolean refreshSuppliers;

  @Column(name = "assessment_supplier_target")
  Integer assessmentSupplierTarget;

  @Column(name = "assessment_id")
  Integer assessmentId;

  @Column(name = "tender_status")
  String tenderStatus;
  
  @Column(name = "async_published_status")
  String asyncPublishedStatus;

  @Column(name = "publish_date")
  Instant publishDate;

  @Column(name = "close_date")
  Instant closeDate;

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_by")
  String updatedBy;

  @Column(name = "updated_at")
  Instant updatedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "procurement_template_payload")
  String procurementTemplatePayload;

  @Column(name="template_id")
  Integer templateId;

  @Column(name = "procurement_template_payload", insertable = false, updatable = false)
  String procurementTemplatePayloadRaw;

  @Column(name = "supplier_selection_justification")
  String supplierSelectionJustification;

  @ToString.Exclude
  @OneToMany(mappedBy = "procurementEvent", fetch = FetchType.LAZY, cascade = CascadeType.ALL,
      orphanRemoval = true)
  Set<DocumentUpload> documentUploads;

  public String getEventID() {
    return ocdsAuthorityName + "-" + ocidPrefix + "-" + id;
  }

  public DataTemplate getProcurementTemplatePayload() {
    DataTemplate templateModel = null;

    if (procurementTemplatePayload != null) {
      try {
        ObjectMapper objectMapper = new ObjectMapper();
        templateModel = objectMapper.readValue(procurementTemplatePayload, DataTemplate.class);
      }
      catch (Exception ex) {
        log.error("Error converting JSON to DataTemplate", ex);
      }
    }

    return templateModel;
  }

  public String setProcurementTemplatePayload(DataTemplate templateModel) {
    String json = null;

    if (templateModel != null) {
      try {
        ObjectMapper objectMapper = new ObjectMapper();
        json = objectMapper.writeValueAsString(templateModel);
      }
      catch (Exception ex) {
        log.error("Error converting DataTemplate to JSON", ex);
      }
    }

    return json;
  }

  /**
   * Is the event an Assessment Event (e.g. FC, FCA, DAA)?
   *
   * @return true if it is, false otherwise
   */
  public boolean isAssessment() {
    return ASSESSMENT_EVENT_TYPES.stream().map(DefineEventType::name)
        .anyMatch(aet -> aet.equals(getEventType()));
  }

  /**
   * Is the event an Assessment Event (e.g. FC, FCA, DAA)?
   *
   * @return true if it is, false otherwise
   */
  public boolean isDataTemplateEvent() {
    return DATA_TEMPLATE_EVENT_TYPES.stream().map(DefineEventType::name)
        .anyMatch(aet -> aet.equals(getEventType()));
  }

  /**
   * Is the event only persisted in Tenders DB (e.g. FCA, DAA)?
   *
   * @return true if it is, false otherwise
   */
  public boolean isTendersDBOnly() {
    return TENDER_DB_ONLY_EVENT_TYPES.stream().map(ViewEventType::name)
        .anyMatch(aet -> aet.equals(getEventType()));
  }
}
