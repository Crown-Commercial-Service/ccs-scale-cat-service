package uk.gov.crowncommercial.dts.scale.cat.model.assessment;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO to map entity.
 */

@Value
@Getter
@Builder
@Jacksonized
public class GCloudEProcurement {

    public Integer assessmentId;
    public String projectId;
    public String eventId;
    public String projectName;
    public String summaryOfWork;
    public LocalDate contractStartDate;
    public BigDecimal estimatedContractValue;
    public Integer contractDurationDays;
    public Integer contractDurationMonths;
    public Integer contractDurationYears;
    public String incumbentSupplier;
    public String contractScope;
    public String additionalSupplierDetails;
}
