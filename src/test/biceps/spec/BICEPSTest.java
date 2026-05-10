package test.biceps.spec;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Test;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;
import biceps.spec.BICEPS;
import junit.framework.TestCase;
import test.beast.BEASTTestCase;

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
		Randomizer.setSeed(127);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(baos);
		biceps.log(0l, out);
		String log = baos.toString();
		System.err.println(log);
		assertEquals("2.959105012256057	0.2493291107385713	0.26862164687420126	3	2	", log);		
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
		Randomizer.setSeed(127);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(baos);
		biceps.log(0l, out);
		String log = baos.toString();
		System.err.println(log);		
		assertEquals("2.251250195788688	0.20735830786907636	0.24338770941064886	0.049791	0.049791	", log);		
	}
}
