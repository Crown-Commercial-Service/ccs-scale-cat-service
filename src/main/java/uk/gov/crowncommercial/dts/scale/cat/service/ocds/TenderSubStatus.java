package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import uk.gov.crowncommercial.dts.scale.cat.model.generated.TenderStatus;

public enum TenderSubStatus {
    //Planned Status
    TOBE_PUBLISHED(0),
    TOBE_APPROVED(100),
    APPROVAL_DECLINED(200),
    // Active Status
    RUNNING(300),
    QUALIFICATION_CLARIFICATION(350),
    AUCTION(600),
    BEST_FINAL_OFFER(1300),
    TIOC_PROGRESS(2000),
    TOBE_EVALUATED(400),
    QUALIFICATION_EVALUATION(750),
    TECHNICAL_EVALUATION(800),
    COMMERCIAL_EVALUATION(900),
    BEST_FINAL_OFFER_EVALUATION(1400),
    TIOC_CLOSED(2100),

    //Complete status
    FINAL_EVALUATION(950),
    //Unsuccessful status
    CLOSED(1200),
    //Withdrawn
    SUSPENDED(1100),
    //Cancelled
    ENDED(1500),

    //## AWARD STATUS
    //Pre-Award
    PRE_AWARDED(975),
    AWARD_APPROVAL(985),

    // AWARDED
    AWARDED(500),
    MIXED_AWARDING(1600),
    AWARDED_OFFLINE(50),
    // AWARD CANCELLED
    NOT_AWARDED(700),
    NEW_RFQ_CREATED(1800),
    EVAL_TRANSFERRED_TO_RFQ(1900);
    private int value;
    TenderSubStatus(int value) {
        this.value = value;
    }

    public int getValue(){
        return this.value;
    }
    public static TenderSubStatus fromValue(int value) {
        for (TenderSubStatus b : TenderSubStatus.values()) {
            if (b.value == value) {
                return b;
            }
        }
        return null;
    }
}
