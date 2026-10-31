package com.pennypincher.backend.trace;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Profile("trace")
public class TraceAspect {

	private static final Logger log = LoggerFactory.getLogger(TraceAspect.class);

	private static final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

	@Around("within(@org.springframework.web.bind.annotation.RestController *)"
			+ " || within(@org.springframework.stereotype.Service *)"
			+ " || execution(* org.springframework.data.repository.Repository+.*(..))")
	public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
		int level = depth.get();
		String indent = "  ".repeat(level);
		String method = joinPoint.getSignature().toShortString();
		log.info("{}-> {}", indent, method);
		long start = System.nanoTime();
		depth.set(level + 1);
		try {
			return joinPoint.proceed();
		}
		finally {
			log.info("{}<- {} ({} ms)", indent, method, (System.nanoTime() - start) / 1_000_000);
			if (level == 0) {
				depth.remove();
			}
			else {
				depth.set(level);
			}
		}
	}

}
