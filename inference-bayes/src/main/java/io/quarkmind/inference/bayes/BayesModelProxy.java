package io.quarkmind.inference.bayes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BayesModelProxy implements InvocationHandler {

    private final BayesInstance<?> instance;
    private final Map<String, MethodBinding> bindings;
    private final Map<String, double[]> snapshots;
    private final Set<String> dirtyBits;
    private final double epsilon;
    private boolean needsPropagate;
    private boolean initialized;

    private BayesModelProxy(BayesInstance<?> instance, Map<String, MethodBinding> bindings,
                             double epsilon) {
        this.instance = instance;
        this.bindings = bindings;
        this.snapshots = new HashMap<>();
        this.dirtyBits = new java.util.HashSet<>();
        this.epsilon = epsilon;
        this.needsPropagate = true;
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> modelInterface, BayesInstance<?> instance) {
        return create(modelInterface, instance, 1e-6);
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> modelInterface, BayesInstance<?> instance,
                                double epsilon) {
        Map<String, MethodBinding> bindings = buildBindings(modelInterface, instance);
        BayesModelProxy handler = new BayesModelProxy(instance, bindings, epsilon);
        return (T) Proxy.newProxyInstance(
                modelInterface.getClassLoader(),
                new Class<?>[]{modelInterface},
                handler);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        if ("toString".equals(methodName)) {
            return "BayesModelProxy[" + instance.getVariables().keySet() + "]";
        }
        if ("hashCode".equals(methodName)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(methodName)) {
            return proxy == args[0];
        }

        if ("isDirty".equals(methodName) && args != null && args.length == 1) {
            String varName = (String) args[0];
            ensurePropagated();
            boolean dirty = dirtyBits.remove(varName);
            return dirty;
        }

        if ("update".equals(methodName) && (args == null || args.length == 0)) {
            propagateAndSnapshot();
            return null;
        }

        if ("distribution".equals(methodName) && args != null && args.length == 1) {
            String varName = (String) args[0];
            ensurePropagated();
            return instance.marginalize(varName).getDistribution().clone();
        }

        MethodBinding binding = bindings.get(methodName);
        if (binding == null) {
            throw new UnsupportedOperationException("No binding for method: " + methodName);
        }

        return switch (binding) {
            case GetterBinding g -> {
                ensurePropagated();
                double[] dist = instance.marginalize(g.variableName).getDistribution();
                yield dist[g.outcomeIndex];
            }
            case SetterBinding s -> {
                instance.setHardEvidence(s.variableName, (String) args[0]);
                needsPropagate = true;
                yield null;
            }
        };
    }

    private void ensurePropagated() {
        if (needsPropagate) {
            propagateAndSnapshot();
        }
    }

    private void propagateAndSnapshot() {
        instance.globalUpdate();
        needsPropagate = false;

        dirtyBits.clear();
        for (String varName : instance.getVariables().keySet()) {
            double[] newDist = instance.marginalize(varName).getDistribution().clone();
            double[] oldDist = snapshots.get(varName);

            if (initialized && oldDist != null) {
                for (int i = 0; i < newDist.length; i++) {
                    if (Math.abs(newDist[i] - oldDist[i]) > epsilon) {
                        dirtyBits.add(varName);
                        break;
                    }
                }
            }
            snapshots.put(varName, newDist);
        }
        initialized = true;
    }

    private static Map<String, MethodBinding> buildBindings(Class<?> iface,
                                                             BayesInstance<?> instance) {
        Map<String, MethodBinding> bindings = new HashMap<>();
        Map<String, BayesVariable> variables = instance.getVariables();

        for (Method method : iface.getMethods()) {
            Variable varAnnotation = method.getAnnotation(Variable.class);
            if (varAnnotation != null) {
                String varName = varAnnotation.name().isEmpty()
                        ? method.getName()
                        : varAnnotation.name();
                varName = resolveVariableName(varName, variables);

                BayesVariable var = variables.get(varName);
                int outcomeIndex = varAnnotation.outcomeIndex();
                if (outcomeIndex < 0) {
                    outcomeIndex = var.getOutcomes().length - 1;
                }
                bindings.put(method.getName(), new GetterBinding(varName, outcomeIndex));
                continue;
            }

            Evidence evAnnotation = method.getAnnotation(Evidence.class);
            if (evAnnotation != null) {
                String varName;
                if (!evAnnotation.name().isEmpty()) {
                    varName = evAnnotation.name();
                } else {
                    varName = deriveSetterVariableName(method.getName());
                }
                varName = resolveVariableName(varName, variables);
                bindings.put(method.getName(), new SetterBinding(varName));
            }
        }

        return bindings;
    }

    private static String resolveVariableName(String name, Map<String, BayesVariable> variables) {
        if (variables.containsKey(name)) {
            return name;
        }
        String lower = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        if (variables.containsKey(lower)) {
            return lower;
        }
        String upper = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        if (variables.containsKey(upper)) {
            return upper;
        }
        throw new IllegalArgumentException(
                "Variable '" + name + "' not found in network. Available: " + variables.keySet());
    }

    private static String deriveSetterVariableName(String methodName) {
        if (methodName.startsWith("set") && methodName.length() > 3) {
            return methodName.substring(3);
        }
        return methodName;
    }

    private sealed interface MethodBinding {}
    private record GetterBinding(String variableName, int outcomeIndex) implements MethodBinding {}
    private record SetterBinding(String variableName) implements MethodBinding {}
}
