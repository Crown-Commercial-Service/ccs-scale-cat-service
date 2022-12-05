package uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;
import org.springframework.web.bind.annotation.GetMapping;



@AllArgsConstructor
@NoArgsConstructor
public class JaggaerSupplierEventData {
    Integer projectId;
    Integer eventId;
    String eventType;
    Integer existingEventId;
    Boolean twoStageEvent = false;
    Boolean overWrite = false;

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
}
