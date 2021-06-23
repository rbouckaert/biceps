package biceps.tools;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import beast.app.treeannotator.TreeAnnotator;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.evolution.tree.Tree;
import beast.evolution.tree.coalescent.IntervalType;
import beast.evolution.tree.coalescent.TreeIntervals;

@Description("Produce trace log with epoch (tree length) statistics for a tree file")
public class EpochStats extends Runnable {
	final public Input<Integer> groupCountInput = new Input<>("groupCount", "number of groups used to split up tree", 10);
	final public Input<TreeFile> treeInput = new Input<>("trees", "file containing tree set", new TreeFile("[[none]]"));
	final public Input<OutFile> outputInput = new Input<>("out", "trace output file that can be processed in Tracer or stdout if not specified.",
			new OutFile("[[none]]"));
	

	@Override
	public void initAndValidate() {}


	@Override
	public void run() throws Exception {
		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			 out = new PrintStream(outputInput.get());
			 Log.warning("Writing to " + outputInput.get().getPath());
		}		
		
		int groupCount = groupCountInput.get();
		out.print("Sample\t");
		for (int i = 0; i < groupCount; i++) {
			out.print("group" + i + "\t");
		}
		out.println();
		
		int[] groupSizes = null;
		
		
		beast.app.treeannotator.TreeAnnotator.MemoryFriendlyTreeSet srcTreeSet = new TreeAnnotator(). new MemoryFriendlyTreeSet(treeInput.get().getPath(), 0);
		srcTreeSet.reset();
		int k = 0;
		while (srcTreeSet.hasNext()) {
			Tree tree = srcTreeSet.next();
			TreeIntervals intervals = new TreeIntervals(tree);
			if (groupSizes == null) {
				groupSizes = getGroupSizes(tree.getLeafNodeCount(), groupCount);
			}
			
	        int groupIndex = 0;
	        int subIndex = 0;

	        List<Integer> lineageCounts = new ArrayList<>();
	        List<Double> intervalSizes = new ArrayList<>();
	        out.print(k+"\t");
	        for (int j = 0; j < intervals.getIntervalCount(); j++) {
	            lineageCounts.add(intervals.getLineageCount(j));
	            intervalSizes.add(intervals.getInterval(j));
	            if (intervals.getIntervalType(j) == IntervalType.COALESCENT) {
	                subIndex += 1;
	            }
	            if (subIndex >= groupSizes[groupIndex]) {
	            	
	            	double length = calcLength(lineageCounts, groupSizes[groupIndex], intervalSizes);
	                groupIndex += 1;
	                subIndex = 0;
	                lineageCounts.clear();
	                intervalSizes.clear();
	    	        out.print(length+"\t");
	            }
	        }
	        out.println();
	        k++;
		}
		
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			out.close();
		}
		Log.warning("Done!");
	}

	
	
    private double calcLength(
    		List<Integer> lineageCounts, 
    		int eventCounts,
    		List<Double> intervalSizes 
    		) {

        double length = 0.0;
        // contributions of intervals
        for (int i = 0; i < lineageCounts.size(); i++) {
        	length += intervalSizes.get(i) * lineageCounts.get(i);
        }
        return length;
    }
	
	private int[] getGroupSizes(int leafNodeCount, int groupCount) {
		int events = leafNodeCount - 1;
		int eventsEach = events / groupCount;
        int eventsExtras = events % groupCount;
        int[] groupSizes = new int[groupCount];
        for (int i = 0; i < groupCount; i++) {
            if (i < eventsExtras) {
            	groupSizes[i] = eventsEach + 1;
            } else {
            	groupSizes[i] = eventsEach;
            }
        }
		return groupSizes;
	}


	public static void main(String[] args) throws Exception {
		new Application(new EpochStats(), "Epoch stats", args);
	}
}
