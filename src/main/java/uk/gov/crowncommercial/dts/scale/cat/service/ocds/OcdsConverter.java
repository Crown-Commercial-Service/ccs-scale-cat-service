package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.*;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Organization;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;

import java.util.*;

@Component
@RequiredArgsConstructor
public final class OcdsConverter {
    private final ModelMapper modelMapper;

    public Criterion1 convert(TemplateCriteria criteria){
        Criterion1 result = modelMapper.map(criteria, Criterion1.class);
        result.setRequirementGroups(criteria
                .getRequirementGroups().stream()
                .map(t -> convert(t))
                .sorted((r, x) -> r.getId().compareTo(x.getId()))
                .toList());
        return result;
    }

    private RequirementGroup1 convert(RequirementGroup t) {
        RequirementGroup.OCDS ocds = t.getOcds();
        RequirementGroup1 result = new RequirementGroup1(); //modelMapper.map(t.getOcds(), RequirementGroup1.class);
        result.setId(ocds.getId());
        result.setDescription(ocds.getDescription());
        result.setRequirements(ocds.getRequirements()
                .stream().map(r -> convert(r))
                .sorted((r, x) -> r.getId().compareTo(x.getId()))
                .toList());
        return result;
    }

    private Requirement1 convert(Requirement r) {
        Requirement1 result = modelMapper.map(r.getOcds(), Requirement1.class);
        result.setDescription(r.getOcds().getDescription());
        if(null!= r.getNonOCDS().getOptions() && r.getNonOCDS().getOptions().size() > 0)
            result.setPattern(r.getNonOCDS().getOptions().stream().filter(t->t.getSelect()).findFirst(). map(t->null != t? t.getValue() : null).orElseGet(()-> null));
        return result;
    }

    public static OrganizationReference1 convert(LotSupplier lotSupplier) {
        OrganizationReference1 orgRef = new OrganizationReference1();
        orgRef.id(lotSupplier.getOrganization().getId());
        orgRef.setName(lotSupplier.getOrganization().getName());
        return orgRef;
    }

    public static Organization1 convertSupplierToOrg(LotSupplier lotSupplier) {
        ModelMapper modelMapper = new ModelMapper();
        Organization org = lotSupplier.getOrganization();
        Organization1 orgRef = new Organization1();
        orgRef.id(org.getId());
        orgRef.setName(org.getName());
        orgRef.setAddress(modelMapper.map(org.getAddress(), Address1.class));
        Optional<Contact> oContact =  lotSupplier.getLotContacts().stream().findFirst();
        if(oContact.isPresent()){
            orgRef.setContactPoint(modelMapper.map(oContact.get().getContactPoint(), ContactPoint1.class));
        }
        OrganizationDetail od = new OrganizationDetail();

        orgRef.setDetails(od);
        return orgRef;
    }
}
