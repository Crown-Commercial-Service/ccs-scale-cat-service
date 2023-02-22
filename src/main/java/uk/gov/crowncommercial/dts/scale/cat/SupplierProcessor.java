package uk.gov.crowncommercial.dts.scale.cat;

import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.ScriptConfig;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.*;
import uk.gov.crowncommercial.dts.scale.cat.csvwriter.SupplierSuggestionWriter;
import uk.gov.crowncommercial.dts.scale.cat.csvwriter.SupplierWriter;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Consumer;


@RequiredArgsConstructor
public class SupplierProcessor implements Consumer<SupplierModel> {
    private final ScriptConfig scriptConfig;
    private final String agreeementDataFileName;
    private final String businessInputFileName;
    private final Map<String, CiiOrg> ciiOrgMap;
    private final Map<String, CiiSingleOrg> ciiOrgSingleMap;
    private final Map<String, JaggaerSupplierModel> jaggaerSupplierMap;
    private final Map<String, Integer> orgMappings;
    private final Map<String, OrganizationModel> orgMap;

    private final SaveService service;
    private BufferedWriter bw;

    private SupplierWriter mappedWriter;
    private SupplierWriter missingWriter;
    private SupplierWriter missingJaggaerWriter;
    private SupplierWriter missingCASWriter;
    private SupplierSuggestionWriter suggestionsWriter;
    private JaggaerMatcher jaggaerMatcher ;


    public void initWriters() throws IOException {
        SupplierCSVReader reader = new SupplierCSVReader();
        String baseFolder = scriptConfig.getBaseFolder();
        String currentFolder = scriptConfig.getCurrentFolder();

        File agreementDataFile = Paths.get(currentFolder, "input", agreeementDataFileName + ".csv").toFile();

        File businessInputFile = Paths.get(baseFolder, "input", businessInputFileName + ".csv").toFile();

        File errorFile = Paths.get(currentFolder, businessInputFileName + "_error.txt").toFile();
        bw = new BufferedWriter(new FileWriter(errorFile));

        mappedWriter = new SupplierWriter(currentFolder, businessInputFileName + "_completed.csv");
        missingWriter = new SupplierWriter(currentFolder, businessInputFileName + "_missing.csv");
        missingJaggaerWriter = new SupplierWriter(currentFolder, businessInputFileName + "_missing_jaggaer.csv");
        missingCASWriter = new SupplierWriter(currentFolder, businessInputFileName + "_missing_cas.csv");
        suggestionsWriter = new SupplierSuggestionWriter(currentFolder, businessInputFileName + "_suggestions.csv");

        jaggaerMatcher = new JaggaerMatcher(jaggaerSupplierMap);
        jaggaerMatcher.init();
        mappedWriter.initHeader();
        missingWriter.initHeader();
        missingJaggaerWriter.initHeader();
        missingCASWriter.initHeader();
        suggestionsWriter.initHeader();
    }

    public void close() throws IOException {
        bw.close();
        mappedWriter.complete();
        missingCASWriter.complete();
        missingJaggaerWriter.complete();
        missingWriter.complete();
        suggestionsWriter.complete();
    }


    @Override
    public void accept(SupplierModel supplierModel) {

        String duns = supplierModel.getEntityId();
        if (null == duns) {
            try {
                bw.write("Invalid DUNS number for supplier " + supplierModel.getSupplierName());
                bw.newLine();
                bw.flush();
                return;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        duns = Util.getEntityId(duns);

        processCiiMatch(supplierModel);

        if (orgMap.containsKey(duns)) {
            OrganizationModel model = orgMap.get(duns);
            supplierModel.setEmailAddress(model.getEmailAddress());
            supplierModel.setPostalAddress(model.getAddress());
        }

//                if (orgMappings.containsKey("US-DUNS-" + duns) || orgMappings.containsKey("US-DUN-" + duns)
//                        || orgMappings.containsKey("GB-COH-" + duns)) {
//                    supplierModel.setMapping("SYNCED", duns);
//                    if (!orgMap.containsKey(Util.getEntityId(supplierModel.getEntityId())))
//                        missingCASWriter.accept(supplierModel);
//                    else
//                        writer.accept(supplierModel);
//                    return;
//                }

        try {
            JaggaerSupplierModel data = getCompanyData(supplierModel, duns);
            if (null != data && null == supplierModel.getFuzzyMatch()) {
                supplierModel.setJaggaerSupplierName(data.getSupplierName());
                supplierModel.setBravoId(data.getBravoId());
                supplierModel.setSimilarity(getSimilarity(supplierModel));
                String extCode = null == data.getExtCode() ? "" : data.getExtCode();
                String extUniqueCode = null == data.getExtUniqueCode() ? "" : data.getExtUniqueCode();
                supplierModel.setJaggaerExtCode(extCode + "/" + extUniqueCode);

                if (!orgMap.containsKey(Util.getEntityId(supplierModel.getEntityId()))) {
//                    if (null == supplierModel.getFuzzyMatch())
                        missingCASWriter.accept(supplierModel);
//                    else
//                        if(null != supplierModel.getFuzzyMatch())
//                            suggestionsWriter.accept(supplierModel);
                    return;
                } else {
                    OrganizationModel model = orgMap.get(Util.getEntityId(supplierModel.getEntityId()));
                    supplierModel.setTradingName(model.getTradingName());
                    supplierModel.setLegalName(model.getLegalName());
                    if (null != supplierModel.getFuzzyMatch()) {
//                        missingWriter.accept(supplierModel);
//                        suggestionsWriter.accept(supplierModel);
//                        return;
                    }
                }

                String dunsNumber = "US-DUNS-" + duns;
                String dunNumber = "US-DUN-" + duns;
                if (orgMappings.containsKey(dunsNumber) || orgMappings.containsKey(dunNumber)) {
                    mappedWriter.accept(supplierModel);
                    return;
                }

                OrganisationMapping om = service.query(duns);

                if (null == om) {
                    try {
//                        service.save(duns, Integer.parseInt(data.getBravoId()));
                        mappedWriter.accept(supplierModel);
                    } catch (Throwable t) {
                        bw.write(duns + "," +  "," + data.getBravoId() + ","
                                + data.getExtUniqueCode() +"," + data.getExtCode()+ "," + supplierModel.getCiiMatch()
                        + "," + supplierModel.getLegalName());
//                        bw.write("cas error:" + t.getMessage());
//                        bw.newLine();
//                        bw.write(duns + "/" + supplierModel.getSupplierName());
//                        if (null != om)
//                            bw.write(" already assigned to " + om.getExternalOrganisationId() + " and");
//                        bw.write(" new assignment to " + data.getBravoId() + "/" + data.getSupplierName());
                        bw.newLine();
                        bw.flush();
                    }
                } else {
                    if (om.getExternalOrganisationId() != Integer.parseInt(data.getBravoId())) {
                        bw.write(duns + "," + om.getExternalOrganisationId() + "," + data.getBravoId() + "," +
                                data.getExtUniqueCode() +"," + data.getExtCode() + "," + supplierModel.getCiiMatch());

//                        bw.write("cas error:");
//                        bw.write(duns + "/" + supplierModel.getSupplierName());
//                        bw.write(" already assigned to " + om.getExternalOrganisationId());
//                        bw.write(" and cannot be changed to " + data.getBravoId() + "/" + data.getSupplierName());
                        bw.newLine();
                        bw.flush();
                    } else {
                        mappedWriter.accept(supplierModel);
                    }
                }

            } else {
                if (null != supplierModel.getFuzzyMatch()) {
                    if (orgMap.containsKey(Util.getEntityId(supplierModel.getEntityId()))) {
                        OrganizationModel model = orgMap.get(Util.getEntityId(supplierModel.getEntityId()));
                        supplierModel.setTradingName(model.getTradingName());
                        supplierModel.setLegalName(model.getLegalName());
                    }
                    suggestionsWriter.accept(supplierModel);
                } //else
                if (orgMap.containsKey(Util.getEntityId(supplierModel.getEntityId()))) {
                    OrganizationModel model = orgMap.get(Util.getEntityId(supplierModel.getEntityId()));
                    supplierModel.setTradingName(model.getTradingName());
                    supplierModel.setLegalName(model.getLegalName());
                    missingJaggaerWriter.accept(supplierModel);
                } else
                    missingWriter.accept(supplierModel);
            }
        } catch (Throwable e) {
            System.out.println("Error while processing duns number " + duns + " " + e.getMessage());
            try {
                bw.write("cas error:");
                if (null == duns)
                    bw.write("empty duns");
                else
                    bw.write(duns);
                bw.write(e.getMessage());
                bw.newLine();
                bw.flush();
            } catch (IOException ex) {
            }
        }
    }

    private void processCiiMatch(SupplierModel supplierModel) {
        String duns = supplierModel.getEntityId();
        String coh = supplierModel.getHouseNumber();

        {
            CiiOrg org = ciiOrgMap.get(duns);
            if (null != org) {
                supplierModel.setCiiMatch("yes/DUNS");
                supplierModel.setCiiOrgName(org.getOrgName());
                supplierModel.setCiiCoH(org.getCoH());
                return;
            }
        }

        CiiSingleOrg matchedCiiOrg;
        for(CiiSingleOrg ciiOrg : ciiOrgSingleMap.values()){
            String ciiDuns = ciiOrg.getDuns();
            String ciiCoH = ciiOrg.getCoH();
            if(null != ciiDuns && duns.equalsIgnoreCase(Util.getEntityId(ciiDuns))){
                matchedCiiOrg = ciiOrg;
                supplierModel.setCiiMatch("yes/DUNS");
                supplierModel.setCiiOrgName(ciiOrg.getOrgName());
                return;
            }else if(Util.isCohEqual(coh, ciiCoH)){
                matchedCiiOrg = ciiOrg;
                supplierModel.setCiiMatch("yes/CoH");
                supplierModel.setCiiOrgName(ciiOrg.getOrgName());
                return;
            }
        }

        supplierModel.setCiiMatch("NA");
    }

    private JaggaerSupplierModel getCompanyData(SupplierModel supplierModel, String duns) {
        JaggaerSupplierModel data = queryCompanyFromJaggaer(duns);
        String houseNUmber = getHouseNumber(supplierModel.getHouseNumber());
        if(null == data)
           data = queryCompanyJaggaerWithTrim(duns);
        if (null == data && null != supplierModel.getHouseNumber()) {
            if (null != houseNUmber) {
                data = queryCompanyFromJaggaer(houseNUmber);
                if (null == data && houseNUmber.length() == 7) {
                    data = queryCompanyFromJaggaer("0" + houseNUmber);
                }
            }
            if (null == data) {
                String dunsNumber = "US-DUNS-" + duns;
                data = queryCompanyFromJaggaer(dunsNumber);
                if (null != data) {
                    supplierModel.setMapping("DUNS", dunsNumber);
                } else {
                    String coh = getCoH(supplierModel);
                    if (null != coh) {
                        if (!coh.equalsIgnoreCase(houseNUmber)) {
                            data = queryCompanyFromJaggaer(coh);
                            if (null != data) {
                                supplierModel.setMapping("COH", coh);
                            }
                        }
                    }
                }
            } else {
                supplierModel.setMapping("COH", supplierModel.getHouseNumber());
            }
        } else {
            supplierModel.setMapping("DUNS", duns);
        }

        if (null == data) {
            if (null != houseNUmber && houseNUmber.length() < 9) {
                String gbCoh = "GB-COH-";
                if (houseNUmber.length() == 7)
                    gbCoh += "0" + houseNUmber;
                else
                    gbCoh = houseNUmber;

                if (null != data) {
                    supplierModel.setMapping("CoH", gbCoh);
                    return data;
                }
            }
//
//            String dunsNumber = "US-DUN-" + duns;
//            data = queryCompanyByExtUniqueCode(duns);
//            if(null != data){
//                supplierModel.setMapping("DUNS", dunsNumber);
//                return data;
//            }
        }

        if (null == data) {
            data = getByJaggaerSupplier(supplierModel);
//            if (null != data) {
//                supplierModel.setFuzzyMatch("true");
//                supplierModel.setMappingType("JaggaerBravo");
//            }

        }

        return data;
    }

    private JaggaerSupplierModel getByJaggaerSupplier(SupplierModel supplierModel) {
        JaggaerSupplierModel jaggaerSupplierModel = jaggaerMatcher.getMatchingSupplier(supplierModel);
        if (null != jaggaerSupplierModel) {
            if(null == supplierModel.getFuzzyMatch()){
                supplierModel.setFuzzyMatch("yes");
                supplierModel.setMappingType("JaggaerBravo");
            }
            return jaggaerSupplierModel;
        }
        return null;
    }

    private JaggaerSupplierModel queryCompanyFromJaggaer(String duns) {
        for(JaggaerSupplierModel model : jaggaerSupplierMap.values()){
            if(null != model.getExtUniqueCode() && duns.equalsIgnoreCase(model.getExtUniqueCode())){
                return model;
            }
        }
        return null;
    }

    private JaggaerSupplierModel queryCompanyJaggaerWithTrim(String duns) {
        for(JaggaerSupplierModel model : jaggaerSupplierMap.values()){
            if(null != model.getExtUniqueCode()){
                String supplierDuns = model.getExtUniqueCode().replaceAll("-", "");
                supplierDuns = supplierDuns.replaceAll(" ", "");
                if(duns.equalsIgnoreCase(supplierDuns))
                    return model;
            }
        }
        return null;
    }


    private Double getSimilarity(SupplierModel supplierModel) {
        String source = supplierModel.getSupplierName();
        String target = null != supplierModel.getJaggaerSupplierName() ? supplierModel.getJaggaerSupplierName() : supplierModel.getLegalName();
        return Util.getSimilarity(source, target);
    }

    private String getHouseNumber(String houseNumber) {
        if (null == houseNumber)
            return houseNumber;
        String value = houseNumber.trim();
        if (value.contains(",")) {
            String[] split = value.split(",");
            return split[0];
        } else if (value.contains(" ")) {
            String[] split = value.split(" ");
            return split[0];
        }
//        if(value.length() == 7)
//            return "0" + value;
        return value;
    }

    private String getCoH(SupplierModel supplierModel) {

        String houseNumber = supplierModel.getHouseNumber();

        String duns = Util.getEntityId(supplierModel.getEntityId());

        if (ciiOrgMap.containsKey(duns)) {
            CiiOrg org = ciiOrgMap.get(duns);
            String coh = org.getCoH();
            if (null != coh) {
                if (Util.isCohEqual(houseNumber, coh)) return null;
                supplierModel.setCiiCoH(coh);
                supplierModel.setCiiOrgName(org.getOrgName());
                supplierModel.setFuzzyMatch("no");
                return coh;
            }
        }


        String currentMappedOrgName = null;
        String currentMappedCoH = null;
        String target = "";
        Double currentSimilarity = 0d;
        for (CiiSingleOrg org : ciiOrgSingleMap.values()) {
            if(null == org.getCoH())
                continue;
            target = null != supplierModel.getSupplierName() ? supplierModel.getSupplierName() : supplierModel.getLegalName();
            String organisation = org.getOrgName();

            Double similarity = Util.getSimilarity(target, organisation);
            if (similarity > currentSimilarity) {
                currentSimilarity = similarity;
                currentMappedCoH = org.getCoH();
                currentMappedOrgName = organisation;
                if (1d == currentSimilarity) {
                    break;
                }
            }
        }
        if (currentSimilarity > 0.85) {
            if (Util.isCohEqual(houseNumber, currentMappedCoH)) return null;
            supplierModel.setCiiCoH(currentMappedCoH);
            supplierModel.setCiiOrgName(currentMappedOrgName);
            supplierModel.setFuzzyMatch("yes");
            System.out.println("supplier '" + target + "' is matched with " + currentMappedOrgName + " max similarity " + currentSimilarity);
            return currentMappedCoH;
        }
//        if(currentSimilarity != 0d){
//            System.out.println("---------------------------- supplier '" + target + "' is not matched with " + currentMappedOrgName + " max similarity " + currentSimilarity);
//        }
        return null;
    }
}
