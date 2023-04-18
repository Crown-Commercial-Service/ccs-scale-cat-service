package uk.gov.crowncommercial.dts.scale.cat.service.csvfileutils;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxTemplateMapping;
import uk.gov.crowncommercial.dts.scale.cat.repo.RfxTemplateMappingRepo;
import uk.gov.crowncommercial.dts.scale.cat.utils.CSVFileUtils;

@Service
public class RfxTemplateMappingCSVService {

	  @Autowired
	  RfxTemplateMappingRepo repository;

	  public void save(MultipartFile file) {
	    try {
	      List<RfxTemplateMapping> mappings = CSVFileUtils.csvToRfxTemplateMappings(file.getInputStream());
	      repository.saveAll(mappings);
	    } catch (IOException e) {
	      throw new RuntimeException("fail to store csv data: " + e.getMessage());
	    }
	  }

	  public List<RfxTemplateMapping> getAllTemplateMappings() {
	    return repository.findAll();
	  }
	  
}
