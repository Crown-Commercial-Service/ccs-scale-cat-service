package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.concurrent.ConcurrentMap;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;

/**
 * Common interface for all document value adaptor implementations
 */
@FunctionalInterface
public interface DocGenValueAdaptor {

  String getValue(ProcurementEvent event, ConcurrentMap<String, Object> requestCache);

}
