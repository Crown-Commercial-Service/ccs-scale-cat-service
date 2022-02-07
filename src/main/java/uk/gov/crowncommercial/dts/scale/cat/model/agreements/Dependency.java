package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Value
@Builder
@Jacksonized
@Slf4j
public class Dependency{
	Conditional conditional;
	List<Relationships> relationships;
}