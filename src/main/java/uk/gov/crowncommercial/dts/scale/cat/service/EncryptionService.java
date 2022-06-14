package uk.gov.crowncommercial.dts.scale.cat.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.RPAAPIConfig;

@Service
@RequiredArgsConstructor
@Slf4j
public class EncryptionService {

  private final RPAAPIConfig rpaAPIConfig;

  public String generateBuyerPassword() {
    // TODO Password pattern should change after decision
    return encryptPassword(RandomStringUtils.randomAlphanumeric(8));
  }

  @SneakyThrows
  public String encryptPassword(String password) {
    String encryptString = Base64.getEncoder().encodeToString(
        createCipher(Cipher.ENCRYPT_MODE).doFinal(password.getBytes(StandardCharsets.US_ASCII)));
    log.debug("Encrypted key: {} ", encryptString);
    return encryptString;
  }

  @SneakyThrows
  public String decryptPassword(String encryptedPassword) {
    String decyString = new String(
        createCipher(Cipher.DECRYPT_MODE).doFinal(Base64.getDecoder().decode(encryptedPassword)));
    log.debug("Decrypted key: {} ", decyString);
    return decyString;
  }

  @SneakyThrows
  private Cipher createCipher(int mode) {
    byte[] iv = rpaAPIConfig.getEncryptionIv().getBytes(StandardCharsets.US_ASCII);
    IvParameterSpec ivspec = new IvParameterSpec(iv);
    SecretKeySpec secretKey = new SecretKeySpec(
        rpaAPIConfig.getEncryptionKey().getBytes(StandardCharsets.US_ASCII), "AES");
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(mode, secretKey, ivspec);
    return cipher;
  }
}
