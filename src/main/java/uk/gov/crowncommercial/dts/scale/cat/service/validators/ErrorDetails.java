package uk.gov.crowncommercial.dts.scale.cat.service.validators;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
@Setter
public class ErrorDetails {
    private String errorCode;
    private String message;
}
