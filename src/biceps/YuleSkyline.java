package biceps;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.distribution.GammaDistribution;

import beast.app.beauti.Beauti;
import beast.core.*;
import beast.core.Input.Validate;
import beast.core.parameter.RealParameter;
import beast.core.util.Log;
import beast.evolution.tree.Node;
import beast.evolution.tree.TreeInterface;
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

    	if (!useEqualEpochs) {
    		prepare();
    	}
    }


    /**
     * CalculationNode methods *
     */
    private int warningCount = 0;
    
	@Override
	public double calculateLogP() {
    	if (!useEqualEpochs) {
    		logP = calculateLogPbyIntervals();
    	} else {
    		logP = calculateLogPbyEqualEpochs(Double.NEGATIVE_INFINITY);
    	}
    	return logP;
	}

	/**
	 * @param threshold Nodes below threshold are ignored
	 * @return logP
	 */
	protected double calculateLogPbyEqualEpochs(double threshold) {
		Arrays.fill(lengths, 0.0);
		Arrays.fill(eventCounts, 0);
		TreeInterface tree = treeInput.get();
		if (tree == null) {
			tree = treeIntervalsInput.get().treeInput.get();
		}
		// add epsilon=1e-10 so root falls in highest numbered epoch
		double rootHeight = tree.getRoot().getHeight() + 1e-10;

		for (Node node : tree.getInternalNodes()) {
			if (node.getHeight() > threshold) {
				eventCounts[(int)(node.getHeight() * groupCount / rootHeight)]++;
				for (Node child : node.getChildren()) {
					addLengths(child, rootHeight / groupCount);
				}
			}
		}
		
		logP = 0;
        alpha = birthRateShape.getValue();
        beta = birthRateRate.getValue();
		// walk the intervals from root to tips
		for (int k = groupCount-1; k >= 0; k--) {
	        double logGammaRatio = 0.0;
	        for (int i = 0; i < eventCounts[k]; i++) {
	            logGammaRatio += Math.log(alpha + i);
	        }
	        
	        double L = lengths[k];
	        prevMean = (alpha + eventCounts[k])/(beta + L);

	        logP += 
	        		+ alpha * Math.log(beta) 
	        		- Math.log(alpha)
	        		+ logGammaRatio 
	        		- (alpha + eventCounts[k]) * Math.log(beta + L);
        	if (linkedMean) {
        		beta = alpha/prevMean;
        	}
		}
		return logP;		
	}
	
	/** update 'lengths' array with length of branch above child **/
	private void addLengths(Node child, double delta) {
		double from = child.getHeight();
		double to = child.getParent().getHeight();
		int start = (int) (from / delta);
		int end = (int) (to / delta);
		if (start == end) {
			// branch is inside a single epochs
			lengths[start] += to-from;
			return;
		}
		
		// branch stretches over multiple epochs
		lengths[start] += delta * (start+1) - from;
		for (int i = start + 1; i < end; i++) {
			lengths[i] += delta;
		}
		lengths[end] += to - delta * end;
	}

	private double calculateLogPbyIntervals() {
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
		
        for (int i = 1; i <= groupCount; i++) {
        	out.print("BirthRates." + i+ "\t");
        }
		if (!useEqualEpochs) {
	        for (int i = 1; i <= groupCount; i++) {
	        	out.print("GroupSizes." + i+ "\t");
	        }
		}
        if (logMeans) {
            for (int i = 1; i <= groupCount; i++) {
            	out.print("MeanBirthRates." + i+ "\t");
            }
        }

	}
	
	
	
	/*
	 * Sample birth rates and mean birth rates
	 */
	public double[][] sampleBirthRates() {
		

        double [] birthRates = new double[groupCount];
    	double [] meanBirthRates = new double[groupCount];

		if (useEqualEpochs) {
			Arrays.fill(lengths, 0.0);
			Arrays.fill(eventCounts, 0);
			TreeInterface tree = treeInput.get();
			if (tree == null) {
				tree = treeIntervalsInput.get().treeInput.get();
			}
			// add epsilon=1e-10 so root falls in highest numbered epoch
			double rootHeight = tree.getRoot().getHeight() + 1e-10;

			for (Node node : tree.getInternalNodes()) {
				eventCounts[(int)(node.getHeight() * groupCount / rootHeight)]++;
				for (Node child : node.getChildren()) {
					addLengths(child, rootHeight / groupCount);
				}
			}
			
	        alpha = birthRateShape.getValue();
	        beta = birthRateRate.getValue();
			// walk the intervals from root to tips
			for (int k = groupCount-1; k >= 0; k--) {
		        prevMean = (alpha + eventCounts[k])/(beta + lengths[k]);
		        
		        GammaDistribution g = new GammaDistribution(myRandomizer, alpha + eventCounts[k], 1.0/(beta + lengths[k]), GammaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY);
		        birthRates[k] = g.sample();
		        meanBirthRates[k] = k == groupCount-1 ? birthRateShape.getValue()/birthRateRate.getValue() : prevMean;

	        	if (linkedMean) {
	        		beta = alpha/prevMean;
	        	}
			}

		} else  {
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
	        
		}
		
		
		double[][] result = new double[2][];
		result[0] = birthRates;
		result[1] = meanBirthRates;
		return result;
		
	}
	
	
	@Override
	public void log(long sampleNr, PrintStream out) {
        super.log(sampleNr, out);
        
        
        double[][] rates = this.sampleBirthRates();
        double [] birthRates = rates[0];
    	double [] meanBirthRates = rates[1];
        

		for (double d : birthRates) {
        	out.print(d + "\t");
        }
		if (!useEqualEpochs) {
	        for (int i : this.groupSizes.getValues()) {
	        	out.print(i + "\t");
	        }
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

        GammaDistribution g = new GammaDistribution(myRandomizer, alpha + eventCounts, 1.0/(beta + L), GammaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY);
        double newLambda = g.sample();
		return newLambda;
	}

    
    

    
    @Override
    public boolean canHandleTipDates() {
    	return false;
    }

}
