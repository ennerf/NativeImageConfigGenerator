package us.hebi.graalvm;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import us.hebi.graalvm.schema.ReflectConfig;
import us.hebi.quickbuf.JsonSink;
import us.hebi.quickbuf.RepeatedMessage;

import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;

/**
 * A simple annotation processor that creates GraalVM native-image
 * configuration files for annotated fields and methods.
 *
 * @author Florian Enner
 * @since 25 May 2023
 */
@AutoService(javax.annotation.processing.Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class NativeImageAnnotationProcessor extends BasicAnnotationProcessor {

    @Override
    protected Iterable<? extends Step> steps() {
        return ImmutableSet.of(new DependencyInjectionConfigProcessor());
    }

    class DependencyInjectionConfigProcessor implements Step {

        private static final String DI_CONFIG_FILE = "META-INF/native-image/annotations-di/reflect-config.json";

        @Override
        public Set<String> annotations() {
            return Set.of(
                    "jakarta.inject.Inject",
                    "jakarta.annotation.PostConstruct",
                    "jakarta.annotation.PreDestroy",
                    "javax.inject.Inject",
                    "javax.annotation.PreDestroy",
                    "javax.annotation.PostConstruct",
                    "javafx.fxml.FXML"
            );
        }

        @Override
        public Set<? extends Element> process(ImmutableSetMultimap<String, Element> elementMap) {

            // Protobuf messages that conform to the GraalVM spec when written as JSON
            RepeatedMessage<ReflectConfig> reflectConfig = RepeatedMessage.newEmptyInstance(ReflectConfig.getFactory());
            HashMap<String, ReflectConfig> classMap = new HashMap<>();
            Function<Element, ReflectConfig> getClassConfig = classElement -> classMap.computeIfAbsent(classElement.toString(), name -> reflectConfig.next().setName(name));

            // Add all annotated elements to the config
            elementMap.values().forEach(element -> {
                switch (element.getKind()) {
                    case INTERFACE, ENUM, CLASS, RECORD -> {
                        getClassConfig.apply(element);
                    }
                    case FIELD -> {
                        getClassConfig.apply(element.getEnclosingElement())
                                .getMutableFields().next().setName(element.getSimpleName());
                    }
                    case CONSTRUCTOR, METHOD -> {
                        var params = getClassConfig.apply(element.getEnclosingElement())
                                .getMutableMethods().next().setName(element.getSimpleName())
                                .getMutableParameterTypes();
                        for (VariableElement param : ((ExecutableElement) element).getParameters()) {
                            params.add(param.asType().toString());
                        }
                    }
                }
            });

            // Write JSON content
            try {
                var target = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", DI_CONFIG_FILE);
                try (Writer out = target.openWriter()) {
                    out.write(JsonSink.newPrettyInstance()
                            .writeRepeatedMessageSilent(reflectConfig)
                            .toString());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return Collections.emptySet();
        }

    }

}