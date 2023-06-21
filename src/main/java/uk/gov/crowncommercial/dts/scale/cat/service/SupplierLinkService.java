package uk.gov.crowncommercial.dts.scale.cat.service;

import uk.gov.crowncommercial.dts.scale.cat.model.SchemeType;
import uk.gov.crowncommercial.dts.scale.cat.model.SupplierLink;

import java.util.List;

public interface SupplierLinkService {
    default SupplierLink get(SchemeType type, String identifier){
        if(null != type) {
            switch (type) {
                case DUNS -> {
                    return getByDuns(identifier);
                }
                case COH -> {
                    return getByCoh(identifier);
                }
                case VAT -> {
                    return getByVat(identifier);
                }
                case NHS -> {
                    return getByNhs(identifier);
                }
            }
        }else
            throw new UnsupportedOperationException("SchemeType must be provided and should not be null");
        throw new UnsupportedOperationException("SchemeType " + type.name() + " not supported");
    }

    SupplierLink getByNhs(String identifier);
    SupplierLink getByVat(String identifier);
    SupplierLink getByDuns(String duns);
    SupplierLink getByCoh(String coh);
    List<SupplierLink> getByDunsAndCoh(String duns, String coh);
}


