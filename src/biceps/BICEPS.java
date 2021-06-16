package biceps;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.distribution.GammaDistribution;

import beast.core.*;
import beast.core.Input.Validate;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.TreeDistribution;
import beast.evolution.tree.coalescent.IntervalType;
import beast.evolution.tree.coalescent.TreeIntervals;
import beast.util.Randomizer;

@Description("Bayesian Integrated Coalescent Epoch PlotS: "
		+ "Bayesian skyline plot that integrates out population sizes under an inverse gamma prior")
public class BICEPS extends TreeDistribution {
    final public Input<RealParameter> populationShapeInput = new Input<>("populationShape", "Shape of the inverse gamma prior distribution on population sizes.", Validate.REQUIRED);
    final public Input<RealParameter> populationMeanInput = new Input<>("populationMean", "Mean of the inverse gamma prior distribution on population sizes.", Validate.REQUIRED);
    public Input<Double> ploidyInput = new Input<>("ploidy", "Ploidy (copy number) for the gene, typically a whole number or half (default is 2) "
    		+ "autosomal nuclear: 2, X: 1.5, Y: 0.5, mitrochondrial: 0.5.", 2.0);
    final public Input<IntegerParameter> groupSizeParamInput = new Input<>("groupSizes", "the group sizes parameter", Validate.REQUIRED);
    final public Input<Boolean> linkedMeanInput = new Input<>("linkedMean", "use populationMean only for first epoch, and for other epochs "
    		+ "use the posterior mean of the previous epoch", false);
    final public Input<Boolean> logMeansInput = new Input<>("logMeans", "log mean population size estimates for each epoch", false);

    private RealParameter populationShape;
    private RealParameter populationMean;
    
	 // alpha: alpha parameter of inverse Gamma prior on pop sizes
	 // beta: ditto but beta parameters
	 // ploidy: copy number of gene
    private double alpha, beta, ploidy;
    
    private IntegerParameter groupSizes;
    private TreeIntervals intervals;
    private boolean m_bIsPrepared = false, linkedMean = false, logMeans = false;
    private double prevMean;


    @Override
    public void initAndValidate() {
    	super.initAndValidate();
    	
    	populationShape = populationShapeInput.get();
    	populationMean = populationMeanInput.get();
    	linkedMean = linkedMeanInput.get();
    	logMeans = logMeansInput.get();
    	ploidy = ploidyInput.get();
    	if (ploidy <= 0) {
    		throw new IllegalArgumentException("ploidy should be a positive number, not " + ploidy);
    	}			

    	if (treeInput.get() != null) {
            throw new IllegalArgumentException("only tree intervals (not tree) should not be specified");
        }
        intervals = treeIntervalsInput.get();
        groupSizes = groupSizeParamInput.get();

        // make sure that the sum of groupsizes == number of coalescent events
        int events = intervals.treeInput.get().getInternalNodeCount();
        if (groupSizes.getDimension() > events) {
            throw new IllegalArgumentException("There are more groups than coalescent nodes in the tree.");
        }
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
                        "The sum of the initial group sizes does not match the number of coalescent events in the tree.");
            }
        }

        prepare();
    }

    public void prepare() {

        int intervalCount = 0;
        for (int i = 0; i < groupSizes.getDimension(); i++) {
            intervalCount += groupSizes.getValue(i);
        }

        assert (intervals.getSampleCount() == intervalCount);
        m_bIsPrepared = true;
    }

    /**
     * CalculationNode methods *
     */

    public List<String> getParameterIds() {
        List<String> paramIDs = new ArrayList<>();
        paramIDs.add(groupSizes.getID());
        return paramIDs;
    }
    
	@Override
	public double calculateLogP() {
        if (!m_bIsPrepared) {
            prepare();
        }

        
        alpha = populationShape.getValue();
        beta = populationMean.getValue() * (alpha - 1.0);

        logP = 0.0;

        int groupIndex = 0;
        Integer [] groupSizes = this.groupSizes.getValues();
        int subIndex = 0;

        List<Integer> lineageCounts = new ArrayList<>();
        List<Double> intervalSizes = new ArrayList<>();
        
        for (int j = 0; j < intervals.getIntervalCount(); j++) {
            lineageCounts.add(intervals.getLineageCount(j));
            intervalSizes.add(intervals.getInterval(j));
            if (intervals.getIntervalType(j) == IntervalType.COALESCENT) {
                subIndex += 1;
            }
            if (subIndex >= groupSizes[groupIndex]) {
            	logP += analyticalLogP(lineageCounts, groupSizes[groupIndex], intervalSizes);
            	if (linkedMean) {
            		beta = prevMean * (alpha - 1.0);
            	}
                groupIndex += 1;
                subIndex = 0;
                lineageCounts.clear();
                intervalSizes.clear();
            }
        }
        return logP;
    }
	
	/**
	 * Analytically integrates out population sizes on an epoch in a tree
	 * @param lineageCount: number of lineages at bottom of the epoch
	 * @param eventCount: number of coalescent events in epoch (this excludes tip being sampled)
	 * @param intervalSizes: array of interval sizes
	 * @return
	 */
    private double analyticalLogP(
    		List<Integer> lineageCounts, 
    		int eventCounts,
    		List<Double> intervalSizes 
    		) {

        double partialGamma = 0.0;
        // contributions of intervals
        for (int i = 0; i < lineageCounts.size(); i++) {
        	partialGamma += intervalSizes.get(i) * lineageCounts.get(i) * (lineageCounts.get(i) - 1.0) / 2.0;
        }

        double logGammaRatio = 0.0;
        for (int i = 0; i < eventCounts; i++) {
            logGammaRatio += Math.log(alpha + i);
        }
        
        prevMean = (beta + partialGamma/ploidy)/(alpha + eventCounts - 1);

        final double logP = 
        		- (alpha + eventCounts) * Math.log(beta + partialGamma / ploidy) 
        		+ alpha * Math.log(beta) 
        		- eventCounts * Math.log(ploidy) 
        		+ logGammaRatio;

        return logP;
    }

    MyRandomizer myRandomizer = new MyRandomizer();
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
	public void init(PrintStream out) {
		super.init(out);
        for (int i = 1; i <= groupSizes.getDimension(); i++) {
        	out.print("ibspPopSizes." + i+ "\t");
        }
        for (int i = 1; i <= groupSizes.getDimension(); i++) {
        	out.print("ibspGroupSizes." + i+ "\t");
        }
        if (logMeans) {
            for (int i = 1; i <= groupSizes.getDimension(); i++) {
            	out.print("ibspMeanPopSizes." + i+ "\t");
            }
        }

	}
	
	@Override
	public void log(long sampleNr, PrintStream out) {
        if (!m_bIsPrepared) {
            prepare();
        }
        
        alpha = populationShape.getValue();
        beta = populationMean.getValue() * (alpha - 1.0);

        int groupIndex = 0;
        Integer [] groupSizes = this.groupSizes.getValues();
        int subIndex = 0;

        List<Integer> lineageCounts = new ArrayList<>();
        List<Double> intervalSizes = new ArrayList<>();
        double [] popSizes = new double[groupSizes.length];
    	double [] meanPopSizes = new double[groupSizes.length];

        for (int j = 0; j < intervals.getIntervalCount(); j++) {
            lineageCounts.add(intervals.getLineageCount(j));
            intervalSizes.add(intervals.getInterval(j));
            if (intervals.getIntervalType(j) == IntervalType.COALESCENT) {
                subIndex += 1;
            }
            if (subIndex >= groupSizes[groupIndex]) {
            	
            	popSizes[groupIndex] = sample(lineageCounts, groupSizes[groupIndex], intervalSizes);
            	meanPopSizes[groupIndex] = groupIndex == 0 || !linkedMean ? populationMean.getValue() : prevMean;
            	if (linkedMean) {
            		beta = prevMean * (alpha - 1.0);
            	}
            	
                groupIndex += 1;
                subIndex = 0;
                lineageCounts.clear();
                intervalSizes.clear();
            }
        }
        
        
        super.log(sampleNr, out);
        for (double d : popSizes) {
        	out.print(d + "\t");
        }
        for (int i : groupSizes) {
        	out.print(i + "\t");
        }
        if (logMeans) {
            for (double d : meanPopSizes) {
            	out.print(d + "\t");
            }
        }
    }

	
	private double[] calcMeans() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void close(PrintStream out) {
		super.close(out);
	}
	
	/** sample population size for one epoch **/
    private double sample(List<Integer> lineageCounts, 
    		int eventCounts,
    		List<Double> intervalSizes ) {
    	double a = 0; // = sum_j k_{jb}
		a += eventCounts;
		
		double b = 0; // = sum_j 1/ploidy \sum_i c_jbi(2 choose (n_jb - i))
		double c = 0;
        for (int i = 0; i < lineageCounts.size(); i++) {
        	c += intervalSizes.get(i) * lineageCounts.get(i) * (lineageCounts.get(i) - 1.0) / 2.0;
        }
		c /= ploidy;
		b += c;
		
		
		double alpha = this.alpha + a;
		double beta = this.beta + b;
		prevMean = beta / (alpha - 1);
		 
		// https://stats.stackexchange.com/questions/224714/sampling-from-an-inverse-gamma-distribution
		GammaDistribution g = new GammaDistribution(myRandomizer, alpha, 1.0/beta, GammaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY);
		double newN = 1.0/g.sample();
		return newN;
	}

    
    @Override
    public void store() {
        m_bIsPrepared = false;
        super.store();
    }

    @Override
    public void restore() {
        m_bIsPrepared = false;
        super.restore();
    }

    @Override
    protected boolean requiresRecalculation() {
        m_bIsPrepared = false;
        return true;
    }

}
