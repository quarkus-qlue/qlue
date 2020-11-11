package io.quarkus.qlue;

import static io.quarkus.qlue._private.Messages.log;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A chain of steps.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Chain {
    private static final String GRAPH_OUTPUT = System.getProperty("io.quarkus.qlue.graph-output");

    private final Set<ItemId> initialIds;
    private final int initialSingleCount;
    private final int initialMultiCount;
    private final List<StepInfo> startSteps;
    private final Set<ItemId> consumed;
    private final int endStepCount;
    private final ClassLoader classLoader;

    Chain(final ChainBuilder chainBuilder) throws ChainBuildException {
        // copy information from chainBuilder so it can be safely reused
        this.classLoader = chainBuilder.classLoader;
        final Set<StepBuilder> steps = Collections.newSetFromMap(new IdentityHashMap<>(chainBuilder.steps.size()));
        steps.addAll(chainBuilder.steps);
        final Set<ItemId> initialIds = new HashSet<>(chainBuilder.initialIds);
        final Set<ItemId> finalIds = new HashSet<>(chainBuilder.finalIds);
        // compile master produce/consume maps
        final Set<ItemId> consumed = new HashSet<>();
        final Map<StepBuilder, StepInfo> mappedSteps = new HashMap<>();
        int initialSingleCount = 0;
        int initialMultiCount = 0;
        final Map<ItemId, List<Consume>> allConsumes = new HashMap<>();
        final Map<ItemId, List<Produce>> allProduces = new HashMap<>();
        for (StepBuilder stepBuilder : steps) {
            Consumer<StepContext> step = stepBuilder.getStep();
            if (step == null) {
                // skip, per spec
                continue;
            }
            final Map<ItemId, Consume> stepConsumes = stepBuilder.getConsumes();
            for (Map.Entry<ItemId, Consume> entry : stepConsumes.entrySet()) {
                final ItemId id = entry.getKey();
                final List<Consume> list = allConsumes.computeIfAbsent(id, x -> new ArrayList<>(4));
                list.add(entry.getValue());
            }
            final Map<ItemId, Produce> stepProduces = stepBuilder.getProduces();
            for (Map.Entry<ItemId, Produce> entry : stepProduces.entrySet()) {
                final ItemId id = entry.getKey();
                final List<Produce> list = allProduces.computeIfAbsent(id, x -> new ArrayList<>(2));
                final Produce toBeAdded = entry.getValue();
                if (!id.isMulti() && toBeAdded.getConstraint() == Constraint.REAL) {
                    // ensure only one producer
                    if (initialIds.contains(id)) {
                        throw log.cannotProduceInitialResource(id, step);
                    }
                    final boolean overridable = toBeAdded.isOverridable();
                    for (Produce produce : list) {
                        if (produce.getConstraint() == Constraint.REAL && produce.isOverridable() == overridable) {
                            throw log.multipleProducers(id, step);
                        }
                    }
                }
                list.add(toBeAdded);
            }
        }
        final Set<StepBuilder> included = Collections.newSetFromMap(new IdentityHashMap<>());
        // now begin to wire dependencies
        final ArrayDeque<StepBuilder> toAdd = new ArrayDeque<>();
        final Set<Produce> lastDependencies = new HashSet<>();
        for (ItemId finalId : finalIds) {
            addOne(allProduces, included, toAdd, finalId, lastDependencies);
        }
        // now recursively add producers of consumed items
        StepBuilder stepBuilder;
        Map<StepBuilder, Set<Produce>> dependencies = new HashMap<>();
        while ((stepBuilder = toAdd.pollFirst()) != null) {
            for (Map.Entry<ItemId, Consume> entry : stepBuilder.getConsumes().entrySet()) {
                final Consume consume = entry.getValue();
                final ItemId id = entry.getKey();
                if (!consume.getFlags().contains(ConsumeFlag.OPTIONAL) && !id.isMulti()) {
                    if (!initialIds.contains(id) && !allProduces.containsKey(id)) {
                        throw log.noProducers(id);
                    }
                }
                // add every producer
                addOne(allProduces, included, toAdd, id, dependencies.computeIfAbsent(stepBuilder, x -> new HashSet<>()));
            }
        }
        // calculate dependents
        Map<StepBuilder, Set<StepBuilder>> dependents = new HashMap<>();
        for (Map.Entry<StepBuilder, Set<Produce>> entry : dependencies.entrySet()) {
            final StepBuilder dependent = entry.getKey();
            for (Produce produce : entry.getValue()) {
                dependents.computeIfAbsent(produce.getStepBuilder(), x -> new HashSet<>()).add(dependent);
            }
        }
        // detect cycles
        cycleCheck(included, new HashSet<>(), new HashSet<>(), dependencies, new ArrayDeque<>());
        // recursively build all
        final Set<StepInfo> startSteps = new HashSet<>();
        final Set<StepInfo> endSteps = new HashSet<>();
        for (StepBuilder builder : included) {
            buildOne(builder, included, mappedSteps, dependents, dependencies, startSteps, endSteps);
        }
        if (GRAPH_OUTPUT != null && !GRAPH_OUTPUT.isEmpty()) {
            try (FileOutputStream fos = new FileOutputStream(GRAPH_OUTPUT)) {
                try (OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    try (BufferedWriter writer = new BufferedWriter(osw)) {
                        writer.write("digraph {");
                        writer.newLine();
                        writer.write("    node [shape=rectangle];");
                        writer.newLine();
                        writer.write("    rankdir=LR;");
                        writer.newLine();
                        writer.newLine();
                        writer.write("    { rank = same; ");
                        for (StepInfo startStep : startSteps) {
                            writer.write(quoteString(startStep.getStep().toString()));
                            writer.write("; ");
                        }
                        writer.write("};");
                        writer.newLine();
                        writer.write("    { rank = same; ");
                        for (StepInfo endStep : endSteps) {
                            if (!startSteps.contains(endStep)) {
                                writer.write(quoteString(endStep.getStep().toString()));
                                writer.write("; ");
                            }
                        }
                        writer.write("};");
                        writer.newLine();
                        writer.newLine();
                        final HashSet<StepInfo> printed = new HashSet<>();
                        for (StepInfo step : startSteps) {
                            writeStep(writer, printed, step);
                        }
                        writer.write("}");
                        writer.newLine();
                    }
                }
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to write debug graph output", ioe);
            }
        }
        this.initialIds = initialIds;
        this.initialSingleCount = initialSingleCount;
        this.initialMultiCount = initialMultiCount;
        this.startSteps = new ArrayList<>(startSteps);
        this.consumed = consumed;
        this.endStepCount = endSteps.size();
    }

    /**
     * Create a new execution builder for this chain.
     *
     * @return the new execution builder (not {@code null})
     */
    public ExecutionBuilder createExecutionBuilder() {
        return new ExecutionBuilder(this);
    }

    /**
     * Get a new chain builder.
     *
     * @return the chain builder (not {@code null})
     */
    public static ChainBuilder builder() {
        return new ChainBuilder();
    }

    boolean hasInitial(final ItemId itemId) {
        return initialIds.contains(itemId);
    }

    int getInitialSingleCount() {
        return initialSingleCount;
    }

    int getInitialMultiCount() {
        return initialMultiCount;
    }

    List<StepInfo> getStartSteps() {
        return startSteps;
    }

    Set<ItemId> getConsumed() {
        return consumed;
    }

    ClassLoader getClassLoader() {
        return classLoader;
    }

    int getEndStepCount() {
        return endStepCount;
    }

    private static void writeStep(final BufferedWriter writer, final HashSet<StepInfo> printed, final StepInfo step)
            throws IOException {
        if (printed.add(step)) {
            final String currentStepName = quoteString(step.getStep().toString());
            final Set<StepInfo> dependents = step.getDependents();
            if (!dependents.isEmpty()) {
                for (StepInfo dependent : dependents) {
                    final String dependentName = quoteString(dependent.getStep().toString());
                    writer.write("    ");
                    writer.write(dependentName);
                    writer.write(" -> ");
                    writer.write(currentStepName);
                    writer.newLine();
                }
                writer.newLine();
                for (StepInfo dependent : dependents) {
                    writeStep(writer, printed, dependent);
                }
            }
        }
    }

    private static final Pattern QUOTE_PATTERN = Pattern.compile("[\"]");

    private static String quoteString(String input) {
        final Matcher matcher = QUOTE_PATTERN.matcher(input);
        final StringBuffer buf = new StringBuffer();
        buf.append('"');
        while (matcher.find()) {
            matcher.appendReplacement(buf, "\\" + matcher.group(0));
        }
        matcher.appendTail(buf);
        buf.append('"');
        return buf.toString();
    }

    private static void cycleCheck(Set<StepBuilder> builders, Set<StepBuilder> visited, Set<StepBuilder> checked,
            final Map<StepBuilder, Set<Produce>> dependencies, final Deque<Produce> producedPath)
            throws ChainBuildException {
        for (StepBuilder builder : builders) {
            cycleCheck(builder, visited, checked, dependencies, producedPath);
        }
    }

    private static void cycleCheckProduce(Set<Produce> produceSet, Set<StepBuilder> visited, Set<StepBuilder> checked,
            final Map<StepBuilder, Set<Produce>> dependencies, final Deque<Produce> producedPath)
            throws ChainBuildException {
        for (Produce produce : produceSet) {
            producedPath.add(produce);
            cycleCheck(produce.getStepBuilder(), visited, checked, dependencies, producedPath);
            producedPath.removeLast();
        }
    }

    private static void cycleCheck(StepBuilder builder, Set<StepBuilder> visited, Set<StepBuilder> checked,
            final Map<StepBuilder, Set<Produce>> dependencies, final Deque<Produce> producedPath)
            throws ChainBuildException {
        if (!checked.contains(builder)) {
            if (!visited.add(builder)) {
                final StringBuilder b = new StringBuilder("Cycle detected:\n\t\t   ");
                final Iterator<Produce> itr = producedPath.descendingIterator();
                if (itr.hasNext()) {
                    Produce produce = itr.next();
                    for (;;) {
                        b.append(produce.getStepBuilder().getStep());
                        ItemId itemId = produce.getItemId();
                        b.append(" produced ").append(itemId);
                        b.append("\n\t\tto ");
                        if (!itr.hasNext())
                            break;
                        produce = itr.next();
                        if (produce.getStepBuilder() == builder)
                            break;
                    }
                    b.append(builder.getStep());
                }
                throw new ChainBuildException(b.toString());
            }
            try {
                final Set<Produce> dependencySet = dependencies.getOrDefault(builder, Collections.emptySet());
                cycleCheckProduce(dependencySet, visited, checked, dependencies, producedPath);
            } finally {
                visited.remove(builder);
            }
        }
        checked.add(builder);
    }

    private static void addOne(final Map<ItemId, List<Produce>> allProduces, final Set<StepBuilder> included,
            final ArrayDeque<StepBuilder> toAdd, final ItemId idToAdd, Set<Produce> dependencies) {
        boolean modified = false;
        for (Produce produce : allProduces.getOrDefault(idToAdd, Collections.emptyList())) {
            final StepBuilder stepBuilder = produce.getStepBuilder();
            // if overridable, add in second pass only if this pass didn't add any producers
            if (!produce.getFlags().contains(ProduceFlag.OVERRIDABLE)) {
                if (!produce.getFlags().contains(ProduceFlag.WEAK)) {
                    if (included.add(stepBuilder)) {
                        // recursively add
                        toAdd.addLast(stepBuilder);
                    }
                }
                dependencies.add(produce);
                modified = true;
            }
        }
        if (modified) {
            // someone has produced this item non-overridably
            return;
        }
        for (Produce produce : allProduces.getOrDefault(idToAdd, Collections.emptyList())) {
            final StepBuilder stepBuilder = produce.getStepBuilder();
            // if overridable, add in this pass only if the first pass didn't add any producers
            if (produce.getFlags().contains(ProduceFlag.OVERRIDABLE)) {
                if (!produce.getFlags().contains(ProduceFlag.WEAK)) {
                    if (included.add(stepBuilder)) {
                        // recursively add
                        toAdd.addLast(stepBuilder);
                    }
                }
                dependencies.add(produce);
            }
        }
    }

    private static StepInfo buildOne(StepBuilder toBuild, Set<StepBuilder> included, Map<StepBuilder, StepInfo> mapped,
            Map<StepBuilder, Set<StepBuilder>> dependents, Map<StepBuilder, Set<Produce>> dependencies,
            final Set<StepInfo> startSteps, final Set<StepInfo> endSteps) {
        if (mapped.containsKey(toBuild)) {
            return mapped.get(toBuild);
        }
        Set<StepInfo> dependentStepInfos = new HashSet<>();
        final Set<StepBuilder> dependentsOfThis = dependents.getOrDefault(toBuild, Collections.emptySet());
        for (StepBuilder dependentBuilder : dependentsOfThis) {
            if (included.contains(dependentBuilder)) {
                dependentStepInfos
                        .add(buildOne(dependentBuilder, included, mapped, dependents, dependencies, startSteps, endSteps));
            }
        }
        final Set<Produce> dependenciesOfThis = dependencies.getOrDefault(toBuild, Collections.emptySet());
        int includedDependencies = 0;
        final Set<StepBuilder> visited = new HashSet<>();
        for (Produce produce : dependenciesOfThis) {
            final StepBuilder stepBuilder = produce.getStepBuilder();
            if (included.contains(stepBuilder) && visited.add(stepBuilder)) {
                includedDependencies++;
            }
        }
        int includedDependents = 0;
        for (StepBuilder dependent : dependentsOfThis) {
            if (included.contains(dependent)) {
                includedDependents++;
            }
        }
        final StepInfo stepInfo = new StepInfo(toBuild, includedDependencies, dependentStepInfos);
        mapped.put(toBuild, stepInfo);
        if (includedDependencies == 0) {
            // it's a start step!
            startSteps.add(stepInfo);
        }
        if (includedDependents == 0) {
            // it's an end step!
            endSteps.add(stepInfo);
        }
        return stepInfo;
    }

}
