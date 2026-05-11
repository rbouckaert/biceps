package biceps.spec;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Test;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;
import junit.framework.TestCase;
import biceps.BEASTTestCase;

public class BICEPSTest extends TestCase {
	
	@Test
	public void testBICEPSLogLikelihood() throws Exception {
		Alignment data = BEASTTestCase.getAlignment();
	    Tree tree = BEASTTestCase.getTree(data);
		TreeIntervals intervals = new TreeIntervals(tree);
		BICEPS biceps = new BICEPS();
		biceps.initByName("linkedMean", true, 
				"ploidy", "1", 
				"populationShape", new RealScalarParam<PositiveReal>(3.0, PositiveReal.INSTANCE), 
				"populationMean", new RealScalarParam<PositiveReal>(1.0, PositiveReal.INSTANCE), 
				"groupCount", 2,
//				"groupSizes", "3 2",
				"treeIntervals", intervals);
		
		double logP = biceps.calculateLogP();
		System.err.println(logP);
		assertEquals(2.959105012256057, logP);
		
		// test logger
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(baos);
		Randomizer.setSeed(127);
		biceps.log(0l, out);
		String log = baos.toString();
		System.err.println(log);
		assertEquals2("2.959105012256057	0.29824097802198085	0.2509280634598848	3	2	", log);
	}

	void assertEquals2(String s1, String s2) {
		System.out.println("Exected : " + s1);
		System.out.println("Obtained: " + s2);
	}
	
	
	@Test
	public void testBICEPSLogLikelihoodEqualEpochs() throws Exception {
		Alignment data = BEASTTestCase.getAlignment();
	    Tree tree = BEASTTestCase.getTree(data);
		TreeIntervals intervals = new TreeIntervals(tree);
		BICEPS biceps = new BICEPS();
		biceps.initByName("linkedMean", true, 
				"ploidy", "1", 
				"populationShape", new RealScalarParam<PositiveReal>(3.0, PositiveReal.INSTANCE), 
				"populationMean", new RealScalarParam<PositiveReal>(1.0, PositiveReal.INSTANCE), 
				"groupCount", 2,
				"equalEpochs", true,
//				"groupSizes", "3 2",
				"treeIntervals", intervals);
		
		double logP = biceps.calculateLogP();
		System.err.println(logP);
		assertEquals(2.251250195788688, logP);
		
		// test logger
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(baos);
		Randomizer.setSeed(127);
		biceps.log(0l, out);
		String log = baos.toString();
		System.err.println(log);		
		assertEquals2("2.251250195788688	0.28687352299727964	0.21827255611296403	0.049791	0.049791	", log);		
	}
}
