package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import jakarta.persistence.Id;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.*;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.ContactPoint;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Organization;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
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
                .stream().map(this::convert)
                .sorted((r, x) -> r.getId().compareTo(x.getId()))
                .toList());
        return result;
    }

    private Requirement1 convert(Requirement r) {
        Requirement1 result = modelMapper.map(r.getOcds(), Requirement1.class);
        result.setDescription(r.getOcds().getDescription());
        if(null!= r.getNonOCDS().getOptions() && r.getNonOCDS().getOptions().size() > 0)
            result.setPattern(EventsHelper.serializeValue(r.getNonOCDS().getOptions()));
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
        orgRef.setIdentifier(getId(org.getId()));

        if(null != org.getAddress())
            orgRef.setAddress(modelMapper.map(org.getAddress(), Address1.class));
        else{
            log.trace("Organisation {} does not have Address details", org.getId());
        }
        if(null != lotSupplier.getLotContacts()) {
            Optional<Contact> oContact = lotSupplier.getLotContacts().stream().findFirst();
            if (oContact.isPresent()) {
                ContactPoint contact = oContact.get().getContactPoint();
                orgRef.setContactPoint(modelMapper.map(contact, ContactPoint1.class));
                String url = contact.getUrl();
                if(null != url) {
                    try {
                        orgRef.getContactPoint().setUrl(new URI(url));
                    } catch (URISyntaxException e) {
                        log.error("Invalid url {} for the supplier {}", url, org.getId());
                    }
                }
            }
        }
        return orgRef;
    }


    public static Identifier1 getId(String id){
        if(id.startsWith("US-DUN")){
            int len = id.indexOf('-', 3);
            return new Identifier1().id(id.substring(len+1)).scheme(OrganizationScheme1.XI_DUNS);
        }

        Optional<OrganizationScheme1> orgScheme = Arrays.stream(OrganizationScheme1.values()).filter(t -> id.startsWith(t.getValue())).findFirst();

        if(orgScheme.isPresent()){
            int len = orgScheme.get().getValue().length();
            return new Identifier1().id(id.substring(len+1)).scheme(orgScheme.get());
        }

        return null;
    }
}
