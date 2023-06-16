package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

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
    private Instant tobeExecutedAt;
    private Object data;
    private String taskStage;
    private Integer groupId;

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

    @Override
    public int hashCode() {
        if(null != id)
            return id.hashCode();

        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Task){
            Task t = (Task) obj;
            if(null == id)
                return false;
            return Objects.equals(this.id, t.id);
        }
        return super.equals(obj);
    }
}
