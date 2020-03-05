package tsml.classifiers.distance_based.knn.strategies;

import evaluation.storage.ClassifierResults;
import java.util.function.Consumer;
import tsml.classifiers.distance_based.tuned.*;
import tsml.classifiers.EnhancedAbstractClassifier;
import tsml.classifiers.distance_based.knn.KNNLOOCV;
import tsml.classifiers.distance_based.utils.stopwatch.StopWatch;
import utilities.*;
import tsml.classifiers.distance_based.utils.collections.PrunedMultimap;
import tsml.classifiers.distance_based.utils.collections.Utils;
import tsml.classifiers.distance_based.utils.collections.box.Box;
import tsml.classifiers.distance_based.utils.iteration.LinearListIterator;
import tsml.classifiers.distance_based.utils.iteration.RandomListIterator;
import tsml.classifiers.distance_based.utils.params.ParamSet;
import tsml.classifiers.distance_based.utils.params.ParamSpace;
import weka.core.Instances;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static tsml.classifiers.distance_based.utils.collections.Utils.replace;

public class RLTunedKNNSetup implements Consumer<Instances>, Serializable {


    private RLTunedClassifier rlTunedClassifier = new RLTunedClassifier();
    private ParamSpace paramSpace;
    private Agent agent = null;
    private Iterator<ParamSet> paramSetIterator;
    // setup building classifier from param set
    private int neighbourhoodSizeLimit = -1;
    private int paramSpaceSizeLimit = -1;
    private int maxParamSpaceSize = -1; // max number of params
    private int maxNeighbourhoodSize = -1; // max number of neighbours
    private Box<Integer> neighbourCount; // current number of neighbours
    private Box<Integer> paramCount; // current number of params
    private long longestExploreTimeNanos; // track maximum time taken for a param to run
    private long longestExploitTimeNanos; // track max time taken for an addition of
    // neighbours
    private boolean incrementalMode = true;
    private Function<Instances, ParamSpace> paramSpaceFunction;
    private Iterator<EnhancedAbstractClassifier> explorer;
    private Iterator<EnhancedAbstractClassifier> exploiter;
    private Optimiser optimiser;
    private Supplier<KNNLOOCV> knnSupplier;
    private double neighbourhoodSizeLimitPercentage = -1;
    private double paramSpaceSizeLimitPercentage = -1;
    private int fullParamSpaceSize = -1;
    private int fullNeighbourhoodSize = -1;
    private Set<EnhancedAbstractClassifier> nextImproveableBenchmarks;
    private Set<EnhancedAbstractClassifier> improveableBenchmarks;
    private Set<EnhancedAbstractClassifier> unimprovableBenchmarks;
    private Iterator<EnhancedAbstractClassifier> improveableBenchmarkIterator;
    private boolean trainSelectedBenchmarksFully = false; // whether to train the final benchmarks up to full neighbourhood or leave as is
    private PrunedMultimap<Double, EnhancedAbstractClassifier> finalBenchmarks;
    private boolean explore;
    private int id = 0;
    private Function<List<EnhancedAbstractClassifier>, Iterator<EnhancedAbstractClassifier>> improveableBenchmarkIteratorBuilder = new Function<List<EnhancedAbstractClassifier>, Iterator<EnhancedAbstractClassifier>>() {
        @Override
        public Iterator<EnhancedAbstractClassifier> apply(List<EnhancedAbstractClassifier> benchmarks) {
            RandomListIterator<EnhancedAbstractClassifier> iterator = new RandomListIterator<>(rlTunedClassifier.getSeed(), new ArrayList<>(improveableBenchmarks));
            iterator.setRemovedOnNext(false);
            return iterator;
        }
    };
    private StopWatch decisionTimer = new StopWatch();
    private Iterator<EnhancedAbstractClassifier> fullyTrainedIterator = null;

    private class FullTrainer extends LinearListIterator<EnhancedAbstractClassifier> {

        public FullTrainer(List<EnhancedAbstractClassifier> list) {
            super(list);
        }

        @Override
        public EnhancedAbstractClassifier next() {
            EnhancedAbstractClassifier classifier = super.next();
            if(classifier instanceof KNNLOOCV) {
                final KNNLOOCV knn = (KNNLOOCV) classifier;
                rlTunedClassifier.getLogger().info(() -> "enabling full train for " + classifier.toString() + " " +
                    classifier.getParams().toString());
                knn.setNeighbourLimit(-1);
                return knn;
            } else {
                throw new IllegalArgumentException("expected knn");
            }
        }
    }

    public boolean isIncrementalMode() {
        return incrementalMode;
    }

    public void setIncrementalMode(boolean incrementalMode) {
        this.incrementalMode = incrementalMode;
    }

    private class KnnAgent implements Agent {

        private boolean hasNextCalled = false;
        private boolean hasNext = false;

        @Override
        public long predictNextTimeNanos() {
            hasNext();
            if(explore) {
                return longestExploreTimeNanos;
            } else {
                return longestExploitTimeNanos;
            }
        }

        @Override
        public Set<EnhancedAbstractClassifier> findFinalClassifiers() {
            // randomly pick 1 of the best classifiers
            final Collection<EnhancedAbstractClassifier> benchmarks = finalBenchmarks.values();
            final List<EnhancedAbstractClassifier> selectedBenchmarks = Utilities.randPickN(benchmarks, 1,
                                                                                            rlTunedClassifier.getRandom());
            if(selectedBenchmarks.size() > 1) {
                throw new IllegalStateException("there shouldn't be more than 1");
            }
            return new HashSet<>(selectedBenchmarks);
        }

        @Override
        public boolean feedback(EnhancedAbstractClassifier classifier) {
            finalBenchmarks.put(scorer.findScore(classifier), classifier); // add the benchmark back to the final benchmarks under the new score (which may be worse, hence why we have to remove the original benchmark first
            rlTunedClassifier.getLogger().info(() -> "score of " + scorer.findScore(classifier) + " for " + classifier.getClassifierName() + " " + classifier.getParams().toString());
            boolean result;
            if(!isImproveable(classifier)) {
                rlTunedClassifier
                    .getLogger().info(() -> "unimproveable classifier " + classifier.getClassifierName() + " " + classifier.getParams().toString());
                Utils.put(classifier, unimprovableBenchmarks);
                result = false;
            } else {
                rlTunedClassifier
                    .getLogger().info(() -> "improveable classifier " + classifier.getClassifierName() + " " + classifier.getParams().toString());
                Utils.put(classifier, nextImproveableBenchmarks);
                long time = classifier.getTrainResults().getBuildPlusEstimateTime();
                if(explore) {
                    longestExploreTimeNanos = Math.max(time + decisionTimer.getTimeNanos(), longestExploreTimeNanos);
                    rlTunedClassifier.getLogger().info(() -> "longest explore time: " + longestExploreTimeNanos);
                } else {
                    longestExploitTimeNanos = Math.max(time + decisionTimer.getTimeNanos(), longestExploitTimeNanos);
                    rlTunedClassifier.getLogger().info(() -> "longest improvement time: " + longestExploitTimeNanos);
                }
                decisionTimer.resetAndDisable();
                result = true;
            }
            return result;
        }

        @Override
        public EnhancedAbstractClassifier next() {
            decisionTimer.enable();
            if(!hasNext) {
                throw new IllegalStateException("oops this shouldn't happen");
            }
            hasNextCalled = false;
            EnhancedAbstractClassifier result;
            if(fullyTrainedIterator != null) {
                result = fullyTrainedIterator.next();
            } else if(explore) {
                result = explorer.next();
            } else {
                result = exploiter.next();
            }
            decisionTimer.disable();
            return result;
        }

        @Override
        public boolean hasNext() {
            if(!hasNextCalled) {
                decisionTimer.enable();
                hasNextCalled = true;
                boolean source = hasNextSourceTime() && hasNextSource();
                boolean improve = hasNextImprovementTime() && hasNextImprovement();
                explore = false; // improve && !source
                boolean result = true;
                if(!improve && source) {
                    explore = true;
                } else if(improve && source) {
                    explore = optimiser.shouldSource();
                } else {
                    if (trainSelectedBenchmarksFully) {
                        if(fullyTrainedIterator == null) {
                            rlTunedClassifier.getLogger().info("limited version, therefore training selected benchmark fully");
                            fullyTrainedIterator = new FullTrainer(new ArrayList<>(findFinalClassifiers()));
                            finalBenchmarks.clear();
                        }
                        result = fullyTrainedIterator.hasNext();
                    } else {
                        result = false;
                    }
                }
                hasNext = result;
                decisionTimer.disable();
            }
            return hasNext;
        }

        @Override
        public boolean isExploringOrExploiting() {
            return explore;
        }
    }

    public interface Optimiser {
        boolean shouldSource();
    }

    public Scorer getScorer() {
        return scorer;
    }

    public void setScorer(Scorer scorer) {
        this.scorer = scorer;
    }

    public boolean isTrainSelectedBenchmarksFully() {
        return trainSelectedBenchmarksFully;
    }

    public void setTrainSelectedBenchmarksFully(boolean trainSelectedBenchmarksFully) {
        this.trainSelectedBenchmarksFully = trainSelectedBenchmarksFully;
    }

    public interface Scorer extends Serializable {
        double findScore(EnhancedAbstractClassifier eac);
    }

    private Scorer scorer = eac -> {
        final ClassifierResults results = eac.getTrainResults();
        final double acc = results.getAcc();
        return acc;
    };

    // todo param handling

    public RLTunedClassifier build() {
        rlTunedClassifier.setTrainSetupFunction(this);
        return rlTunedClassifier;
    }

    private int findLimit(int size, int rawLimit, double percentageLimit) {
        if(size == 0) {
            throw new IllegalArgumentException();
        }
        int result = size;
        if(rawLimit >= 0) {
            result = rawLimit;
        }
        if(NumUtils.isPercentage(percentageLimit)) {
            result = (int) (size * percentageLimit);
        }
        if(result == 0) {
            result = 1;
        }
        return result;
    }

    private boolean hasLimits() {
        return hasLimitedNeighbourhoodSize() || hasLimitedParamSpaceSize();
    }

    private boolean hasLimitedParamSpaceSize() {
        return paramSpaceSizeLimit >= 0 || NumUtils.isPercentage(paramSpaceSizeLimitPercentage);
    }

    private boolean hasLimitedNeighbourhoodSize() {
        return neighbourhoodSizeLimit >= 0 || NumUtils.isPercentage(neighbourhoodSizeLimitPercentage);
    }

    private boolean withinParamSpaceSizeLimit() {
        return paramCount.get() < maxParamSpaceSize;
    }

    private boolean withinNeighbourhoodSizeLimit() {
        return neighbourCount.get() < maxNeighbourhoodSize;
    }

    public double getParamSpaceSizeLimitPercentage() {
        return paramSpaceSizeLimitPercentage;
    }

    public RLTunedKNNSetup setParamSpaceSizeLimitPercentage(final double paramSpaceSizeLimitPercentage) {
        this.paramSpaceSizeLimitPercentage = paramSpaceSizeLimitPercentage;
        return this;
    }

    public Set<EnhancedAbstractClassifier> getImproveableBenchmarks() {
        return improveableBenchmarks;
    }

    public Set<EnhancedAbstractClassifier> getUnimprovableBenchmarks() {
        return unimprovableBenchmarks;
    }

    public Set<EnhancedAbstractClassifier> getAllBenchmarks() {
        final HashSet<EnhancedAbstractClassifier> benchmarks = new HashSet<>();
        benchmarks.addAll(unimprovableBenchmarks);
        benchmarks.addAll(improveableBenchmarks);
        return benchmarks;
    }

    private boolean isImproveable(EnhancedAbstractClassifier benchmark) {
        try {
            final KNNLOOCV knn = (KNNLOOCV) benchmark;
            return knn.getNeighbourLimit() + 1 <= maxNeighbourhoodSize;
        } catch(Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean hasNextSourceTime() {
        return !rlTunedClassifier.hasTrainTimeLimit() || longestExploreTimeNanos < rlTunedClassifier.getRemainingTrainTimeNanos();
    }

    private boolean hasNextImprovementTime() {
        return !rlTunedClassifier.hasTrainTimeLimit() || longestExploitTimeNanos < rlTunedClassifier.getRemainingTrainTimeNanos();
    }

    private boolean hasNextSource() {
        return explorer.hasNext();
    }

    private boolean hasNextImprovement() {
        return exploiter.hasNext();
    }

    @Override
    public void accept(Instances trainData) {
        neighbourCount = new Box<>(1); // must start at 1 otherwise the loocv produces no train estimate
        paramCount = new Box<>(0);
        longestExploreTimeNanos = 0;
        id = 0;
        longestExploitTimeNanos = 0;
        fullyTrainedIterator = null;
        nextImproveableBenchmarks = new HashSet<>();
        improveableBenchmarks = new HashSet<>();
        unimprovableBenchmarks = new HashSet<>();
        improveableBenchmarkIterator = improveableBenchmarkIteratorBuilder.apply(new ArrayList<>());
        finalBenchmarks = PrunedMultimap.desc(ArrayList::new);
        finalBenchmarks.setSoftLimit(1);
        final int seed = rlTunedClassifier.getSeed();
        paramSpace = paramSpaceFunction.apply(trainData);
        paramSetIterator = new RandomListIterator<>(this.paramSpace, seed).setRemovedOnNext(true);
        fullParamSpaceSize = this.paramSpace.size();
        fullNeighbourhoodSize = trainData.size(); // todo check all seeds set
        maxNeighbourhoodSize = findLimit(fullNeighbourhoodSize, neighbourhoodSizeLimit, neighbourhoodSizeLimitPercentage);
        maxParamSpaceSize = findLimit(fullParamSpaceSize, paramSpaceSizeLimit, paramSpaceSizeLimitPercentage);
        if(rlTunedClassifier.hasTrainTimeLimit() && hasLimits()) {
            throw new IllegalStateException("cannot train under a contract with limits set");
        }
        if(!incrementalMode) {
            neighbourCount.set(maxNeighbourhoodSize);
        }
        // transform classifiers into benchmarks
        explorer = new ParamExplorer();
        // setup an iterator to improve benchmarks
        exploiter = new NeighbourExploiter();
        optimiser = new LeeOptimiser();
        agent = new KnnAgent();
        // set corresponding iterators in the incremental tuned classifier
        rlTunedClassifier.setAgent(agent);
        rlTunedClassifier.setEnsembler(Ensembler.single());
        // todo make sure the seeds are set for everything
    }

    private class ParamExplorer implements Iterator<EnhancedAbstractClassifier> {
        @Override public EnhancedAbstractClassifier next() {
            ParamSet paramSet = paramSetIterator.next();
            paramCount.set(paramCount.get() + 1);
            final KNNLOOCV knn = knnSupplier.get();
            knn.setParams(paramSet);
            final String name = knn.getClassifierName() + "_" + (id++);
            knn.setClassifierName(name);
            knn.setNeighbourLimit(neighbourCount.get());
            return knn;
        }

        @Override public boolean hasNext() {
            return paramSetIterator.hasNext() && withinParamSpaceSizeLimit();
        }
    }

    private class NeighbourExploiter implements Iterator<EnhancedAbstractClassifier> {

        @Override
        public EnhancedAbstractClassifier next() {
            final int origNeighbourCount = neighbourCount.get();
            final int nextNeighbourCount = origNeighbourCount + 1;
            rlTunedClassifier.getLogger().info(() -> "neighbourhood " + origNeighbourCount + " --> " + nextNeighbourCount);
            neighbourCount.set(nextNeighbourCount);
            if(!improveableBenchmarkIterator.hasNext()) {
                improveableBenchmarks = nextImproveableBenchmarks;
                nextImproveableBenchmarks = new HashSet<>();
                improveableBenchmarkIterator = improveableBenchmarkIteratorBuilder.apply(new ArrayList<>(improveableBenchmarks));
                if(!improveableBenchmarkIterator.hasNext()) {
                    throw new IllegalStateException("it definitely should have next");
                }
            }
            final EnhancedAbstractClassifier classifier = improveableBenchmarkIterator.next();
            improveableBenchmarkIterator.remove();
            final KNNLOOCV knn = (KNNLOOCV) classifier; // todo should get rid of this with some generic twisting
            if(rlTunedClassifier.isDebug()) {
                final int currentNeighbourLimit = knn.getNeighbourLimit();
                if(nextNeighbourCount <= currentNeighbourLimit) {
                    throw new IllegalStateException("no improvement to the number of neighbours");
                }
            }
            knn.setNeighbourLimit(nextNeighbourCount);
            finalBenchmarks.remove(scorer.findScore(classifier), classifier); // remove the current classifier from the final benchmarks
            return knn;
        }

        @Override
        public boolean hasNext() {
            return improveableBenchmarkIterator.hasNext() || !nextImproveableBenchmarks.isEmpty();
        }
    }

    private class LeeOptimiser implements Optimiser {

        @Override
        public boolean shouldSource() {
            // only called when *both* improvements and source remain
            final int neighbours = neighbourCount.get();
            final int params = paramCount.get();
            if(params < maxParamSpaceSize / 10) {
                // 10% params, 0% neighbours
                return true;
            } else if(neighbours < maxNeighbourhoodSize / 10) {
                // 10% params, 10% neighbours
                return false;
            } else if(params < maxParamSpaceSize / 2) {
                // 50% params, 10% neighbours
                return true;
            } else if(neighbours < maxNeighbourhoodSize / 2) {
                // 50% params, 50% neighbours
                return false;
            } else if(params < maxParamSpaceSize) {
                // 100% params, 50% neighbours
                return true;
            }
            else {
                // by this point all params have been hit. Therefore, shouldSource should not be called at
                // all as only improvements will remain, if any.
                throw new IllegalStateException("invalid source / improvement state");
            }
        }
    }

    public Function<List<EnhancedAbstractClassifier>, Iterator<EnhancedAbstractClassifier>> getImproveableBenchmarkIteratorBuilder() {
        return improveableBenchmarkIteratorBuilder;
    }

    public void setImproveableBenchmarkIteratorBuilder(Function<List<EnhancedAbstractClassifier>, Iterator<EnhancedAbstractClassifier>> improveableBenchmarkIteratorBuilder) {
        this.improveableBenchmarkIteratorBuilder = improveableBenchmarkIteratorBuilder;
    }

    public Agent getAgent() {
        return agent;
    }

    public int getFullParamSpaceSize() {
        return fullParamSpaceSize;
    }

    public int getFullNeighbourhoodSize() {
        return fullNeighbourhoodSize;
    }

    public Set<EnhancedAbstractClassifier> getNextImproveableBenchmarks() {
        return nextImproveableBenchmarks;
    }

    public Iterator<EnhancedAbstractClassifier> getImproveableBenchmarkIterator() {
        return improveableBenchmarkIterator;
    }

    public PrunedMultimap<Double, EnhancedAbstractClassifier> getFinalBenchmarks() {
        return finalBenchmarks;
    }

    public boolean isExplore() {
        return explore;
    }

    public RLTunedClassifier getRlTunedClassifier() {
        return rlTunedClassifier;
    }

    public RLTunedKNNSetup setRlTunedClassifier(final RLTunedClassifier rlTunedClassifier) {
        this.rlTunedClassifier = rlTunedClassifier;
        return this;
    }

    public ParamSpace getParamSpace() {
        return paramSpace;
    }

    public RLTunedKNNSetup setParamSpace(final ParamSpace paramSpace) {
        this.paramSpace = paramSpace;
        return this;
    }

    public Iterator<ParamSet> getParamSetIterator() {
        return paramSetIterator;
    }

    public RLTunedKNNSetup setParamSetIterator(final Iterator<ParamSet> paramSetIterator) {
        this.paramSetIterator = paramSetIterator;
        return this;
    }

    public int getMaxParamSpaceSize() {
        return maxParamSpaceSize;
    }

    public RLTunedKNNSetup setParamSpaceSizeLimit(final int limit) {
        this.neighbourhoodSizeLimit = limit;
        return this;
    }

    public int getMaxNeighbourhoodSize() {
        return maxNeighbourhoodSize;
    }

    public RLTunedKNNSetup setNeighbourhoodSizeLimit(final int limit) {
        this.neighbourhoodSizeLimit = limit;
        return this;
    }

    public int getNeighbourhoodSizeLimit() {
        return neighbourhoodSizeLimit;
    }

    public int getParamSpaceSizeLimit() {
        return paramSpaceSizeLimit;
    }

    public Integer getNeighbourCount() {
        return neighbourCount.get();
    }

    public Integer getParamCount() {
        return paramCount.get();
    }

    public long getLongestExploreTimeNanos() {
        return longestExploreTimeNanos;
    }

    public RLTunedKNNSetup setLongestExploreTimeNanos(final long longestExploreTimeNanos) {
        this.longestExploreTimeNanos = longestExploreTimeNanos;
        return this;
    }

    public long getLongestExploitTimeNanos() {
        return longestExploitTimeNanos;
    }

    public RLTunedKNNSetup setLongestExploitTimeNanos(final long longestExploitTimeNanos) {
        this.longestExploitTimeNanos = longestExploitTimeNanos;
        return this;
    }

    public Function<Instances, ParamSpace> getParamSpaceFunction() {
        return paramSpaceFunction;
    }

    public RLTunedKNNSetup setParamSpaceFunction(
        final Function<Instances, ParamSpace> paramSpaceFunction) {
        this.paramSpaceFunction = paramSpaceFunction;
        return this;
    }

    public Optimiser getOptimiser() {
        return optimiser;
    }

    public RLTunedKNNSetup setOptimiser(final Optimiser optimiser) {
        this.optimiser = optimiser;
        return this;
    }

    public Supplier<KNNLOOCV> getKnnSupplier() {
        return knnSupplier;
    }

    public RLTunedKNNSetup setKnnSupplier(final Supplier<KNNLOOCV> knnSupplier) {
        this.knnSupplier = knnSupplier;
        return this;
    }

    public RLTunedKNNSetup setParamSpace(Function<Instances, ParamSpace> func) {
        return setParamSpaceFunction(func);
    }

    public RLTunedKNNSetup setParamSpaceFunction(Supplier<ParamSpace> supplier) {
        return setParamSpace(i -> supplier.get());
    }

    public RLTunedKNNSetup setParamSpace(Supplier<ParamSpace> supplier) {
        return setParamSpaceFunction(supplier);
    }

    public double getNeighbourhoodSizeLimitPercentage() {
        return neighbourhoodSizeLimitPercentage;
    }

    public RLTunedKNNSetup setNeighbourhoodSizeLimitPercentage(final double neighbourhoodSizeLimitPercentage) {
        this.neighbourhoodSizeLimitPercentage = neighbourhoodSizeLimitPercentage;
        return this;
    }
}
