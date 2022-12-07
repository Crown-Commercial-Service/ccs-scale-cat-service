package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Task {
    private final String principal;
    private final String runner;
    private final String className;
    private Object data;

    void setData(Object data){
        this.data = data;
    }
}
