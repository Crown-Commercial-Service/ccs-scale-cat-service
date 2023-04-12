package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AwardStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ContractStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MilestoneStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MilestoneType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ReleaseTag;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TenderStatus;

@Value
@Builder
@Jacksonized
public class OcdsTenderStatus {

	TenderStatus tenderStatus;
	AwardStatus awardStatus;
	ContractStatus contractStatus;
	ReleaseTag releaseTag;
	MilestoneStatus milestoneStatus;
	MilestoneType milestoneType;
	
}
