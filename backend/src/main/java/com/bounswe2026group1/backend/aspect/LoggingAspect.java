package com.bounswe2026group1.backend.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class LoggingAspect {
    // Intercept every method in every class inside the controller package
    @Around("execution(* com.bounswe2026group1.backend.controller..*(..))")
    public Object logControllerMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        // Receives human readable name for the method and prints it
        log.info("-> {}", method);
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
