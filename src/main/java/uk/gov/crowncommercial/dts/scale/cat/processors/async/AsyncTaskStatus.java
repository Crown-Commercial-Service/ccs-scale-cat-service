package uk.gov.crowncommercial.dts.scale.cat.processors.async;

public enum AsyncTaskStatus {
    SCHEDULED("Scheduled"),IN_FLIGHT("InFlight"), COMPLETED("Completed"), FAILED("Failed"), RETRY("Retry");
    private final String status;

    AsyncTaskStatus(String status){
        this.status = status;
    }
    public String getStatus(){
        return status;
    }
}
