package uk.gov.crowncommercial.dts.scale.cat.config;

import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.spi.MappingContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.OrganizationScheme1;

@Configuration
public class ModelMapperConfig {
    @Bean
    public ModelMapper modelMapper() {
        return configure(new ModelMapper());
    }

    private ModelMapper configure(ModelMapper modelMapper) {
        Converter<String, OrganizationScheme1>  String2OrgSchemeMapper = new Converter<String, OrganizationScheme1>() {
            @Override
            public OrganizationScheme1 convert(MappingContext<String, OrganizationScheme1> mappingContext) {
                String src = mappingContext.getSource();
                if(null == src)
                    return null;
                else
                    return OrganizationScheme1.fromValue(src);
            }
        };

        Converter<OrganizationScheme1, String> OrgSchemeToStringMapper = new Converter<OrganizationScheme1, String>() {
            @Override
            public String convert(MappingContext<OrganizationScheme1, String> mappingContext) {
                OrganizationScheme1 src = mappingContext.getSource();
                if(null == src)
                    return null;
                else
                    return src.getValue();
            }
        };

        modelMapper.addConverter(String2OrgSchemeMapper);
        modelMapper.addConverter(OrgSchemeToStringMapper);

        return modelMapper;
    }
}
