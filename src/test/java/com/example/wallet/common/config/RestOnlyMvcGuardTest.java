package com.example.wallet.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.wallet.common.result.Result;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.view.xslt.XsltView;
import org.springframework.web.servlet.view.xslt.XsltViewResolver;

class RestOnlyMvcGuardTest {
    @Test
    void shouldAcceptJsonAndResponseEntityEnvelopes() throws Exception {
        assertThatCode(() -> guard(new JsonController(), "json").afterSingletonsInstantiated()).doesNotThrowAnyException();
        assertThatCode(() -> guard(new JsonController(), "entity").afterSingletonsInstantiated()).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectViewControllersAndStreamingReturnTypes() throws Exception {
        assertThatThrownBy(() -> guard(new ViewController(), "view").afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("REST-only MVC policy");
        assertThatThrownBy(() -> guard(new JsonController(), "stream").afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectSseEvenWithResultReturnType() throws Exception {
        var mapping = new RequestMappingHandlerMapping();
        mapping.registerMapping(RequestMappingInfo.paths("/events").produces("text/event-stream").build(),
                new JsonController(), JsonController.class.getMethod("json"));
        assertThatThrownBy(() -> new RestOnlyMvcGuard(List.of(mapping), new DefaultListableBeanFactory())
                .afterSingletonsInstantiated()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectXsltBeansWithoutAnyHandler() {
        for (Object bean : List.of(new XsltView(), new XsltViewResolver())) {
            var factory = new DefaultListableBeanFactory();
            factory.registerSingleton("xslt", bean);
            assertThatThrownBy(() -> new RestOnlyMvcGuard(List.of(), factory).afterSingletonsInstantiated())
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("XSLT views");
        }
    }

    @Test
    void shouldVerifyEveryCurrentApplicationControllerIncludingInactiveProfiles() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        var environment = new StandardEnvironment();
        environment.setActiveProfiles("dev", "test");
        scanner.setEnvironment(environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        var mapping = new RequestMappingHandlerMapping();
        int handlers = 0;
        for (var candidate : scanner.findCandidateComponents("com.example.wallet.module")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            Object controller = mock(type);
            for (Method method : type.getMethods()) {
                RequestMapping annotation = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (annotation != null) {
                    mapping.registerMapping(RequestMappingInfo.paths("/guard/" + handlers++)
                            .produces(annotation.produces()).build(), controller, method);
                }
            }
        }
        assertThat(handlers).isGreaterThan(20);
        assertThatCode(() -> new RestOnlyMvcGuard(List.of(mapping), new DefaultListableBeanFactory())
                .afterSingletonsInstantiated()).doesNotThrowAnyException();
    }

    private RestOnlyMvcGuard guard(Object controller, String method) throws Exception {
        var mapping = new RequestMappingHandlerMapping();
        mapping.registerMapping(RequestMappingInfo.paths("/test").build(), controller,
                controller.getClass().getMethod(method));
        return new RestOnlyMvcGuard(List.of(mapping), new DefaultListableBeanFactory());
    }

    @RestController
    static class JsonController {
        @GetMapping
        public Result<String> json() { return Result.success("ok"); }
        public ResponseEntity<Result<String>> entity() { return ResponseEntity.ok(json()); }
        public SseEmitter stream() { return new SseEmitter(); }
    }

    @Controller
    static class ViewController {
        public String view() { return "user-controlled-view"; }
    }
}
