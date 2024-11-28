package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.*;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.ContactPoint;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Organization;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Provides means for OCDS question and answer data to be converted into various formats
 */
@Component
@RequiredArgsConstructor
@Slf4j
public final class OcdsConverter {
    private final ModelMapper modelMapper;

    /**
     * Converts TemplateCriteria objects into Criterion1 objects
     */
    public Criterion1 convert(TemplateCriteria criteria){
        if (criteria != null) {
            Criterion1 result = modelMapper.map(criteria, Criterion1.class);

            if (result != null && criteria.getRequirementGroups() != null) {
                result.setRequirementGroups(criteria.getRequirementGroups().stream().map(this::convert).sorted(Comparator.comparing(RequirementGroup1::getId)).toList());
            }

            return result;
        }

        return null;
    }

    /**
     * Converts RequirementGroup objects into RequirementGroup1 objects
     */
    private RequirementGroup1 convert(RequirementGroup t) {
        if (t != null && t.getOcds() != null) {
            RequirementGroup.OCDS ocds = t.getOcds();
            RequirementGroup1 result = new RequirementGroup1();

            if (ocds.getId() != null) {
                result.setId(ocds.getId());
            }

            if (ocds.getDescription() != null) {
                result.setDescription(ocds.getDescription());
            }

            if (ocds.getRequirements() != null) {
                result.setRequirements(ocds.getRequirements().stream().map(this::convert).sorted(Comparator.comparing(Requirement1::getId)).toList());
            }

            return result;
        }

        return null;
    }

    /**
     * Converts a Requirement object into a Requirement1 object
     */
    private Requirement1 convert(Requirement r) {
        if (r != null && r.getOcds() != null) {
            Requirement1 result = modelMapper.map(r.getOcds(), Requirement1.class);

            if (result != null) {
                if (r.getOcds().getDescription() != null) {
                    result.setDescription(r.getOcds().getDescription());
                }

                if (r.getNonOCDS() != null && r.getNonOCDS().getOptions() != null && !r.getNonOCDS().getOptions().isEmpty()) {
                    result.setPattern(EventsHelper.serializeValue(r.getNonOCDS().getOptions()));
                }

                return result;
            }
        }

        return null;
    }

    /**
     * Converts a LotSupplier object into an OrganizationReference1 object
     */
    public static OrganizationReference1 convert(LotSupplier lotSupplier) {
        OrganizationReference1 orgRef = new OrganizationReference1();

        if (lotSupplier != null && lotSupplier.getOrganization() != null) {
            if (lotSupplier.getOrganization().getId() != null) {
                orgRef.id(lotSupplier.getOrganization().getId());
            }

            if (lotSupplier.getOrganization().getName() != null) {
                orgRef.setName(lotSupplier.getOrganization().getName());
            }
        }

        return orgRef;
    }

    /**
     * Converts a LotSupplier object into an Organization1 object
     */
    public static Organization1 convertSupplierToOrg(LotSupplier lotSupplier) {
        ModelMapper modelMapper = new ModelMapper();
        Organization1 orgRef = new Organization1();

        if (lotSupplier != null && lotSupplier.getOrganization() != null) {
            Organization org = lotSupplier.getOrganization();

            if (org.getId() != null) {
                orgRef.id(org.getId());
                orgRef.setIdentifier(getId(org.getId()));
            }

            if (org.getName() != null) {
                orgRef.setName(org.getName());
            }

            if (org.getAddress() != null)
                orgRef.setAddress(modelMapper.map(org.getAddress(), Address1.class));
            else {
                log.debug("Organisation {} does not have Address details", org.getId());
            }

            populateContact(lotSupplier, modelMapper, org, orgRef);
        }

        return orgRef;
    }

    /**
     * Populates contact information for a given organisation
     */
    private static void populateContact(LotSupplier lotSupplier, ModelMapper modelMapper, Organization org, Organization1 orgRef) {
        if (lotSupplier != null && lotSupplier.getLotContacts() != null) {
            Optional<Contact> oContact = lotSupplier.getLotContacts().stream().findFirst();

            if (oContact.isPresent() && oContact.get().getContactPoint() != null) {
                ContactPoint contact = oContact.get().getContactPoint();
                orgRef.setContactPoint(modelMapper.map(contact, ContactPoint1.class));

                if (contact.getUrl() != null && org.getId() != null) {
                    String url = contact.getUrl();

                    try {
                        orgRef.getContactPoint().setUrl(new URI(url));
                    } catch (URISyntaxException e) {
                        log.error("Invalid url {} for the supplier {}", url, org.getId());
                    }
                }
            }
        }
    }

    /**
     * Converts a string ID into an Identifier1 object
     */
    public static Identifier1 getId(String id){
        if (id != null) {
            if (id.startsWith(Constants.MAPPERS_ID_DUNS_PREFIX)) {
                int len = id.indexOf('-', 3);
                return new Identifier1().id(id.substring(len + 1)).scheme(OrganizationScheme1.XI_DUNS);
            }

            Optional<OrganizationScheme1> orgScheme = Arrays.stream(OrganizationScheme1.values()).filter(t -> id.startsWith(t.getValue())).findFirst();

            if (orgScheme.isPresent()) {
                int len = orgScheme.get().getValue().length();
                return new Identifier1().id(id.substring(len + 1)).scheme(orgScheme.get());
            }
        }

        return null;
    }
}