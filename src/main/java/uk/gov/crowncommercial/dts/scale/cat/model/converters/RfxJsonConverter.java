package uk.gov.crowncommercial.dts.scale.cat.model.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Rfx;

/**
 * Converter to transform request payloads for batch jobs between JSON and Rfx objects
 */
@Slf4j
@Converter
public class RfxJsonConverter implements AttributeConverter<Rfx, String> {
    @Override
    public String convertToDatabaseColumn(Rfx data) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(data);
        } catch (Exception ex) {
            log.error("Error converting Rfx to JSON for request batch", ex);
            return null;
        }
    }

    @Override
    public Rfx convertToEntityAttribute(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, Rfx.class);
        } catch (Exception ex) {
            log.error("Error converting JSON to Rfx for request batch", ex);
            return null;
        }
    }
}