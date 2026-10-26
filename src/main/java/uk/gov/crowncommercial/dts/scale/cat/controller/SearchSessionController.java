package uk.gov.crowncommercial.dts.scale.cat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.SearchSessionRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.SearchSessionWrite;
import uk.gov.crowncommercial.dts.scale.cat.service.SearchSessionService;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/tenders/search-sessions")
public class SearchSessionController extends AbstractRestController {

    private final SearchSessionService searchSessionService;

    @PostMapping
    @TrackExecutionTime
    public ResponseEntity<String> saveSearchSession(@RequestBody SearchSessionWrite request,
                                                    final JwtAuthenticationToken authentication) {

        var principal = getPrincipalFromJwt(authentication);
        log.info("saveSearchSession invoked on behalf of principal: {}", principal);


        if (request.getFilters() == null || request.getFilters().trim().isEmpty() ||
                request.getProjectId() == null) {

            log.error("Invalid search session request received. Missing required fields.");
            return ResponseEntity.badRequest().build();
        }

        request.setCreatedBy(principal);
        final String sessionId = searchSessionService.saveSearchSessionFilters(request);
        log.debug("SAS sessionId: {}", sessionId);
        return ResponseEntity.ok(sessionId);
    }

    @GetMapping("/{sessionId}")
    @TrackExecutionTime
    public ResponseEntity<SearchSessionRead> getSearchFilters(@PathVariable String sessionId,
                                                   final JwtAuthenticationToken authentication) {

        var principal = getPrincipalFromJwt(authentication);
        log.info("getSearchSession invoked on behalf of principal: {}", principal);
        return searchSessionService.getSearchSessionFilters(sessionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("SAS search filters not found for the sessionId: {}", sessionId);
                    return ResponseEntity.notFound().build();
                });

    }
}
