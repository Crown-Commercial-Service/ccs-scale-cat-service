package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;

import java.util.ArrayList;
import java.util.List;

public class OcdsHelper {
    public static Planning1 getPlanning(Record1 record) {
        Release release = getRelease(record);
        Planning1 planning = release.getPlanning();
        if(null == planning){
            planning = new Planning1();
            release.setPlanning(planning);
        }
        return planning;
    }

    public static Release getRelease(Record1 record) {
        Release release =  record.getCompiledRelease();
        if(null == release){
            release = new Release();
            record.setCompiledRelease(release);
        }
        return release;
    }

    public static Tender1 getTender(Record1 record) {
        Release release = getRelease(record);
        Tender1 releaseTender = release.getTender();
        if(null == releaseTender){
            releaseTender = new Tender1();
            release.setTender(releaseTender);
        }
        return releaseTender;
    }

    public static List<Award2> getAwards(Record1 record) {
        Release release = getRelease(record);
        List<Award2> awards = release.getAwards();
        if(null == awards){
            awards = new ArrayList<>();
            release.setAwards(awards);
        }
        return awards;
    }
}
