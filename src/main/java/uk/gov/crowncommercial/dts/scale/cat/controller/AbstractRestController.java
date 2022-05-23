package uk.gov.crowncommercial.dts.scale.cat.controller;

import org.apache.commons.io.IOUtils;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.exception.MalformedJwtException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.JWT_CLAIM_SUBJECT;

/**
 * Base class for shared state and behaviour
 */
public abstract class AbstractRestController {

  protected static final String SUPPLIER_DATA = "Supplier_Dimension_Data_%s_%s.csv";
  protected static final String EXPORT_SUPPLIER_DATA = "Supplier_Dimension_Data_%s_%s.zip";
  protected String getPrincipalFromJwt(final JwtAuthenticationToken authentication) {
    return Optional.ofNullable(authentication.getTokenAttributes().get(JWT_CLAIM_SUBJECT))
        .map(Object::toString).orElseThrow(() -> new MalformedJwtException(
            "JWT did not contain mandatory '" + Constants.JWT_CLAIM_SUBJECT + "' claim"));
  }

  protected String getCiiOrgIdFromJwt(final JwtAuthenticationToken authentication) {
    return Optional
        .ofNullable(authentication.getTokenAttributes().get(Constants.JWT_CLAIM_CII_ORG_ID))
        .map(Object::toString).orElseThrow(() -> new MalformedJwtException(
            "JWT did not contain '" + Constants.JWT_CLAIM_CII_ORG_ID + "' claim"));
  }

  protected ZipEntry getZipEntryForSupplierResponse(final byte[] data,
      final ZipOutputStream zipOutputStream, final Integer toolId, final Integer dimensionId) throws IOException {

    ZipEntry zipEntry = new ZipEntry(String.format(SUPPLIER_DATA, toolId, dimensionId));
    zipOutputStream.putNextEntry(zipEntry);
    InputStream is = new ByteArrayInputStream(data);
    IOUtils.copy(is,zipOutputStream);

    return zipEntry;
  }

}
