package org.sheinbergon.needle.shielding;

import lombok.val;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.utility.JavaModule;
import org.apache.commons.lang3.ObjectUtils;
import org.sheinbergon.needle.Pinned;
import org.sheinbergon.needle.shielding.util.AffinityGroups;
import org.sheinbergon.needle.shielding.util.YamlCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Map;
import java.util.function.Supplier;

import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public final class ShieldingAgent {

    private ShieldingAgent() {
    }

    /**
     * @param arguments
     * @param instrumentation
     */
    public static void premain(
            final String arguments,
            final Instrumentation instrumentation) throws Exception {
        val storage = Files.createTempDirectory("shielding-agent-instrumentation").toFile();
        setupBootstrapInjection(storage, instrumentation);
        agentConfiguration(arguments);
        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .ignore(nameStartsWith("net.bytebuddy."))
                .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                .with(AgentBuilder.RedefinitionStrategy.REDEFINITION)
                .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .with(new Listener())
                .with(new AgentBuilder.InjectionStrategy.UsingInstrumentation(instrumentation, storage))
                .type(isSubTypeOf(Thread.class).or(is(Thread.class)))
                .and(not(isSubTypeOf(Pinned.class)))
                .transform(ShieldingAgent::premainTransform)
                .installOn(instrumentation);
    }

    /**
     * @param arguments
     * @param instrumentation
     */
    public static void agentmain(
            final String arguments,
            final Instrumentation instrumentation) throws Exception {
        agentConfiguration(arguments);
        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .ignore(nameStartsWith("net.bytebuddy."))
                .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                .with(AgentBuilder.RedefinitionStrategy.REDEFINITION)
                .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .type(isSubTypeOf(Thread.class).or(is(Thread.class)))
                .and(not(isSubTypeOf(Pinned.class)))
                .transform(agentmainTransform())
                .installOn(instrumentation);
    }

    private static DynamicType.Builder<?> premainTransform(
            final @Nonnull DynamicType.Builder<?> builder,
            final @Nonnull TypeDescription typeDescription,
            final @Nullable ClassLoader classLoader,
            final @Nonnull JavaModule module) {
        return builder.visit(Advice.to(ShieldingAdvice.class).on(named("run")));
    }

    private static AgentBuilder.Transformer agentmainTransform() {
        return new AgentBuilder.Transformer.ForAdvice()
                .include(ShieldingAgent.class.getClassLoader())
                .advice(named("run"), ShieldingAdvice.class.getName());
    }

    private static void setupBootstrapInjection(
            final @Nonnull File storage,
            final @Nonnull Instrumentation instrumentation) {
        ClassInjector.UsingInstrumentation
                .of(storage, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, instrumentation)
                .inject(Map.of(
                        new TypeDescription.ForLoadedType(ShieldingAdvice.class),
                        ClassFileLocator.ForClassLoader.read(ShieldingAdvice.class)));
    }

    private static void agentConfiguration(final @Nullable String arguments) throws MalformedURLException {
        Supplier<ShieldingConfiguration> supplier;
        if (arguments != null) {
            val url = ObjectUtils.firstNonNull(ShieldingAgent.class.getResource(arguments), new URL(arguments));
            supplier = () -> YamlCodec.parseConfiguration(url);
        } else {
            supplier = () -> ShieldingConfiguration.DEFAULT;
        }
        AffinityGroups.setConfigurationSupplier(supplier);
    }

    private static class Listener implements AgentBuilder.Listener {

        @Override
        public void onTransformation(final TypeDescription typeDescription,
                                     final ClassLoader classLoader,
                                     final JavaModule module,
                                     final boolean loaded,
                                     final DynamicType dynamicType) {
            System.out.printf("Transformed - %s, %s, %s, %s, %s%n",
                    typeDescription, classLoader, module, loaded, dynamicType);
        }

        @Override
        public void onError(final String typeName,
                            final ClassLoader classLoader,
                            final JavaModule module,
                            final boolean loaded,
                            final Throwable throwable) {
            System.out.printf("Error - %s, %s, %s, %s, %s%n",
                    typeName, classLoader, module, loaded, throwable.getMessage());
        }

        @Override
        public void onDiscovery(final String typeName,
                                final ClassLoader classLoader,
                                final JavaModule module,
                                final boolean loaded) {
        }

        @Override
        public void onIgnored(final TypeDescription typeDescription,
                              final ClassLoader classLoader,
                              final JavaModule module,
                              final boolean loaded) {
        }

        @Override
        public void onComplete(final String typeName,
                               final ClassLoader classLoader,
                               final JavaModule module,
                               final boolean loaded) {
        }
    }
}