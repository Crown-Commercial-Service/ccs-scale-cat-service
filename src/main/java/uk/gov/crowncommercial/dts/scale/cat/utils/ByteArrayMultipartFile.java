package uk.gov.crowncommercial.dts.scale.cat.utils;

import java.io.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

/**
 * Simple implementation of a {@link MultipartFile} to wrap byte array contents.
 */
@RequiredArgsConstructor
public class ByteArrayMultipartFile implements MultipartFile {

  private final byte[] bytes;
  private final String name;
  private final String contentType;

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getOriginalFilename() {
    return getName();
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public boolean isEmpty() {
    return bytes == null || bytes.length == 0;
  }

  @Override
  public long getSize() {
    return bytes.length;
  }

  @Override
  public byte[] getBytes() throws IOException {
    return bytes;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(bytes);
  }

  @Override
  public void transferTo(final File dest) throws IOException, IllegalStateException {
    new FileOutputStream(dest).write(bytes);
  }
}
