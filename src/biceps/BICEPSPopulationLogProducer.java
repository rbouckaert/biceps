package biceps;

import java.io.File;
import java.io.PrintStream;
import java.util.List;

import beast.app.treeannotator.TreeAnnotator;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.Input.Validate;
import beast.core.parameter.RealParameter;
import beast.core.util.Log;
import beast.evolution.tree.Tree;
import beast.evolution.tree.coalescent.TreeIntervals;
import beast.util.LogAnalyser;

@Description("Create log based on trace log and trees file")
public class BICEPSPopulationLogProducer extends Runnable {
	final public Input<File> inFile = new Input<>("trace", "trace file containing population mean samples",  Validate.REQUIRED);
    final public Input<String> populationMeanInput = new Input<>("populationMean", "label of the population mean in the trace file", "BICEPSPopSize");
	final public Input<TreeFile> treesInput = new Input<>("trees","NEXUS file containing a tree set", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file. Print to stdout if not specified");
    final public Input<Integer> groupCountInput = new Input<>("groupCount", "the number of groups used, which determines the dimension of the groupSizes parameter. "
    		+ "If less than zero (default) 10 groups will be used, "
    		+ "unless group sizes are larger than 30 (then group count = number of taxa/30) or "
    		+ "less than 6 (then group count = number of taxa/6", -1);

	
    public Input<Double> ploidyInput = new Input<>("ploidy", "Ploidy (copy number) for the gene, typically a whole number or half (default is 2) "
    		+ "autosomal nuclear: 2, X: 1.5, Y: 0.5, mitrochondrial: 0.5.", 2.0);
    final public Input<RealParameter> populationShapeInput = new Input<>("populationShape", "Shape of the inverse gamma prior distribution on population sizes.", new RealParameter("3.0"));

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		LogAnalyser traceLog = new LogAnalyser(inFile.get().getPath(), 0, true, false);
		int i = indexOf(traceLog.getLabels(), populationMeanInput.get());
		Double [] samples = traceLog.getTrace(0);
		Double [] popMeans = null;
		if (i >= 0) {
			popMeans = traceLog.getTrace(i+1);		
		} else {
			Log.warning("Cannot find trace \"" + populationMeanInput.get() + "\". Assuming popMean=1.0");
		}
		
		PrintStream out = System.out;
        if (outputInput.get() != null) {
			Log.warning("Writing to file " + outputInput.get().getPath());
        	out = new PrintStream(outputInput.get());
        }

        

        
        
        
        
        
        TreeAnnotator.MemoryFriendlyTreeSet treeSet = new TreeAnnotator().new MemoryFriendlyTreeSet(treesInput.get().getPath(), 0);
        treeSet.reset();
        int k = 0;
        while (treeSet.hasNext()) {
        	Tree tree = treeSet.next();
    		TreeIntervals intervals = new TreeIntervals(tree);
    		BICEPS biceps = new BICEPS();
    		biceps.setID("BICEPS");
    		biceps.initByName("linkedMean", true, 
    				"ploidy", ploidyInput.get(), 
    				"populationShape", populationShapeInput.get(), 
    				"populationMean", popMeans == null ? "1.0" : popMeans[k]+"", 
    				"groupCount", groupCountInput.get(),
//    				"groupSizes", "3 2",
    				"treeIntervals", intervals);
    		
    		double logP = biceps.calculateLogP();
    		if (k == 0) {
    			out.print("Sample\t");
    			biceps.init(out);
        		out.println();
    		}
    		long sample = ((long)(double)samples[k]);
    		out.print(sample + "\t");
    		biceps.log(sample, out);
    		out.println();
        	k++;
        }
        
        
        
        if (outputInput.get() != null) {
        	out.close();
        }
        Log.warning.println("Done.");
	}

	
	
	
	private int indexOf(List<String> labels, String string) {
		for (int i = 0 ;i < labels.size(); i++) {
			if (labels.get(i).equals(string)) {
				return i;
			}
		}
		return -1;
	}

	public static void main(String[] args) throws Exception {
		new Application(new BICEPSPopulationLogProducer(), "BICEPSPopulationLogProducer", args);
	}

}
