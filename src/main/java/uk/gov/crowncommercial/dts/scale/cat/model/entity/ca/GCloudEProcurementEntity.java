package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "gcloud_e_procurement")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GCloudEProcurementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    Integer id;

    @Column(name = "assessment_id")
    private Integer assessmentId;

    @Column(name = "project_id")
    private Integer projectId;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "summary_of_work")
    private String summaryOfWork;

    @Column(name = "contract_start_date")
    private LocalDate contractStartDate;

    @Column(name = "estimated_contract_value")
    private BigDecimal estimatedContractValue;

    @Column(name = "contract_duration_days")
    private Integer contractDurationDays;

    @Column(name = "contract_duration_months")
    private Integer contractDurationMonths;

    @Column(name = "contract_duration_years")
    private Integer contractDurationYears;

    @Column(name = "incumbent_supplier")
    private String incumbentSupplier;

    @Column(name = "contract_scope")
    private String contractScope;

    @Column(name = "additional_supplier_details")
    private String additionalSupplierDetails;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
