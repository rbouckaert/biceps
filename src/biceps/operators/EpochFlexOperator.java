package biceps.operators;


import java.text.DecimalFormat;

import beast.core.Description;
import beast.core.Input;
import beast.core.Operator;
import beast.core.parameter.IntegerParameter;
import beast.core.Input.Validate;
import beast.evolution.operators.KernelDistribution;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.evolution.tree.coalescent.TreeIntervals;
import beast.util.Randomizer;

@Description("Scale operator that scales random epoch in a tree")
public class EpochFlexOperator extends Operator {
    final public Input<Tree> treeInput = new Input<>("tree", "beast.tree on which this operation is performed", Validate.REQUIRED);
    final public Input<KernelDistribution> kernelDistributionInput = new Input<>("kernelDistribution", "provides sample distribution for proposals", 
    		KernelDistribution.newDefaultKernelDistribution());
    final public Input<Boolean> optimiseInput = new Input<>("optimise", "flag to indicate that the scale factor is automatically changed in order to achieve a good acceptance rate (default true)", true);
    final public Input<Double> scaleFactorInput = new Input<>("scaleFactor", "scaling factor -- positive number that determines size of the jump: higher means bigger jumps.", 0.05);

    final public Input<Boolean> fromOldestTipOnlyInput = new Input<>("fromOldestTipOnly", "only scale parts between root and oldest tip. If false, use any epoch between youngest tip and root.", true); 

    final public Input<IntegerParameter> groupSizeParamInput = new Input<>("groupSizes", "the group sizes parameter. If specified, use group sizes as boundaries"
    		+ "(and fromOldestTipOnly is ignored)");
    final public Input<TreeIntervals> treeIntervalsInput = new Input<>("treeIntervals", "Intervals for a phylogenetic beast tree. Must be specified if groupSizes is specified.");
    
    protected KernelDistribution kernelDistribution;
    protected double scaleFactor;
    private boolean fromOldestTipOnly;
    private IntegerParameter groupSizes;
    private TreeIntervals treeIntervals;
    
    @Override
	public void initAndValidate() {
    	kernelDistribution = kernelDistributionInput.get();
    	scaleFactor = scaleFactorInput.get();
    	fromOldestTipOnly = fromOldestTipOnlyInput.get();
    	groupSizes = groupSizeParamInput.get();
    	if (groupSizes != null) {
    		treeIntervals = treeIntervalsInput.get();
    		if (treeIntervals == null) {
    			throw new IllegalArgumentException("treeIntervals must be specified if groupSizes are specified");
    		}
    	}
	}	
	
	public EpochFlexOperator(){}
	
	public EpochFlexOperator(Tree tree, double weight) {
		initByName("tree", tree, "weight", weight);
	}
	
	
	

    @Override
    public double proposal() {
    	Tree tree = treeInput.get();
    	double oldHeight = tree.getRoot().getHeight();
    	
    	double upper = tree.getRoot().getHeight();
		double lower0 = 0;
		Node [] nodes = tree.getNodesAsArray();
		
		if (fromOldestTipOnly) {
			for (int i = 0; i < nodes.length/2+1; i++) {
				lower0 = Math.max(nodes[i].getHeight(), lower0);
			}
		}
	
		double intervalLow = 0;
		double intervalHi = 0;
		
		if (groupSizes != null) {
			int k = Randomizer.nextInt(groupSizes.getDimension());
			
			int j = 0;
			for (int i = 0; i < k; i++) {
				j += groupSizes.getValue(i);
			}
			intervalLow = treeIntervals.getIntervalTime(j);
			intervalHi = treeIntervals.getIntervalTime(j + groupSizes.getValue(k));
		} else {
			intervalLow = lower0 + Randomizer.nextDouble() * (upper - lower0);
			intervalHi  = lower0 + Randomizer.nextDouble() * (upper - lower0);
		}
		
		if (intervalHi < intervalLow) {
			// make sure intervalLow < intervalHi
			double tmp = intervalHi; intervalHi = intervalLow; intervalLow = tmp;
		}

		double scale = kernelDistribution.getScaler(1, scaleFactor);
		double to = intervalLow + scale * (intervalHi - intervalLow);
		double delta = to-intervalHi;
		
		int scaled = 0;
		for (int i = nodes.length/2+1; i < nodes.length; i++) {
			Node node = nodes[i];
			double h = node.getHeight();
			if (h > intervalLow && h < intervalHi) {
				h = intervalLow + scale * (h-intervalLow);
				node.setHeight(h);
				scaled++;
			} else if (h > intervalHi) {				
				h += delta;
				node.setHeight(h);
			}
		}

		for (Node node0 : nodes) {
			if (node0.getLength() < 0) {
				return Double.NEGATIVE_INFINITY;
			}
		}

		double newHeight = tree.getRoot().getHeight();

		double logHR = scaled * Math.log(scale);
		if (groupSizes == null) {
			logHR += 2 * Math.log(newHeight/oldHeight);
		}
		return logHR;
    }

    
    
    @Override
    public void optimize(double logAlpha) {
        // must be overridden by operator implementation to have an effect
    	if (optimiseInput.get()) {
	        double delta = calcDelta(logAlpha);
	        double scaleFactor = getCoercableParameterValue();
	        delta += Math.log(scaleFactor);
	        scaleFactor = Math.exp(delta);
	        setCoercableParameterValue(scaleFactor);
    	}
    }
    
    @Override
    public double getTargetAcceptanceProbability() {
    	return 0.4;
    }
    
    @Override
    public double getCoercableParameterValue() {
        return scaleFactor;
    }

    @Override
    public void setCoercableParameterValue(final double value) {
    	scaleFactor = value; // Math.max(Math.min(value, upper), lower);
    }


    @Override
    public String getPerformanceSuggestion() {
        double prob = m_nNrAccepted / (m_nNrAccepted + m_nNrRejected + 0.0);
        double targetProb = getTargetAcceptanceProbability();

        double ratio = prob / targetProb;
        if (ratio > 2.0) ratio = 2.0;
        if (ratio < 0.5) ratio = 0.5;

        // new scale factor
        double newWindowSize = getCoercableParameterValue() * ratio;

        DecimalFormat formatter = new DecimalFormat("#.###");
        if (prob < 0.10 || prob > 0.40) {
            return "Try setting scale factor to about " + formatter.format(newWindowSize);
        } else return "";
    }
}
