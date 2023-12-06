package io.quarkus.qlue;

import static io.quarkus.qlue._private.Messages.log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final Map<StepId, StepInfo> stepIndex;
    private final Set<ItemId> consumed;
    private final int endStepCount;
    private final ClassLoader classLoader;

    Chain(final ChainBuilder chainBuilder) throws ChainBuildException {
        // copy information from chainBuilder so it can be safely reused
        this.classLoader = chainBuilder.classLoader;
        final Map<StepId, StepBuilder> stepBuilders = chainBuilder.steps.stream().collect(Collectors.toMap(
                StepBuilder::id,
                Function.identity()));
        final Set<ItemId> initialIds = Set.copyOf(chainBuilder.initialIds);
        final Set<ItemId> finalIds = Set.copyOf(chainBuilder.finalIds);
        // compile master produce/consume maps
        final Set<ItemId> consumed = new HashSet<>();
        final Map<StepBuilder, StepInfo> mappedSteps = new HashMap<>();
        int initialSingleCount = 0;
        int initialMultiCount = 0;
        // compute index of all producers and consumers
        final Map<ItemId, Set<Consume>> allConsumes = stepBuilders.values().stream()
                .flatMap(sb -> sb.getConsumes().values().stream())
                .collect(Collectors.groupingBy(
                        Consume::itemId,
                        Collectors.toUnmodifiableSet()));
        final Map<ItemId, Set<Produce>> allProduces = stepBuilders.values().stream()
                .flatMap(sb -> sb.getProduces().values().stream())
                .collect(Collectors.groupingBy(Produce::itemId, Collectors.toUnmodifiableSet()));
        final Map<ItemId, Set<Produce>> realProducers = allProduces.values().stream()
                .flatMap(Collection::stream)
                .filter(Produce::isReal)
                .collect(Collectors.groupingBy(Produce::itemId, Collectors.toUnmodifiableSet()));

        // validate the producer configs for each step
        for (Map.Entry<ItemId, Set<Produce>> entry : realProducers.entrySet()) {
            ItemId itemId = entry.getKey();
            if (!itemId.isMulti()) {
                // make sure there's just one
                if (entry.getValue().size() > 1) {
                    // special case: one overridable, one not
                    if (entry.getValue().stream().filter(Produce::isOverridable).count() != 1 ||
                            entry.getValue().stream().filter(p -> !p.isOverridable()).count() != 1) {
                        throw log.multipleProducers(itemId, entry.getValue().stream().map(Produce::stepId).toList());
                    }
                }
                // make sure it's not an initial item
                if (initialIds.contains(itemId)) {
                    throw log.cannotProduceInitialResource(itemId, entry.getValue().stream().map(Produce::stepId).toList());
                }
            }
        }
        final Set<StepBuilder> included = Collections.newSetFromMap(new IdentityHashMap<>());
        // now begin to wire dependencies
        final ArrayDeque<StepBuilder> toAdd = new ArrayDeque<>();
        final Set<Produce> lastDependencies = new HashSet<>();
        for (ItemId finalId : finalIds) {
            addOne(allProduces, included, toAdd, finalId, lastDependencies, stepBuilders);
        }
        // now recursively add producers of consumed items
        StepBuilder stepBuilder;
        Map<StepBuilder, Set<Produce>> dependencies = new HashMap<>();
        while ((stepBuilder = toAdd.pollFirst()) != null) {
            for (Map.Entry<ItemId, Consume> entry : stepBuilder.getConsumes().entrySet()) {
                final Consume consume = entry.getValue();
                final ItemId id = entry.getKey();
                if (!consume.flags().contains(ConsumeFlag.OPTIONAL) && !id.isMulti()) {
                    if (!initialIds.contains(id) && !allProduces.containsKey(id)) {
                        throw log.noProducers(id);
                    }
                }
                // add every producer
                addOne(allProduces, included, toAdd, id, dependencies.computeIfAbsent(stepBuilder, Chain::newHashSet),
                        stepBuilders);
            }
        }
        // calculate dependents
        Map<StepBuilder, Set<StepBuilder>> dependents = new HashMap<>();
        for (Map.Entry<StepBuilder, Set<Produce>> entry : dependencies.entrySet()) {
            final StepBuilder dependent = entry.getKey();
            for (Produce produce : entry.getValue()) {
                dependents.computeIfAbsent(stepBuilders.get(produce.stepId()), Chain::newHashSet).add(dependent);
            }
        }
        // detect cycles
        cycleCheck(included, new HashSet<>(), new HashSet<>(), dependencies, new ArrayDeque<>(), stepBuilders);
        // recursively build all
        final Set<StepInfo> startSteps = new HashSet<>();
        final Set<StepInfo> endSteps = new HashSet<>();
        final Map<StepId, StepInfo> stepIndex = new HashMap<>();
        for (StepBuilder builder : included) {
            buildOne(builder, included, mappedSteps, dependents, dependencies, startSteps, endSteps, stepIndex, stepBuilders);
        }
        //        if (GRAPH_OUTPUT != null && !GRAPH_OUTPUT.isEmpty()) {
        //            try (FileOutputStream fos = new FileOutputStream(GRAPH_OUTPUT)) {
        //                try (OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
        //                    try (BufferedWriter writer = new BufferedWriter(osw)) {
        //                        writer.write("digraph {");
        //                        writer.newLine();
        //                        writer.write("    node [shape=rectangle];");
        //                        writer.newLine();
        //                        writer.write("    rankdir=LR;");
        //                        writer.newLine();
        //                        writer.newLine();
        //                        writer.write("    { rank = same; ");
        //                        for (StepInfo startStep : startSteps) {
        //                            writer.write(quoteString(startStep.id().toString()));
        //                            writer.write("; ");
        //                        }
        //                        writer.write("};");
        //                        writer.newLine();
        //                        writer.write("    { rank = same; ");
        //                        for (StepInfo endStep : endSteps) {
        //                            if (!startSteps.contains(endStep)) {
        //                                writer.write(quoteString(endStep.id().toString()));
        //                                writer.write("; ");
        //                            }
        //                        }
        //                        writer.write("};");
        //                        writer.newLine();
        //                        writer.newLine();
        //                        final HashSet<StepInfo> printed = new HashSet<>();
        //                        for (StepInfo step : startSteps) {
        //                            writeStep(writer, printed, step);
        //                        }
        //                        writer.write("}");
        //                        writer.newLine();
        //                    }
        //                }
        //            } catch (IOException ioe) {
        //                throw new RuntimeException("Failed to write debug graph output", ioe);
        //            }
        //        }
        this.initialIds = initialIds;
        this.initialSingleCount = initialSingleCount;
        this.initialMultiCount = initialMultiCount;
        this.stepIndex = Map.copyOf(stepIndex);
        this.startSteps = new ArrayList<>(startSteps);
        this.consumed = consumed;
        this.endStepCount = endSteps.size();
    }

    private static <E> Set<E> newHashSet(Object ignored) {
        return new HashSet<>();
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

    StepInfo stepInfo(StepId stepId) {
        return stepIndex.get(stepId);
    }

    int getEndStepCount() {
        return endStepCount;
    }

    //    private static void writeStep(final BufferedWriter writer, final HashSet<StepInfo> printed, final StepInfo step)
    //            throws IOException {
    //        if (printed.add(step)) {
    //            final String currentStepName = quoteString(step.step().toString());
    //            final Set<StepId> dependents = step.dependents();
    //            if (!dependents.isEmpty()) {
    //                for (StepId id : dependents) {
    //                    final String dependentName = quoteString(id.toString());
    //                    writer.write("    ");
    //                    writer.write(dependentName);
    //                    writer.write(" -> ");
    //                    writer.write(currentStepName);
    //                    writer.newLine();
    //                }
    //                writer.newLine();
    //                for (StepId id : dependents) {
    //                    writeStep(writer, printed, stepInfo(id).toString());
    //                }
    //            }
    //        }
    //    }

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
            final Map<StepBuilder, Set<Produce>> dependencies, final Deque<Produce> producedPath,
            final Map<StepId, StepBuilder> stepBuilders)
            throws ChainBuildException {
        for (StepBuilder builder : builders) {
            cycleCheck(builder, visited, checked, dependencies, producedPath, stepBuilders);
        }
    }

    private static void cycleCheckProduce(Set<Produce> produceSet, Set<StepBuilder> visited, Set<StepBuilder> checked,
            final Map<StepBuilder, Set<Produce>> dependencies, final Deque<Produce> producedPath,
            final Map<StepId, StepBuilder> stepBuilders)
            throws ChainBuildException {
        for (Produce produce : produceSet) {
            producedPath.add(produce);
            cycleCheck(stepBuilders.get(produce.stepId()), visited, checked, dependencies, producedPath, stepBuilders);
            producedPath.removeLast();
        }
    }

    private static void cycleCheck(StepBuilder builder, Set<StepBuilder> visited, Set<StepBuilder> checked,
            final Map<StepBuilder, Set<Produce>> dependencies, final Deque<Produce> producedPath,
            final Map<StepId, StepBuilder> stepBuilders)
            throws ChainBuildException {
        if (!checked.contains(builder)) {
            if (!visited.add(builder)) {
                final StringBuilder b = new StringBuilder("Cycle detected:\n\t\t   ");
                final Iterator<Produce> itr = producedPath.descendingIterator();
                if (itr.hasNext()) {
                    Produce produce = itr.next();
                    for (;;) {
                        b.append(produce.stepId());
                        ItemId itemId = produce.itemId();
                        b.append(" produced ").append(itemId);
                        b.append("\n\t\tto ");
                        if (!itr.hasNext())
                            break;
                        produce = itr.next();
                        if (produce.stepId() == builder.id())
                            break;
                    }
                    b.append(builder.step());
                }
                throw new ChainBuildException(b.toString());
            }
            try {
                final Set<Produce> dependencySet = dependencies.getOrDefault(builder, Collections.emptySet());
                cycleCheckProduce(dependencySet, visited, checked, dependencies, producedPath, stepBuilders);
            } finally {
                visited.remove(builder);
            }
        }
        checked.add(builder);
    }

    private static void addOne(final Map<ItemId, Set<Produce>> allProduces, final Set<StepBuilder> included,
            final ArrayDeque<StepBuilder> toAdd, final ItemId idToAdd, Set<Produce> dependencies,
            final Map<StepId, StepBuilder> stepBuilderIndex) {
        boolean modified = false;
        for (Produce produce : allProduces.getOrDefault(idToAdd, Set.of())) {
            final StepBuilder stepBuilder = stepBuilderIndex.get(produce.stepId());
            // if overridable, add in second pass only if this pass didn't add any producers
            if (!produce.flags().contains(ProduceFlag.OVERRIDABLE)) {
                if (!produce.flags().contains(ProduceFlag.WEAK)) {
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
        for (Produce produce : allProduces.getOrDefault(idToAdd, Set.of())) {
            final StepBuilder stepBuilder = stepBuilderIndex.get(produce.stepId());
            // if overridable, add in this pass only if the first pass didn't add any producers
            if (produce.flags().contains(ProduceFlag.OVERRIDABLE)) {
                if (!produce.flags().contains(ProduceFlag.WEAK)) {
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
            final Set<StepInfo> startSteps, final Set<StepInfo> endSteps, final Map<StepId, StepInfo> stepIndex,
            final Map<StepId, StepBuilder> stepBuilders) {
        if (mapped.containsKey(toBuild)) {
            return mapped.get(toBuild);
        }
        Set<StepId> stepDependents = new HashSet<>();
        final Set<StepBuilder> dependentsOfThis = dependents.getOrDefault(toBuild, Set.of());
        for (StepBuilder dependentBuilder : dependentsOfThis) {
            if (included.contains(dependentBuilder)) {
                StepInfo built = buildOne(dependentBuilder, included, mapped, dependents, dependencies, startSteps, endSteps,
                        stepIndex, stepBuilders);
                stepDependents.add(built.id());
            }
        }
        final Set<Produce> dependenciesOfThis = dependencies.getOrDefault(toBuild, Set.of());
        Set<StepId> stepDependencies = new HashSet<>();
        final Set<StepBuilder> visited = new HashSet<>();
        for (Produce produce : dependenciesOfThis) {
            final StepBuilder stepBuilder = stepBuilders.get(produce.stepId());
            if (included.contains(stepBuilder) && visited.add(stepBuilder)) {
                stepDependencies.add(stepBuilder.id());
            }
        }
        final StepInfo stepInfo = new StepInfo(toBuild, stepDependencies, stepDependents);
        mapped.put(toBuild, stepInfo);
        stepIndex.put(stepInfo.id(), stepInfo);
        if (stepDependencies.isEmpty()) {
            // it's a start step!
            startSteps.add(stepInfo);
        }
        if (stepDependents.isEmpty()) {
            // it's an end step!
            endSteps.add(stepInfo);
        }
        return stepInfo;
    }

}
