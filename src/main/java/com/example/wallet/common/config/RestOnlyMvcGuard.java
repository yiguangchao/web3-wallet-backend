package com.example.wallet.common.config;

import com.example.wallet.common.result.Result;
import java.util.List;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.view.xslt.XsltView;
import org.springframework.web.servlet.view.xslt.XsltViewResolver;

/** Enforces the REST-only assumptions of the temporary Spring MVC CVE triage. */
@Component
public class RestOnlyMvcGuard implements SmartInitializingSingleton {
    private final List<RequestMappingHandlerMapping> mappings;
    private final ListableBeanFactory beans;

    public RestOnlyMvcGuard(List<RequestMappingHandlerMapping> mappings, ListableBeanFactory beans) {
        this.mappings = mappings;
        this.beans = beans;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (beans.getBeanNamesForType(XsltView.class).length != 0
                || beans.getBeanNamesForType(XsltViewResolver.class).length != 0) {
            throw new IllegalStateException("XSLT views require removal of the Spring MVC CVE exception");
        }
        for (var mapping : mappings) {
            mapping.getHandlerMethods().forEach((info, handler) -> {
                if (!handler.getBeanType().getPackageName().startsWith("com.example.wallet.")) {
                    return; // Framework endpoints, including Swagger, are not application views.
                }
                boolean rest = AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), RestController.class);
                boolean sse = info.getProducesCondition().getProducibleMediaTypes().stream()
                        .anyMatch(type -> MediaType.TEXT_EVENT_STREAM.isCompatibleWith(type));
                if (!rest || !returnsResult(handler) || sse) {
                    throw new IllegalStateException("REST-only MVC policy violated by " + handler
                            + "; reassess CVE-2026-47884 and CVE-2026-47890 before enabling views or SSE");
                }
            });
        }
    }

    private boolean returnsResult(HandlerMethod handler) {
        ResolvableType type = ResolvableType.forMethodReturnType(handler.getMethod());
        if (type.resolve() == ResponseEntity.class) {
            type = type.getGeneric(0);
        }
        return type.resolve() == Result.class;
    }
}
