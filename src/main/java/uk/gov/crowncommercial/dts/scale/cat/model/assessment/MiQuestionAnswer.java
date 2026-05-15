package uk.gov.crowncommercial.dts.scale.cat.model.assessment;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * DTO to map entity.
 */

@Value
@Getter
@Builder
@Jacksonized
public class MiQuestionAnswer {

    public Integer assessmentId;
    public String projectId;
    public String eventId;
    public int questionId;
    public String questionAnswer;
    public String createdBy;
    public OffsetDateTime createdAt;
}
