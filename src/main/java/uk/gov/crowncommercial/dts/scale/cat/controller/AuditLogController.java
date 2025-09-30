package uk.gov.crowncommercial.dts.scale.cat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.audit.AuditLog;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.audit.AuditLogDto;
import uk.gov.crowncommercial.dts.scale.cat.service.AuditLogService;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Audit log Controller which provides to add/view audit logs.
 */
@RestController
@RequestMapping(path = "/audit", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class AuditLogController extends AbstractRestController {

    @Autowired
    private AuditLogService auditLogService;

    @GetMapping("/logs")
    public List<AuditLogDto> getAuditLogs(final JwtAuthenticationToken authentication,
                                        @RequestParam(name = "fromDate") String fromDate,
                                        @RequestParam(name = "toDate") String toDate) {
        var principal = getPrincipalFromJwt(authentication);
        log.info("getAuditLogs from Audit logs has been invoked on behalf of principal: {}",
                principal);
        return auditLogService.getAuditLogsWithDate(fromDate, toDate);
    }

    @PostMapping
    public AuditLog createLog(@RequestBody AuditLog auditLog,
                              final JwtAuthenticationToken authentication) {

        var principal = getPrincipalFromJwt(authentication);
        log.info("createLog invoked on behalf of principal: {}", principal);
        return auditLogService.save(auditLog);
    }
}
