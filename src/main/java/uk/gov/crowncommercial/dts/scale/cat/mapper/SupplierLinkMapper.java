package uk.gov.crowncommercial.dts.scale.cat.mapper;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.Util;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.SupplierLink;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.SupplierLinkEntity;

@Component
public class SupplierLinkMapper {
    public SupplierLinkEntity toEntity(SupplierLink model){
        SupplierLinkEntity entity = new SupplierLinkEntity();
        entity.setBravoId(model.getBravoId());
        entity.setCohNumber(model.getCohNumber());
        entity.setDunsNumber(model.getDunsNumber());
        entity.setNhsNumber(model.getNhsNumber());
        entity.setVatNumber(model.getVatNumber());
        entity.setTimestamps(Timestamps.createTimestamps("data_upload"));
        return entity;
    }

    public SupplierLinkEntity getExample(SupplierLink model){
        SupplierLinkEntity entity = new SupplierLinkEntity();
        entity.setBravoId(model.getBravoId());
        entity.setCohNumber(model.getCohNumber());
        entity.setDunsNumber(model.getDunsNumber());
        entity.setNhsNumber(model.getNhsNumber());
        entity.setVatNumber(model.getVatNumber());
        return entity;
    }

    public void formatIds(SupplierLink model){
        model.setCohNumber(Util.getCoHId(format(model.getCohNumber())));
        model.setDunsNumber(Util.getEntityId(format(model.getDunsNumber())));
        model.setNhsNumber(format(model.getNhsNumber()));
        model.setVatNumber(format(model.getVatNumber()));
    }

    private String format(String value){
        if(null == value)
            return null;

        return value.substring(value.lastIndexOf('-')+1, value.length());
    }

    public SupplierLinkEntity toEntity(SupplierLink model, SupplierLinkEntity entity){
        boolean updated = false;
        if(isUpdatable("BravoId", entity.getBravoId(), model.getBravoId())) {
            entity.setBravoId(model.getBravoId());
            updated = true;
        }

        if(isUpdatable("Coh NUmber", entity.getCohNumber(), model.getCohNumber())){
            entity.setCohNumber(model.getCohNumber());
            updated = true;
        }

        if(isUpdatable("Duns Number", entity.getDunsNumber(), model.getDunsNumber())){
            entity.setDunsNumber(model.getDunsNumber());
            updated = true;
        }

        if(isUpdatable("Nhs Number", entity.getNhsNumber(), model.getNhsNumber())){
            entity.setNhsNumber(model.getNhsNumber());
            updated = true;
        }
        if(isUpdatable("Vat Number", entity.getVatNumber(), model.getVatNumber())){
            entity.setVatNumber(model.getVatNumber());
            updated = true;
        }

        if(updated)
            Timestamps.updateTimestamps(entity.getTimestamps(), "data_upload");
        return entity;
    }

    private boolean isUpdatable(String type, Object target, Object source) {
        if(null == target || null == source){
            return (null == target ^ null == source);
        }
        if(target.equals(source)){
            return false;
        }else{
            throw new RuntimeException( type + " "+ target+ " cannot be updated to " + source );
        }
    }
}
