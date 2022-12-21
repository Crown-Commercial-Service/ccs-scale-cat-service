package uk.gov.crowncommercial.dts.scale.cat.utils;


import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class UserInputUtils {

    public static Integer sanitiseInputParam(Integer input){

        return Integer.parseInt(Pattern.quote(String.valueOf(input)));
    }

    public static String sanitiseInputParam(String input){

        return Pattern.quote(String.valueOf(input));
    }
}
