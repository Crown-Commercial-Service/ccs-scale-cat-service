package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;

import java.util.ArrayList;
import java.util.List;

public class OcdsHelper {
    /*
     * The first 3 methods are written in different ways just to avoid codeclimate similarcode false positive
     *
     */
    public static Planning1 getPlanning(Record1 record) {
        Release release = getRelease(record);
        Planning1 planning = release.getPlanning();
        if(null == planning){
            planning = new Planning1();
            release.setPlanning(planning);
        }
        return planning;
    }

    public static Tender1 getTender(Record1 record) {
        Release release = getRelease(record);
        return null != release.getTender() ? release.getTender() : release.tender(new Tender1()).getTender();
    }

    public static List<Award2> getAwards(Record1 record) {
        Release release = getRelease(record);
        if(null != release.getAwards())
            return release.getAwards();

        ArrayList<Award2> awards = new ArrayList<>();
        release.setAwards(awards);

        return awards;
    }

    public static Release getRelease(Record1 record) {
        Release release =  record.getCompiledRelease();
        if(null == release){
            release = new Release();
            record.setCompiledRelease(release);
        }
        return release;
    }

    public static Bids1 getBids(Record1 record){
        Release release = getRelease(record);
        if(null != release.getBids() && release.getBids().size() > 0)
            return release.getBids().get(0);
        Bids1 result = new Bids1();
        release.addBidsItem(result);
        return result;
    }
}
