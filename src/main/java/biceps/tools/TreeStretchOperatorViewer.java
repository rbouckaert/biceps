package biceps.tools;

import java.awt.Graphics;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import beast.base.core.Description;
import beast.base.evolution.tree.Node;

@Description("Visualise the tree stretch operator")
public class TreeStretchOperatorViewer extends EpochFlexOperatorViewer {
	private static final long serialVersionUID = 1L;

	public TreeStretchOperatorViewer() {	
	}
	
	public TreeStretchOperatorViewer(String startValues) {
		super(startValues);
	}

	@Override
	protected OperatorPanel newOperatorPanel() {
		OperatorPanel panel = new SkewedScaleOperatorPanel();
		panel.init();
		return panel;
	}
	
	@Override
	protected void transformNodes(int [] leafvalues) {
		for (int i = 0; i < leafvalues.length; i++) {
			nodes[i].setHeight(leafvalues[i] / 100.0);
			scalednodes[i].setHeight(leafvalues[i] / 100.0);
		}

		Node root = scalednodes[scalednodes.length - 1];
		double [] oldLengths = new double[scalednodes.length - 1];
		for (int i = 0; i < scalednodes.length - 1; i++) {
			oldLengths[i] = scalednodes[i].getLength();
		}
		double scale = 1+(operatorPanel.scaleslider.getValue()/100.0);
		scale(root, scale, oldLengths);
	}
	
	
	private void scale(Node node, double scale, double [] oldLengths) {
		if (!node.isLeaf()) {
			for (Node child : node.getChildren()) {
				scale(child, scale, oldLengths);
			}
			Node left = node.getLeft();
			double h1 = left.getHeight() + oldLengths[left.getNr()] * scale;
			Node right = node.getRight();
			if (right != null) {
				double h2 = right.getHeight() + oldLengths[right.getNr()] * scale;
				//h2 = Math.max(h1, h2);
				h2 = (h1+h2)/2;
				node.setHeight(h2);
			} else {
				node.setHeight(h1);
			}
		}
	}


	public class SkewedScaleOperatorPanel extends OperatorPanel {
		private static final long serialVersionUID = 1L;
		
		public SkewedScaleOperatorPanel() {
		}
		
		@Override
		public void init() {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			JPanel sliderPanel = new JPanel(); 			
			sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.X_AXIS));
			sliderPanel.add(new JLabel("scale"));
			scaleslider = new JSlider(-100, 100, 0);
			scaleslider.addChangeListener(new ChangeListener() {				
				@Override
				public void stateChanged(ChangeEvent e) {
					controlPanel.updateTree();
				}
			});
			sliderPanel.add(scaleslider);
			add(sliderPanel);
		}
		
		public void paint(Graphics g, int w, int h) {
		}
	}
	
	
	public static void main(String[] args) {
		JFrame frame = new JFrame();
		frame.setSize(1024, 768);
		TreeStretchOperatorViewer viewer = new TreeStretchOperatorViewer(args.length > 0 ? args[0] : null);
		frame.add(viewer);
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);		
	}
}
