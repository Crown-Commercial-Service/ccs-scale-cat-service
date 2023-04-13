package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.SchemeType;
import uk.gov.crowncommercial.dts.scale.cat.model.SupplierLink;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.SupplierLinkEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.SupplierLinkRepo;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class TenderDBSupplierLinkService implements SupplierLinkService {
    private final SupplierLinkRepo supplierLinkRepo;

    @Override
    public SupplierLink getByNhs(String identifier) {
        return getModel(supplierLinkRepo.findByNhsNumber(identifier));
    }

    @Override
    public SupplierLink getByVat(String identifier) {
        return getModel(supplierLinkRepo.findByVatNumber(identifier));
    }

    @Override
    public SupplierLink getByDuns(String duns) {
        return getModel(supplierLinkRepo.findByDunsNumber(duns));
    }

    @Override
    public SupplierLink getByCoh(String coh) {
        return getModel(supplierLinkRepo.findByCohNumber(coh));
    }

    @Override
    public List<SupplierLink> getByDunsAndCoh(String duns, String coh) {
        return supplierLinkRepo.findByDunsNumberOrCohNumber(duns, coh)
                .stream().map(this::getModel)
                .collect(Collectors.toList());
    }

    SupplierLink getModel(SupplierLinkEntity entity) {
        if (null == entity)
            return null;
        SupplierLink model = new SupplierLink();
        model.setCasId(SchemeType.of(entity.getCasId()));
        model.setPpgId(SchemeType.of(entity.getPpgId()));

        model.setBravoId(entity.getBravoId());
        model.setCohNumber(entity.getCohNumber());
        model.setNhsNumber(entity.getNhsNumber());
        model.setDunsNumber(entity.getDunsNumber());
        model.setVatNumber(entity.getVatNumber());
        return model;
    }
}
