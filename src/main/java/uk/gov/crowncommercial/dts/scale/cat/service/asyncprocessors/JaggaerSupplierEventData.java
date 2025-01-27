package uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;
import org.springframework.web.bind.annotation.GetMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;

import java.util.List;


@NoArgsConstructor
public class JaggaerSupplierEventData {
    private Integer projectId;
    private Integer eventId;
    private String eventType;
    private Integer existingEventId;
    private Boolean twoStageEvent = false;
    private Boolean overWrite = false;
    @JsonIgnore
    private List<Supplier> suppliers;

    public JaggaerSupplierEventData(Integer projectId, Integer eventId, String eventType, Integer existingEventId, Boolean twoStageEvent, Boolean overWrite) {
        this.projectId = projectId;
        this.eventId = eventId;
        this.eventType = eventType;
        this.existingEventId = existingEventId;
        this.twoStageEvent = twoStageEvent;
        this.overWrite = overWrite;
    }

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    public Integer getEventId() {
        return eventId;
    }

    public void setEventId(Integer eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getExistingEventId() {
        return existingEventId;
    }

    public void setExistingEventId(Integer existingEventId) {
        this.existingEventId = existingEventId;
    }

    public Boolean getTwoStageEvent() {
        return twoStageEvent;
    }

    public void setTwoStageEvent(Boolean twoStageEvent) {
        this.twoStageEvent = twoStageEvent;
    }

    public Boolean getOverWrite() {
        return overWrite;
    }

    public void setOverWrite(Boolean overWrite) {
        this.overWrite = overWrite;
    }

    @JsonIgnore
    public void setSuppliers(List<Supplier> suppliers) {
        this.suppliers = suppliers;
    }

    @JsonIgnore
    public List<Supplier> getSuppliers() {
        return suppliers;
    }
}
