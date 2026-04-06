package com.bounswe2026group1.backend.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    // Controllers whose request bodies contain PII and must not be logged
    private static final Set<String> REDACTED_CONTROLLERS = Set.of("AuthController");

    // Intercept every method in every class inside the controller package
    @Around("execution(* com.bounswe2026group1.backend.controller..*(..))")
    public Object logControllerMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        // Receives human readable name and the arguements and prints it
        String declaringType = joinPoint.getSignature().getDeclaringType().getSimpleName();

        String args = REDACTED_CONTROLLERS.contains(declaringType)
                ? "[redacted]"
                : Arrays.toString(joinPoint.getArgs());

        log.info("-> {} args={}", method, args);
        try {
            // Continue the execution of the real object
            Object result = joinPoint.proceed();
            log.info("<- {} completed", method);
            return result;
        } catch (Exception ex) {
            log.warn("<- {} threw {}: {}", method, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }
}
