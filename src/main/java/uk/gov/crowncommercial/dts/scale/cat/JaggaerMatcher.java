package uk.gov.crowncommercial.dts.scale.cat;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.JaggaerSupplierModel;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.OrganizationModel;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.SupplierModel;

import java.util.*;

@RequiredArgsConstructor
@Log4j2
public class JaggaerMatcher {
    private final Map<String, JaggaerSupplierModel> jaggaerSupplierMap;
    private String[] common_domains = {"gmail.com", "googlemail.com", "yahoo.com", "live.com", "live.co.uk"
            , "hotmail.com", "outlook.com", "btinternet.com", "yahoo.co.uk", "icloud.com", "ntlworld.com"};
    private Map<String, JaggaerSupplierModel> supplierByDomain = new HashMap<>();
    private Set<String> duplicateMap = new HashSet<>();

    public void init() {
        Set<String> tempMap = new HashSet<>();
        for (JaggaerSupplierModel model : jaggaerSupplierMap.values()) {
            String email = model.getEmail();
            if (null != email) {
                String domain = getDomain(email);
                if (tempMap.contains(domain)) {
                    supplierByDomain.remove(domain);
                    if (!duplicateMap.contains(domain))
                        duplicateMap.add(domain);
                } else {
                    tempMap.add(domain);
                    supplierByDomain.put(domain, model);
                }
            }
        }
    }

    private String getDomain(String email) {
        return email.substring(email.indexOf('@') + 1).trim();
    }

    public JaggaerSupplierModel getMatchingSupplier(SupplierModel supplierModel) {
        String emailAddress = supplierModel.getEmailAddress();
        JaggaerSupplierModel result = null;
        if (null != emailAddress) {
            result = getByEmailDomain(supplierModel);
            if (null != result) {
//                System.out.println("Matched by the domain " + result.getEmail() + " " + result.getSupplierName());
                return result;
            }
        }
        result = matchSupplierByNameAddress(supplierModel);
        if (null != result)
            return result;
        return matchSupplierByName(supplierModel);
    }

    private JaggaerSupplierModel getByEmailDomain(SupplierModel supplierModel) {
        String email = supplierModel.getEmailAddress();
        if (null != email) {
            String domain = getDomain(email);
            JaggaerSupplierModel result = supplierByDomain.get(domain);
            if (null != result) {
                supplierModel.setMapping("Domain", domain);
                supplierModel.setFuzzyMatch("no");
                return result;
            } else {
                for (JaggaerSupplierModel model : jaggaerSupplierMap.values()) {
                    if (isEmailMatch(model, supplierModel)) {
                        supplierModel.setMapping("Email", email);
                        supplierModel.setFuzzyMatch("no");
                        return model;
                    }
                }

            }
        }
        return null;
    }

    private JaggaerSupplierModel matchSupplierByNameAddress(SupplierModel supplierModel) {
        JaggaerSupplierModel curSelected = null;
        if (null == supplierModel.getPostalAddress() || supplierModel.getPostalAddress().length() < 10)
            return null;

        String supplierOrganisation = supplierModel.getSupplierName() + " " + supplierModel.getPostalAddress();
        Double currentSimilarity = 0d;

        for (JaggaerSupplierModel jaggaerSupplierModel : jaggaerSupplierMap.values()) {
            String organisation = jaggaerSupplierModel.getSupplierName() + " " + jaggaerSupplierModel.getPostalAddress();
            Double similarity = Util.getSimilarity(supplierOrganisation, organisation);
            if (similarity > currentSimilarity) {
                currentSimilarity = similarity;
                curSelected = jaggaerSupplierModel;
                if (1d == currentSimilarity) {
                    break;
                }
            }
        }

        if (currentSimilarity > 0.85) {
            if (isDomainMatch(curSelected, supplierModel)) {
                supplierModel.setMapping("jaggaerBravo/Address", supplierOrganisation);
                supplierModel.setFuzzyMatch("yes");
                return curSelected;
            }
        }

        return null;
    }

    private JaggaerSupplierModel matchSupplierByName(SupplierModel supplierModel) {
        JaggaerSupplierModel curSelected = null;
        String supplierOrganisation = supplierModel.getSupplierName();
        Double currentSimilarity = 0d;

        for (JaggaerSupplierModel jaggaerSupplierModel : jaggaerSupplierMap.values()) {
            String organisation = jaggaerSupplierModel.getSupplierName();

            Double similarity = Util.getSimilarity(supplierOrganisation, organisation);
            if (isIntDomainMatch(jaggaerSupplierModel, supplierModel))
                similarity += 0.5d;
            if (similarity > currentSimilarity) {
                curSelected = jaggaerSupplierModel;
                currentSimilarity = similarity;
                if (1d == currentSimilarity) {
                    break;
                }
            }
        }

        if (currentSimilarity > 0.9) {
            if (isDomainMatch(curSelected, supplierModel)) {
                supplierModel.setMapping("jaggaerBravo/Domain", curSelected.getEmail() + "/" + supplierModel.getEmailAddress());
                supplierModel.setFuzzyMatch("yes");
                return curSelected;
            } else if (null ==supplierModel.getEmailAddress() || null == curSelected.getEmail()) {
                supplierModel.setMapping("jaggaerBravo", supplierOrganisation);
                supplierModel.setFuzzyMatch("yes");
                return curSelected;
            }
        }

        return null;
    }

    private boolean isEmailMatch(JaggaerSupplierModel jaggaerSupplierModel, SupplierModel supplierModel) {
        String emailJaggaer = jaggaerSupplierModel.getEmail();
        String emailSupplier = supplierModel.getEmailAddress();
        if (null == emailSupplier || null == emailJaggaer) {
            return false;
        }
        return emailJaggaer.trim().equalsIgnoreCase(emailSupplier.trim());
    }

    private boolean isIntDomainMatch(JaggaerSupplierModel jaggaerSupplierModel, SupplierModel supplierModel) {
        String emailJaggaer = jaggaerSupplierModel.getEmail();
        String emailSupplier = supplierModel.getEmailAddress();

        if (null == emailSupplier || null == emailJaggaer) {
            return false;
        }
        String src = getDomain(emailJaggaer);
        String tgt = getDomain(emailSupplier);
        if (isCommonDomain(src))
            return false;
        if (src.trim().equalsIgnoreCase(tgt.trim())) {
            return true;
        }
        return false;
    }

    private boolean isDomainMatch(JaggaerSupplierModel jaggaerSupplierModel, SupplierModel supplierModel) {
        String emailJaggaer = jaggaerSupplierModel.getEmail();
        String emailSupplier = supplierModel.getEmailAddress();

        if (null == emailSupplier || null == emailJaggaer) {
            return false;
        }
        String src = getDomain(emailJaggaer);
        String tgt = getDomain(emailSupplier);
        if (isCommonDomain(src))
            return false;
        if (src.trim().equalsIgnoreCase(tgt.trim())) {
            return true;
        }
        log.trace("domain not matching for " + supplierModel.getSupplierName() + "/" + jaggaerSupplierModel.getSupplierName() + " jaggaer Email:" + emailJaggaer + " supplier Email:" + emailSupplier);
        return false;
    }

    private boolean isCommonDomain(String src) {
        return Arrays.stream(common_domains).anyMatch((sd) -> {
            return sd.equalsIgnoreCase(src);
        });
    }

    public Map<String, List<JaggaerSupplierModel>> getDuplicateByDomain() {
        Map<String, List<JaggaerSupplierModel>> supplierByDomain = new HashMap<>();
        Map<String, List<JaggaerSupplierModel>> resultMap = new HashMap<>();
        for (JaggaerSupplierModel model : jaggaerSupplierMap.values()) {
            String email = model.getEmail();
            if (null != email) {
                String domain = getDomain(email);
                List<JaggaerSupplierModel> suppliers = supplierByDomain.get(domain);
                if (null == suppliers) {
                    suppliers = new ArrayList<>();
                    supplierByDomain.put(domain, suppliers);
                }
                suppliers.add(model);
            }
        }

        for (Map.Entry<String, List<JaggaerSupplierModel>> suppliers : supplierByDomain.entrySet()) {
            if (suppliers.getValue().size() > 1) {
                resultMap.put(suppliers.getKey(), suppliers.getValue());
            }
        }

        return resultMap;
    }

    public Map<String, List<JaggaerSupplierModel>> getDuplicateByDomain(List<OrganizationModel> supplierList) {

        Set<String> tempMap = new HashSet<>();
        for (OrganizationModel model : supplierList) {
            String email = model.getEmailAddress();
            if (null != email) {
                String domain = getDomain(email);
                if (!tempMap.contains(domain)) {
                    tempMap.add(domain);
                }
            }
        }

        Map<String, List<JaggaerSupplierModel>> supplierByDomain = new HashMap<>();
        Map<String, List<JaggaerSupplierModel>> resultMap = new HashMap<>();
        for (JaggaerSupplierModel model : jaggaerSupplierMap.values()) {
            String email = model.getEmail();
            if (null != email) {
                String domain = getDomain(email);
                if (tempMap.contains(domain)) {
                    List<JaggaerSupplierModel> suppliers = supplierByDomain.get(domain);
                    if (null == suppliers) {
                        suppliers = new ArrayList<>();
                        supplierByDomain.put(domain, suppliers);
                    }
                    suppliers.add(model);
                }
            }
        }

        for (Map.Entry<String, List<JaggaerSupplierModel>> suppliers : supplierByDomain.entrySet()) {
            if (suppliers.getValue().size() > 1) {
                resultMap.put(suppliers.getKey(), suppliers.getValue());
            }
        }

        return resultMap;
    }
}