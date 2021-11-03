package uk.gov.crowncommercial.dts.scale.cat;

import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.ProjectEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventType;

@SpringBootApplication
@RequiredArgsConstructor
public class Application {

  public static void main(final String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Bean public ModelMapper modelMapper() {
    ModelMapper modelMapper = new ModelMapper();
    modelMapper.addMappings(new PropertyMap<ProjectEventType, EventType>() {
      @Override protected void configure() {
        map().setPreMarketActivity(source.getPreMarketEvent());
      }
    });
    return modelMapper;
  }
}
