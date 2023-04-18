package uk.gov.crowncommercial.dts.scale.cat.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.web.multipart.MultipartFile;

import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxTemplateMapping;

public class CSVFileUtils {
	  public static String TYPE = "text/csv";
	  static String[] HEADERs = { "rfx_reference_code", "rfx_short_description", "commercial_agreement_number", "lot_number" };

	  public static boolean hasCSVFormat(MultipartFile file) {

	    if (!TYPE.equals(file.getContentType())) {
	      return false;
	    }

	    return true;
	  }
	  
	  public static List<RfxTemplateMapping> csvToRfxTemplateMappings(InputStream is) {
		    try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
		        
		      CSVParser csvParser = new CSVParser(fileReader,
		            CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build());) {

//		        CSVParser csvParser = new CSVParser(fileReader,
//			            CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim());) {

		    	
		      List<RfxTemplateMapping> rfxTemplateMappings = new ArrayList<RfxTemplateMapping>();

		      Iterable<CSVRecord> csvRecords = csvParser.getRecords();

		      for (CSVRecord csvRecord : csvRecords) {
		    	  RfxTemplateMapping mapping = RfxTemplateMapping.builder()
		    			  .rfxReferenceCode(csvRecord.get("rfx_reference_code"))
		    			  .rfxShortDescription(csvRecord.get("rfx_short_description"))
		    			  .commercialAgreementNumber(csvRecord.get("commercial_agreement_number"))
		    			  .lotNumber(csvRecord.get("lot_number"))
		    			  .build();

		    	  rfxTemplateMappings.add(mapping);
		      }

		      return rfxTemplateMappings;
		    } catch (IOException e) {
		      throw new RuntimeException("fail to parse CSV file: " + e.getMessage());
		    }
		  }

	  
}