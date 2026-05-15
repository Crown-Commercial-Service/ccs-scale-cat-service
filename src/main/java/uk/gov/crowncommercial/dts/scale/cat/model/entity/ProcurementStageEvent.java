package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.ASSESSMENT_EVENT_TYPES;
import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.DATA_TEMPLATE_EVENT_TYPES;
import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.TENDER_DB_ONLY_EVENT_TYPES;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefineEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;

/**
 * JPA entity representing a mapping between a project event OCID (authority + prefix + internal ID)
 * and Jaggaer internal event code
 */
@Entity
@Table(name = "procurement_stage_events")
@Data
@Slf4j
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode
@IdClass(ProcurementStageEventId.class)
public class ProcurementStageEvent {
    @Id
    @Column(name = "event_id")
    protected Integer id;

    @Id
    @Column(name = "stage_number")
    Integer stageNumber;

    @Column(name = "stage_description")
    String stageDescription;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id")
    ProcurementProject project;

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

    @Column(name = "assessment_id")
    Integer assessmentId;

    @Column(name = "tender_status")
    String tenderStatus;

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

    @Column(name = "cancellation_reason")
    String cancellationReason;

    @Column(name = "cancellation_reason_detail")
    String cancellationReasonDetail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "procurement_template_payload")
    String procurementTemplatePayload;

    @Column(name="template_id")
    Integer templateId;

    @Column(name = "procurement_template_payload", insertable = false, updatable = false)
    String procurementTemplatePayloadRaw;

    @Column(name = "buyer_exited")
    Boolean buyerExited;

    @Column(name = "supplier_awarded")
    String supplierAwarded;

    @Column(name = "contract_start_date")
    Instant contractStartDate;

    @Column(name = "contract_value")
    String contractValue;

    @Column(name = "award_url")
    String awardUrl;

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public String getOcdsAuthorityName() {
        return ocdsAuthorityName;
    }

    public String getOcidPrefix() {
        return ocidPrefix;
    }

    public String getEventID() {
      return ocdsAuthorityName + "-" + ocidPrefix + "-" + id;
    }

    public void setStageNumber(final Integer stageNumber) {
        this.stageNumber = stageNumber;
    }

    public Integer getStageNumber() {
        return stageNumber;
    }

    public void setStageDescription(final String stageDescription) {
        this.stageDescription = stageDescription;
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

    public void setProcurementTemplatePayload(DataTemplate templateModel) {
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

      procurementTemplatePayload = json;
    }

    public String getThisProcurementTemplatePayload() {
        return procurementTemplatePayload;
    }

    public void setThisProcurementTemplatePayload(final String procurementTemplatePayload) {
        this.procurementTemplatePayload = procurementTemplatePayload;
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
