package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Task {
    public static final char COMPLETED = 'C';
    public static final char SCHEDULED = 'S';
    public static final char INFLIGHT = 'I';
    public static final char FAILED = 'F';
    public static final char ABORTED = 'A';
    private final String principal;
    private final String runner;
    private final String className;
    private Object data;


    private Long id;

    public <T> Task(String principal, String springName, String className, T data) {
        this.principal = principal;
        this.runner = springName;
        this.className = className;
        this.data = data;
    }

    void setData(Object data){
        this.data = data;
    }

    void setId (Long id){
        this.id = id;
    }
}
