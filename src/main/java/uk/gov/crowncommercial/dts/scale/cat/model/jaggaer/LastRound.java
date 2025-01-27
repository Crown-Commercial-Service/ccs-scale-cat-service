package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
@Builder
@Jacksonized
public class LastRound {
    Integer numSupplInvited;
    Integer numSupplResponded;
    Integer numSupplNotResponded;
    Integer numSupplRespDeclined;
    Integer numSupplRespAccepted;
    Integer numSupplRespExcluded;
    Integer numSupplRespExcludedPreEval;
    Integer numSupplRespExcludedQual;
    Integer numSupplRespExcludedTech;
    Integer numSupplRespExcludedComm;
    Integer numSupplRespExcludedAward;
}
