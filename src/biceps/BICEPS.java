package biceps;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.distribution.GammaDistribution;

import beast.app.beauti.Beauti;
import beast.core.*;
import beast.core.Input.Validate;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.coalescent.IntervalType;

@Description("Bayesian Integrated Coalescent Epoch PlotS: "
		+ "Bayesian skyline plot that integrates out population sizes under an inverse gamma prior")
public class BICEPS extends EpochTreeDistribution {
    public Input<Double> ploidyInput = new Input<>("ploidy", "Ploidy (copy number) for the gene, typically a whole number or half (default is 2) "
    		+ "autosomal nuclear: 2, X: 1.5, Y: 0.5, mitrochondrial: 0.5.", 2.0);
    final public Input<RealParameter> populationShapeInput = new Input<>("populationShape", "Shape of the inverse gamma prior distribution on population sizes.", Validate.REQUIRED);
    final public Input<RealParameter> populationMeanInput = new Input<>("populationMean", "Mean of the inverse gamma prior distribution on population sizes.", Validate.REQUIRED);

    private RealParameter populationShape;
    private RealParameter populationMean;
    
	 // alpha: alpha parameter of inverse Gamma prior on pop sizes
	 // beta: dito but beta parameters
	 // ploidy: copy number of gene
    private double alpha, beta, ploidy;
    


    @Override
    public void initAndValidate() {
    	if (Beauti.isInBeauti()) {
    		return;
    	}
    	super.initAndValidate();
    	
    	populationShape = populationShapeInput.get();
    	populationMean = populationMeanInput.get();
    	ploidy = ploidyInput.get();
    	if (ploidy <= 0) {
    		throw new IllegalArgumentException("ploidy should be a positive number, not " + ploidy);
    	}			


        prepare();
    }


    /**
     * CalculationNode methods *
     */
    
	@Override
	public double calculateLogP() {
		
    	if (!useEqualEpochs) {
    		logP = calculateLogPbyIntervals();
    	} else {
    		logP = calculateLogPbyEqualEpochs();
    	}
    	return logP;
	}
	
	private double calculateLogPbyEqualEpochs() {
		throw new RuntimeException("Equal sized epochs are not implemented yet for " + getClass().getName());
	}
	
	private double calculateLogPbyIntervals() {
        if (!isPrepared) {
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
        
        for (int j = 0; groupIndex < groupSizes.length && j < intervals.getIntervalCount(); j++) {
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



	
	
	@Override
	public void init(PrintStream out) {
		super.init(out);
        for (int i = 1; i <= groupSizes.getDimension(); i++) {
        	out.print("PopSizes." + i+ "\t");
        }
        for (int i = 1; i <= groupSizes.getDimension(); i++) {
        	out.print("GroupSizes." + i+ "\t");
        }
        if (logMeans) {
            for (int i = 1; i <= groupSizes.getDimension(); i++) {
            	out.print("MeanPopSizes." + i+ "\t");
            }
        }

	}
	
	@Override
	public void log(long sampleNr, PrintStream out) {
        if (!isPrepared) {
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

        for (int j = 0; groupIndex < groupSizes.length && j < intervals.getIntervalCount(); j++) {
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
    public boolean canHandleTipDates() {
    	return true;
    }

}
