package uk.gov.crowncommercial.dts.scale.cat.processors;

import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;

public interface DataTemplateProcessor {
    DataTemplate process (DataTemplate template, DataTemplate oldData);
}
