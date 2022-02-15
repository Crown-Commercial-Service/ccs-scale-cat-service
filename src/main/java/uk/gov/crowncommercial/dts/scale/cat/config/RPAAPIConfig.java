package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 *
 */
@Configuration
@ConfigurationProperties(prefix = "config.external.jaggaer.rpa", ignoreUnknownFields = true)
@Data
public class RPAAPIConfig {

	private String authenticationUrl;
	private String accessUrl;
	private String userName;
	private String userPwd;
	private String buyerPwd;

}
