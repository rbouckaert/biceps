package biceps;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.parameter.IntegerParameter;
import beast.base.evolution.tree.TreeDistribution;
import beast.base.evolution.tree.TreeInterface;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.util.Randomizer;

@Description("Base class for epoch-based tree distributions like BICEPS and YuleSkyline")
public class EpochTreeDistribution extends TreeDistribution {
    final public Input<Integer> groupCountInput = new Input<>("groupCount", "the number of groups used, which determines the dimension of the groupSizes parameter. "
    		+ "If less than zero (default) 10 groups will be used, "
    		+ "unless group sizes are larger than 30 (then group count = number of taxa/30) or "
    		+ "less than 6 (then group count = number of taxa/6", -1);
    final public Input<IntegerParameter> groupSizeParamInput = new Input<>("groupSizes", 
    		"The group sizes parameter. Ignored if equalEpochs=true. "
    		+ "If not estimated (estimate=false on this parameter), fixed group sizes will be used, otherwise they will be estimated."
    		+ "If not specified, a fixed set of group sizes determined by the groupCount input will be used.");
    final public Input<Boolean> useEqualEpochsInput = new Input<>("equalEpochs", "if equalEpochs is false, use epochs based on groups "
    		+ "from tree intervals, otherwise use equal sized epochs that scale with the tree height", false);
    final public Input<Boolean> linkedMeanInput = new Input<>("linkedMean", "use populationMean only for first epoch, and for other epochs "
    		+ "use the posterior mean of the previous epoch", false);
    final public Input<Boolean> logMeansInput = new Input<>("logMeans", "log mean population size estimates for each epoch", false);

    protected IntegerParameter groupSizes;
    protected TreeIntervals intervals;
    protected boolean isPrepared = false, linkedMean = false, logMeans = false;
    protected double prevMean;
    
    /** actual number of groups in use **/
    protected int groupCount;

    protected MyRandomizer myRandomizer = new MyRandomizer();
    /** if useEqualEpochs is false, use epochs based on groups from tree intervals, 
     * otherwise use equal sized epochs that scale with the tree height **/
    protected boolean useEqualEpochs = false;
    protected double [] lengths;
    protected int [] eventCounts;

    @Override
    public void initAndValidate() {
    	super.initAndValidate();
    	linkedMean = linkedMeanInput.get();
    	logMeans = logMeansInput.get();

		useEqualEpochs = useEqualEpochsInput.get();
    	if (!useEqualEpochs && treeInput.get() != null) {
            throw new IllegalArgumentException("only tree intervals (not tree) should not be specified when not using equal intervals");
        }
        
    	intervals = treeIntervalsInput.get();
		TreeInterface tree = treeInput.get();
		if (tree == null) {
			tree = intervals.treeInput.get();
		}	

		groupCount = groupCountInput.get();
        if (groupCount <= 0) {
        	groupCount = 10;
        	int n = tree.getInternalNodeCount();
        	if (n/10 > 30) {
        		groupCount = n/30;
        	} else if (n/10 < 6) {
        		groupCount = n/6;
        	}
        }

        groupSizes = groupSizeParamInput.get();
        if (groupSizes == null) {
        	groupSizes = new IntegerParameter("1");
        }
        groupSizes.setDimension(groupCount);
        
        // make sure that the sum of groupsizes == number of coalescent events
        if (!useEqualEpochs) {
	        int events = intervals.treeInput.get().getInternalNodeCount();
	        if (groupSizes.getDimension() > events) {
	            throw new IllegalArgumentException("There are more groups than coalescent nodes in the tree.");
	        }
        }

    	if (!useEqualEpochs) {
    		setUpIntervals();
    	} else {
    		setUpEqualIntervals();
    	}
    }
    
    
    private void setUpEqualIntervals() {
		TreeInterface tree = treeInput.get();
		if (tree == null) {
			tree = intervals.treeInput.get();
		}	
        lengths = new double[groupCount];
        eventCounts = new int[groupCount];        
    }
    
    private void setUpIntervals() {
        int events = intervals.treeInput.get().getInternalNodeCount();
        int paramDim2 = groupSizes.getDimension();

        int eventsCovered = 0;
        for (int i = 0; i < groupSizes.getDimension(); i++) {
            eventsCovered += groupSizes.getValue(i);
        }

        if (eventsCovered != events) {
            if (eventsCovered == 0 || eventsCovered == paramDim2) {
                // For these special cases we assume that the XML has not
                // specified initial group sizes
                // or has set all to 1 and we set them here automatically...
                int eventsEach = events / paramDim2;
                int eventsExtras = events % paramDim2;
                Integer[] values = new Integer[paramDim2];
                for (int i = 0; i < paramDim2; i++) {
                    if (i < eventsExtras) {
                        values[i] = eventsEach + 1;
                    } else {
                        values[i] = eventsEach;
                    }
                }
                IntegerParameter parameter = new IntegerParameter(values);
                parameter.setBounds(1, Integer.MAX_VALUE);
                groupSizes.assignFromWithoutID(parameter);
            } else {
                // ... otherwise assume the user has made a mistake setting
                // initial group sizes.
                throw new IllegalArgumentException(
                        "The sum of the initial group sizes does not match the number of coalescent events in the tree:" + eventsCovered + "!=" + events
                        + "\nConsider setting groupCount to the desired number of groups");
            }
        }

    }
    
    
    public void prepare() {

        int intervalCount = 0;
        for (int i = 0; i < groupSizes.getDimension(); i++) {
            intervalCount += groupSizes.getValue(i);
        }

        assert (intervals.getSampleCount() == intervalCount);
        isPrepared = true;
    }


	/**
	 *  this class is used to make sure the apache library uses random numbers from the BEAST Randomizer
	 *  so that the MCMC chain remains deterministic and starting with a certain seed twice will result in
	 *  the same sequence.
	 */
	public class MyRandomizer implements org.apache.commons.math3.random.RandomGenerator {

		@Override
		public double nextDouble() {
			return Randomizer.nextDouble();
		}

		@Override
		public float nextFloat() {
			return Randomizer.nextFloat();
		}

		@Override
		public int nextInt() {
			return Randomizer.nextInt();
		}

		@Override
		public long nextLong() {
			return Randomizer.nextLong();
		}

		@Override
		public void setSeed(int seed) {
			Randomizer.setSeed(seed);			
		}

		@Override
		public void setSeed(int[] seed) {
			throw new RuntimeException("Not implemented");
			// Randomizer.setSeed(seed);			
		}

		@Override
		public void setSeed(long seed) {
			Randomizer.setSeed(seed);			
		}

		@Override
		public void nextBytes(byte[] bytes) {
			Randomizer.nextBytes(bytes);
		}

		@Override
		public int nextInt(int n) {
			return Randomizer.nextInt(n);
		}

		@Override
		public boolean nextBoolean() {
			return Randomizer.nextBoolean();
		}

		@Override
		public double nextGaussian() {
			return Randomizer.nextGaussian();
		}
	}
	

    @Override
    public void store() {
        isPrepared = false;
        super.store();
    }

    @Override
    public void restore() {
        isPrepared = false;
        super.restore();
    }

    @Override
    protected boolean requiresRecalculation() {
        isPrepared = false;
        return true;
    }
}
