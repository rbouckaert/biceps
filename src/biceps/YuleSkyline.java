package biceps;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.distribution.GammaDistribution;

import beast.app.beauti.Beauti;
import beast.core.*;
import beast.core.Input.Validate;
import beast.core.parameter.RealParameter;
import beast.core.util.Log;
import beast.evolution.tree.coalescent.IntervalType;

@Description("Skyline version of Yule tree prior that integrates out birth rate parameters"
		+ " under a gamma prior")
public class YuleSkyline extends EpochTreeDistribution {
    final public Input<RealParameter> birthRateShapeInput = new Input<>("birthRateShape", "Shape of the gamma prior distribution on birth rates.", Validate.REQUIRED);
    final public Input<RealParameter> birthRateRateInput = new Input<>("birthRateRate", "Rate of the gamma prior distribution on birth rates.", Validate.REQUIRED);

    private RealParameter birthRateShape;
    private RealParameter birthRateRate;
    
	 // alpha: alpha parameter of Gamma prior on birth rates
	 // beta: dito but beta parameters
    private double alpha, beta;

    @Override
    public void initAndValidate() {
    	if (Beauti.isInBeauti()) {
    		return;
    	}
    	super.initAndValidate();
    	birthRateShape = birthRateShapeInput.get();
    	birthRateRate = birthRateRateInput.get();

        prepare();
    }


    /**
     * CalculationNode methods *
     */
    private int warningCount = 0;
    
	@Override
	public double calculateLogP() {
        if (!isPrepared) {
            prepare();
        }

        
        alpha = birthRateShape.getValue();
        beta = birthRateRate.getValue();

        logP = 0.0;

        int groupIndex = 0;
        Integer [] groupSizes = this.groupSizes.getValues();
        int subIndex = 0;

        List<Integer> lineageCounts = new ArrayList<>();
        List<Double> intervalSizes = new ArrayList<>();
        
        for (int j = intervals.getIntervalCount()-1; j >= 0 && groupIndex < groupSizes.length; j--) {
            lineageCounts.add(intervals.getLineageCount(j));
            intervalSizes.add(intervals.getInterval(j));
            if (intervals.getIntervalType(j) == IntervalType.COALESCENT) {
                subIndex += 1;
            } else {
            	if (warningCount < 10) {
            		Log.warning("Encountered a non-coalescent event -- this prior only works for trees without sampled tips.");
            	}
            	warningCount++;
            }
            if (subIndex >= groupSizes[groupIndex]) {
            	logP += analyticalLogP(lineageCounts, groupSizes[groupIndex], intervalSizes);
            	if (linkedMean) {
            		beta = alpha/prevMean;
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
	 * Analytically integrates out birth rates on an epoch in a tree
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

        double L = 0.0;
        // contributions of intervals
        for (int i = 0; i < lineageCounts.size(); i++) {
        	L += intervalSizes.get(i) * lineageCounts.get(i);
        }

        double logGammaRatio = 0.0;
        for (int i = 0; i < eventCounts; i++) {
            logGammaRatio += Math.log(alpha + i);
        }
        
        prevMean = (alpha + eventCounts)/(beta + L);

        final double logP = 
        		+ alpha * Math.log(beta) 
        		- Math.log(alpha)
        		+ logGammaRatio 
        		- (alpha + eventCounts) * Math.log(beta + L);

        return logP;
    }

	
	@Override
	public void init(PrintStream out) {
		super.init(out);
        for (int i = 1; i <= groupSizes.getDimension(); i++) {
        	out.print("BirthRates." + i+ "\t");
        }
        for (int i = 1; i <= groupSizes.getDimension(); i++) {
        	out.print("GroupSizes." + i+ "\t");
        }
        if (logMeans) {
            for (int i = 1; i <= groupSizes.getDimension(); i++) {
            	out.print("MeanBirthRates." + i+ "\t");
            }
        }

	}
	
	@Override
	public void log(long sampleNr, PrintStream out) {
        if (!isPrepared) {
            prepare();
        }
        
        alpha = birthRateShape.getValue();
        beta = birthRateRate.getValue();

        int groupIndex = 0;
        Integer [] groupSizes = this.groupSizes.getValues();
        int subIndex = 0;

        List<Integer> lineageCounts = new ArrayList<>();
        List<Double> intervalSizes = new ArrayList<>();
        double [] birthRates = new double[groupSizes.length];
    	double [] meanBirthRates = new double[groupSizes.length];

        for (int j = intervals.getIntervalCount()-1; j >= 0 && groupIndex < groupSizes.length; j--) {
            lineageCounts.add(intervals.getLineageCount(j));
            intervalSizes.add(intervals.getInterval(j));
            if (intervals.getIntervalType(j) == IntervalType.COALESCENT) {
                subIndex += 1;
            }
            if (subIndex >= groupSizes[groupIndex]) {
            	
            	birthRates[groupIndex] = sample(lineageCounts, groupSizes[groupIndex], intervalSizes);
            	meanBirthRates[groupIndex] = groupIndex == 0 || !linkedMean ? birthRateShape.getValue()/birthRateRate.getValue() : prevMean;
            	if (linkedMean) {
            		beta = alpha/prevMean;
            	}
            	
                groupIndex += 1;
                subIndex = 0;
                lineageCounts.clear();
                intervalSizes.clear();
            }
        }
        
        
        super.log(sampleNr, out);
        for (double d : birthRates) {
        	out.print(d + "\t");
        }
        for (int i : groupSizes) {
        	out.print(i + "\t");
        }
        if (logMeans) {
            for (double d : meanBirthRates) {
            	out.print(d + "\t");
            }
        }
    }

	
	@Override
	public void close(PrintStream out) {
		super.close(out);
	}
	
	/** sample birth rate for one epoch **/
    private double sample(List<Integer> lineageCounts, 
    		int eventCounts,
    		List<Double> intervalSizes ) {
    	
        double L = 0.0;
        // contributions of intervals
        for (int i = 0; i < lineageCounts.size(); i++) {
        	L += intervalSizes.get(i) * lineageCounts.get(i);
        }
        
        prevMean = (alpha + eventCounts)/(beta + L);

        GammaDistribution g = new GammaDistribution(myRandomizer, alpha + eventCounts, beta + L, GammaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY);
        double newLambda = g.sample();
		return newLambda;
	}

    

    
    @Override
    public boolean canHandleTipDates() {
    	return false;
    }

}
