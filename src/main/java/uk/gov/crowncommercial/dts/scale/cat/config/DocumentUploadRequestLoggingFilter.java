package uk.gov.crowncommercial.dts.scale.cat.config;

import java.io.IOException;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class DocumentUploadRequestLoggingFilter extends OncePerRequestFilter {

  private static final Pattern DOCUMENT_UPLOAD_PATH =
      Pattern.compile(".*/tenders/projects/[^/]+/events/[^/]+/documents");

  @Override
  protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
      final FilterChain filterChain) throws ServletException, IOException {

    if (!isDocumentUploadRequest(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    log.info(
        "Document file upload request reached Tenders API. method={}, uri={}, contentType={}, contentLength={}, authorizationHeader={}",
        request.getMethod(), request.getRequestURI(), request.getContentType(), request.getContentLengthLong(),
        describeAuthorizationHeader(request.getHeader(HttpHeaders.AUTHORIZATION)));

    try {
      filterChain.doFilter(request, response);
    } finally {
      log.info("Document upload request completed in Tenders API. method={}, uri={}, status={}",
          request.getMethod(), request.getRequestURI(), response.getStatus());
    }
  }

  private boolean isDocumentUploadRequest(final HttpServletRequest request) {
    return HttpMethod.PUT.matches(request.getMethod())
        && DOCUMENT_UPLOAD_PATH.matcher(request.getRequestURI()).matches();
  }

  private String describeAuthorizationHeader(final String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.isBlank()) {
      return "missing";
    }

    return "present(length=" + authorizationHeader.length() + ")";
  }
}
