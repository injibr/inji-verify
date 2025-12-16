package io.inji.verify.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.inji.verify.config.AuditConfig;
import io.inji.verify.exception.PdfParseException;
import io.inji.verify.services.VcParserService;
import io.inji.verify.services.VpProcessAuditService;
import io.inji.verify.services.VpRequestService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class VpAuditAspect {

    private final VpProcessAuditService auditService;
    private final VpRequestService vpRequestService;
    private final VcParserService vcParserService;
    private final AuditConfig auditConfig;

    public VpAuditAspect(VpProcessAuditService auditService,
                         VpRequestService vpRequestService,
                         VcParserService vcParserService,
                         AuditConfig auditConfig) {
        this.auditService = auditService;
        this.vpRequestService = vpRequestService;
        this.vcParserService = vcParserService;
        this.auditConfig = auditConfig;
    }

    @Around("execution(* io.inji.verify.controller.VPProcessController.submitVP(..))")
    public Object auditVpProcess(ProceedingJoinPoint joinPoint) throws Throwable {

        // 🔥 If audit OFF → skip all logic and run controller normally
        if (!auditConfig.isAuditEnabled()) {
            return joinPoint.proceed();
        }

        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = sig.getParameterNames();    // <- this exists on MethodSignature
        Object[] paramValues = joinPoint.getArgs();

        String vpToken = null;
        String state = null;
        for (int i = 0; i < paramNames.length; i++) {
            if ("vpToken".equals(paramNames[i])) {
                vpToken = (String) paramValues[i];
            } if ("state".equals(paramNames[i])) {
                state = (String) paramValues[i];
            }
        }
        StringBuilder credentialtypes = new StringBuilder();
        int totalVCs = vcParserService.getTotalNumberOfVc(vpToken);
        for (int i = 0; i < totalVCs; i++) {
            try {
                credentialtypes.append(vcParserService.getTypesInVerifiableCredential(vpToken, i)).append(" ");
            } catch (JsonProcessingException ex) {
                log.error("Error while parsing vc", ex);
                throw new PdfParseException();
            }
        }

        String transactionId = vpRequestService.getVpRequestsByRequestId(state).getTransactionId();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            log.error("VP verification failed: {}", e.getMessage());
            log.info("VP Audit created isVcShared=false | transactionId={}", transactionId);
            auditService.logAudit(false, transactionId, credentialtypes.toString());
            throw e;
        }

        if (!(((ResponseEntity<?>) result).getStatusCode().is2xxSuccessful())){
            log.info("VP Audit created  isVcShared=false | transactionId={}", transactionId);
            auditService.logAudit( false, transactionId, credentialtypes.toString());
        }else {
            log.info("VP Audit updated for  isVcShared=true | transactionId={}", transactionId);
            auditService.logAudit(true, transactionId, credentialtypes.toString());
        }
        return result;
    }
}
