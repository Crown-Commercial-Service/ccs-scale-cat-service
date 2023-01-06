package uk.gov.crowncommercial.dts.scale.cat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationConfig;

@Component
@RequiredArgsConstructor
public class EmailTranslator {
    public String translateEmail(String email){
        String modifiedEmail = email.replaceAll("@", "_");
        String result = modifiedEmail + "_castest@yopmail.com";
        return result;
    }

    public String getEmail(String email){
            return translateEmail(email);
    }
}
