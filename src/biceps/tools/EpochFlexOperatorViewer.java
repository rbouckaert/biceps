package biceps.tools;


import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;

import beast.app.util.Utils;
import beast.core.BEASTInterface;
import beast.core.Description;
import beast.core.Input;
import beast.evolution.tree.Node;
import beast.util.Randomizer;

@Description("Visualises epoch flex operator")
public class EpochFlexOperatorViewer extends JPanel implements BEASTInterface {
	private static final long serialVersionUID = 1L;

	static int N = 6; // number of sliders
	JPanel treePanel;
	ControlPanel controlPanel;
	OperatorPanel operatorPanel;
	//Tree tree;
	Node root, scaledroot;
	Node [] nodes, scalednodes;
	
	boolean isAnimating = false;
	boolean isSequential = false;
	int [] animationState;
	JButton animationButton;

	JButton screenShotButton;
	int screenShotCount = 0;
	
	public EpochFlexOperatorViewer() {	
	}
	
	public EpochFlexOperatorViewer(String startValues) {
		initialise(startValues);
		
		animationState = new int[N];
		//Arrays.fill(animationState, 1);
	}
	
	protected void initialise(String startValues) {
		setLayout(new BorderLayout());
		controlPanel = new ControlPanel(startValues);
		add(controlPanel, BorderLayout.WEST);
		treePanel = new TreePanel();
		add(treePanel, BorderLayout.CENTER);
	}
	
	public class TreePanel extends JPanel {
		private static final long serialVersionUID = 1L;

		public TreePanel() {
			super();
			setToolTipText("<html>" + getDescription().replaceAll("\n", "<br>") + "</html>");
		}
		
		private void drawString(Graphics g, String text, int x, int y) {
	        for (String line : text.split("\n"))
	            g.drawString(line, x, y += g.getFontMetrics().getHeight());
	    }
		
		@Override
		protected void paintComponent(Graphics g) {
			int w = getWidth();
			int h = getHeight();
			g.setColor(Color.white);
			g.fillRect(0, 0, w, h);

			
			operatorPanel.paint(g, w, h);
			
			g.setColor(Color.black);
			if (root == null) {
				String description = getDescription();
				g.setFont(g.getFont().deriveFont(20f));
				drawString(g, description.equals("Not documented!!!") ? "no tree" : description, w/10, h/2);
			} else {
				
				g.setColor(new Color(0,0,255, 50));
				paint(nodes, root, w, h, g);
				g.setColor(new Color(0,0,255, 255));
				paint(scalednodes, scalednodes[N*2+1], w, h, g);
				


				g.setColor(Color.black);
				for (int i = 0; i <= N; i++) {
					g.drawString("" + (char)(65+i), i * (this.getWidth()-20) / N, this.getHeight() - 5);
				}
				
				
			}
		}
		
		
		private void paint(Node[] nodes, Node root, int w, int h, Graphics g) {
			double [] x = new double[N*2+2];
			int leafCount = N+1;
			for (int i = 0; i < leafCount; i++) {
				//if (!isSequential || nodes[i].getLength() > 1e-10) {
				if (nodes[i].getLength() > 1e-10) {
					x[i] = i/(leafCount - 1.0);
				} else {
					int closest = findClosestNonZeroNode(i);
					x[i] = closest/(leafCount - 1.0);						
//					x[i] = i/(leafCount - 1.0);
				}
			}
//			if (isSequential) {
//				for (int i = 0; i < leafCount; i++) {
//					if (nodes[i].getLength() < 1e-10) {
//						x[i] = N/(leafCount - 1.0);
//						break;
//					}
//				}
//			}
			x[x.length-1] = 0.5;
			setX(root, x, null);
			// g.setColor(Color.blue);

			double [] x2 = new double[N*2+2];
			System.arraycopy(x, 0, x2, 0, x2.length);
			//for (int i = 0; i < 10; i++) {
			traverse2(root, x, x2);
			//	System.arraycopy(x2, 0, x, 0, x2.length);
			//}
			x2[x2.length - 1] = x2[root.getChild(0).getNr()];
			
			Graphics2D g2 = (Graphics2D) g;
			g2.setStroke(new BasicStroke(1.0f));
			traverse(root, g, w, h, x2);
			
			g2.setStroke(new BasicStroke(3.0f));
			Node current = nodes[0];
			List<Node> path = new ArrayList<>(); 
			// path down to node 1
			while (current != root) {
				int i = current.getNr();
				int j = current.getParent().getNr();
				double hn = current.getHeight();
				double hp = current.getParent().getHeight();
				g.drawLine((int)(x2[i] * w)-5, (int)(h-hn * h), (int)(x2[j] * w)-5, (int)(h-hp * h));
				current = current.getParent();
				path.add(current);
			}
			for (int i = 1; i <= N; i++) {
				current = nodes[i];
				List<Node> path2 = new ArrayList<>();
				// path down to next leaf
				while (!path.contains(current)) {
					int k = current.getNr();
					int j = current.getParent().getNr();
					double hn = current.getHeight();
					double hp = current.getParent().getHeight();
					int X1 = (int)(x2[k] * w)-5;
					int Y1 = (int)(h-hn * h);
					int X2 = (int)(x2[j] * w)-5;
					int Y2 = (int)(h-hp * h);
					if (path.contains(current.getParent())) {
						// cull last few pixels from line
						double a = Math.atan2(Y2-Y1, X2 - X1);
						X2 = (int)(X2 - Math.cos(a) * 15);
						Y2 = (int)(Y2 - Math.sin(a) * 15);
					}
					if (Y1 != Y2)
						g.drawLine(X1, Y1, X2, Y2);							
					current = current.getParent();
					path2.add(current);
				}

				// path up from previous leaf
				Node other = nodes[i-1];
				while (other != current) {
					int k = other.getNr();
					int j = other.getParent().getNr();
					double hn = other.getHeight();
					double hp = other.getParent().getHeight();
					int X1 = (int)(x2[k] * w)+5;
					int Y1 = (int)(h-hn * h);
					int X2 = (int)(x2[j] * w)+5;
					int Y2 = (int)(h-hp * h);
					if (current == other.getParent()) {
						// cull last few pixels from line
						double a = Math.atan2(Y2-Y1, X2 - X1);
						X2 = (int)(X2 - Math.cos(a) * 15);
						Y2 = (int)(Y2 - Math.sin(a) * 15);
					}
					//if (Y1 != Y2)
						//g.drawLine(X1, Y1, X2, Y2);							

					other = other.getParent();						
				}
				
				while (current != root) {
					current = current.getParent();
					path2.add(current);
				}
				path = path2;
			}
			
			
			for (int i = 0; i < N; i++) {
				current = nodes[i];
				int k = current.getNr();
				int j = current.getParent().getNr();
				double hn = current.getHeight();
				double hp = current.getParent().getHeight();
				int X1 = (int)(x2[k] * w)+5;
				int Y1 = (int)(h-hn * h);
				int X2 = (int)(x2[j] * w)+5;
				int Y2 = (int)(h-hp * h);
				double a = Math.atan2(Y2-Y1, X2 - X1);
				
//				g.setColor(Color.red);
//				g2.drawArc((int)(x2[k] * w)-5, h-10, 10, 10, (int)(-180*a/Math.PI)-90, -180);//(int)(-180*a/Math.PI) + 180, (int)(-180*a/Math.PI));
			}
			
		}

		private void traverse2(Node node, double[] x, double[] x2) {
			int i = node.getNr();
			if (node.isLeaf()) {
				x2[i] = x[i];
			} else {
				double maxHeight = 0;
				for (Node child : node.getChildren()) {
				//	traverse2(child, x, x2);
					if (child.getHeight() > maxHeight) {
						maxHeight = child.getHeight();
					}
				}
				if (!node.isRoot()) {
					int p = node.getParent().getNr();
					double w = (node.getParent().getHeight() - maxHeight) > 1e-10 ?
							(node.getHeight()-maxHeight) / (node.getParent().getHeight() - maxHeight) :
							1;
					x2[i] = (1-w) * x[i] + w*x2[p];
				}
				for (Node child : node.getChildren()) {
					traverse2(child, x, x2);
				}
			}
		}


		private void traverse(Node node, Graphics g, int scaleX, int scaleY, double [] x) {
			int i = node.getNr();
			if (!node.isRoot()) {
				int j = node.getParent().getNr();
				double h = node.getHeight();
				double hp = node.getParent().getHeight();
				g.drawLine((int)(x[i] * scaleX), (int)(scaleY-h * scaleY), (int)(x[j] * scaleX), (int)(scaleY-hp * scaleY));
			}
			if (!node.isLeaf()) {
				for (Node child : node.getChildren()) {
					traverse(child, g, scaleX, scaleY, x);
				}
			}
		}


		private int setX(Node node, double [] x, double [] sumLeafX) {
			if (node.isLeaf()) {
				sumLeafX[0] = x[node.getNr()];
				return 1;
			} else {
				int leafCount = 0;
				double sumX = 0;
				for (Node child : node.getChildren()) {
					double [] s = new double[1];
					leafCount += setX(child, x, s);
					sumX += s[0];
				}
				x[node.getNr()] = sumX / leafCount;
				if (sumLeafX != null) {
					sumLeafX[0] = sumX;
				}
				return leafCount;
			}
		}
		
	}
	
	public class OperatorPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		
		JSlider scaleslider, lowerslider, upperslider;
		
		public OperatorPanel() {
		}
		
		public void init() {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			JPanel sliderPanel = new JPanel(); 
			sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.X_AXIS));
			sliderPanel.add(new JLabel("lower"));
			lowerslider = new JSlider(0, 100, 0);
			lowerslider.addChangeListener(new ChangeListener() {				
				@Override
				public void stateChanged(ChangeEvent e) {
					controlPanel.updateTree();
				}
			});
			sliderPanel.add(lowerslider);
			add(sliderPanel);
			
			sliderPanel = new JPanel();
			sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.X_AXIS));
			sliderPanel.add(new JLabel("upper"));
			upperslider = new JSlider(0, 100, 0);
			upperslider.addChangeListener(new ChangeListener() {				
				@Override
				public void stateChanged(ChangeEvent e) {
					controlPanel.updateTree();
				}
			});
			sliderPanel.add(upperslider);
			add(sliderPanel);
			
			sliderPanel = new JPanel();
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
			g.setColor(new Color(0,0,255, 50));
			int h1 = (int)(h * (operatorPanel.lowerslider.getValue()/100.0));
			int h2 = (int)(h * (operatorPanel.upperslider.getValue()/100.0));
			
			g.fillRect(0, h-h2, 
					w, h2 - h1);

		}
	}
	
	public class ControlPanel extends JPanel {
		private static final long serialVersionUID = 1L;

		JSlider [] slider;
		JSlider [] leafslider;
		JSlider progress;
		
		public ControlPanel(String startValues) {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			
			slider = new JSlider[N];
			int [] start = new int[N];
			if (startValues != null) {
				String [] strs = startValues.split(",");
				for (int i = 0; i < N; i++) {
					start[i] = Integer.parseInt(strs[i]);
				}
			} else {
				for (int i = 0; i < N; i++) {
					start[i] = Randomizer.nextInt(100);
				}
			}
			for (int i = 0; i < N; i++) {
				slider[i] = new JSlider(0, 100, start[i]);
				slider[i].addChangeListener(new ChangeListener() {				
					@Override
					public void stateChanged(ChangeEvent e) {
						updateTree();
					}
				});
				JPanel sliderPanel = new JPanel();
				sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.X_AXIS));
				sliderPanel.add(new JLabel("" + (char)(65+i) + (char)(66+i)));
				sliderPanel.add(slider[i]);
				add(sliderPanel);
			}
			JPanel animationPanel = new JPanel();
			animationButton = new JButton("Start");
			animationButton.addActionListener(new ActionListener() {
				
				@Override
				public void actionPerformed(ActionEvent e) {
					if (isAnimating) {
						animationButton.setText("Start");
						isAnimating = false;
						animationState = new int[N];
						//Arrays.fill(animationState, 1);
					} else {
						animationButton.setText("Stop");
						isAnimating = true;
						new Thread() {
							public void run() {
								updateAnimationStep();								
							};
						}.start();
					}
					
				}
			});
			animationPanel.add(animationButton);
			
			screenShotButton = new JButton("Screen shot");
			screenShotButton.addActionListener(new ActionListener() {
				
				@Override
				public void actionPerformed(ActionEvent e) {
					BufferedImage bi;
					Graphics g;
					bi = new BufferedImage(treePanel.getWidth(), treePanel.getHeight(), BufferedImage.TYPE_INT_RGB);
					g = bi.getGraphics();
					g.setPaintMode();
					g.setColor(getBackground());
					g.fillRect(0, 0, treePanel.getWidth(), treePanel.getHeight());
					treePanel.printAll(g);
					String fileName = "/tmp/cube-shot";
					int k = screenShotCount++;
					try {
						ImageIO.write(bi, "png", new File(fileName +
								(k<10?"00":(k<100?"0":"")) +
								k + ".png"));
					} catch (Exception ex) {
						JOptionPane.showMessageDialog(null,
								fileName + " was not written properly: " + ex.getMessage());
						ex.printStackTrace();
					}
				}
			});
			animationPanel.add(screenShotButton);
			
			JCheckBox toggleSequential = new JCheckBox("sequential");
			toggleSequential.setSelected(isSequential);
			toggleSequential.addActionListener(new ActionListener() {
				
				@Override
				public void actionPerformed(ActionEvent e) {
					isSequential = toggleSequential.isSelected();
					treePanel.repaint();
				}
			});
			animationPanel.add(toggleSequential);
			add(animationPanel);

			add(new JSeparator(JSeparator.HORIZONTAL));
			
			
			
			leafslider = new JSlider[N+1];
			for (int i = 0; i < N+1; i++) {
				leafslider[i] = new JSlider(0, 100, 0);
				leafslider[i].addChangeListener(new ChangeListener() {				
					@Override
					public void stateChanged(ChangeEvent e) {
						updateTree();
					}
				});
				JPanel sliderPanel = new JPanel();
				sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.X_AXIS));
				sliderPanel.add(new JLabel("" + (char)(65+i)));
				sliderPanel.add(leafslider[i]);
				add(sliderPanel);
			}
			
			add(new JSeparator(JSeparator.HORIZONTAL));

			operatorPanel = newOperatorPanel();
			add(operatorPanel);

			add(new JSeparator(JSeparator.HORIZONTAL));
			
			exportPDF = new JButton("To pdf");
			exportPDF.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					File file = Utils.getSaveFile("Export PDF to...");
					exportPDF(file.getPath());
				}
			});
			add(exportPDF);

			progress = new JSlider(0, 1000, 1000);
			progress.addChangeListener(new ChangeListener() {				
				@Override
				public void stateChanged(ChangeEvent e) {
					processProgress();
				}

			});
			add(progress);
		}
		
		JButton exportPDF;
		
		private void processProgress() {
			int p = progress.getValue();
			int sum = 0;
			for (int i = 0; i < N; i++) {
				if (isSequential) {
					sum += slider[i].getValue();
				} else {
					sum += slider[i].getMaximum() - slider[i].getValue();
				}
			}
			int target = sum * p / progress.getMaximum();
			System.err.println("sum: " + sum + " target: " + target);
			if (target == 0) {
				target = 1;
			}
			isAnimating = true;
			
			int [] values = new int [N];
			for (int i = 0; i < N; i++) {
				values[i] = slider[i].getValue();
			}

			int k = 0;
			animationState = new int[N];
			while (k < target) {
				doOneAnimationStep(values);
				k += 1;
			}
			int [] leafvalues = new int[N+1];
			for (int i = 0; i < N+1; i++) {
				leafvalues[i] = leafslider[i].getValue();
			}
			updateTree(animationState, leafvalues);
		}
		
		private void doOneAnimationStep(int [] values) {
			if (isSequential) {
				int iMin = -1;
				int v = Integer.MAX_VALUE;
				for (int i = 0; i < N; i++) {
					if (values[i] > animationState[i] && values[i] < v) {
						v = values[i];
						iMin = i;
						if (isSequential) {
							break;
						}
					}
				}
				if (iMin == -1) {
					isAnimating = false;
					animationState = new int [N];
					//Arrays.fill(animationState, 1);
					animationButton.setText("start");
					return;
				}
				animationState[iMin]++;
			} else {
				boolean isInit = true;
				for (int i = 0; i < N; i++) {
					if (animationState[i] != 0) {
						isInit = false;
						break;
					}
				}
				if (isInit) {
					Arrays.fill(animationState, 100);
				}
				int iMin = -1;
				int v = Integer.MAX_VALUE;
				for (int i = 0; i < N; i++) {
					if (values[i] < animationState[i] && values[i] < v) {
						v = values[i];
						iMin = i;
						if (isSequential) {
							break;
						}
					}
				}
				if (iMin == -1) {
					isAnimating = false;
					animationState = new int [N];
					//Arrays.fill(animationState, 1);
					animationButton.setText("start");
					return;
				}
				animationState[iMin]--;					
			}
		}
		
		
		public void updateTree() {
			int [] values = new int [N];
			for (int i = 0; i < N; i++) {
				values[i] = slider[i].getValue();
			}
			int [] leafvalues = new int [N+1];
			for (int i = 0; i < N+1; i++) {
				leafvalues[i] = leafslider[i].getValue();
			}
			System.out.println(Arrays.toString(values));
			isAnimating = false;
			updateTree(values, leafvalues);
			progress.setValue(progress.getMaximum());
		}
		
		public void updateAnimationStep() {
			int [] values = new int [N];
			for (int i = 0; i < N; i++) {
				values[i] = slider[i].getValue();
			}

			int [] leafvalues = new int[N+1];
			for (int i = 0; i < N+1; i++) {
				leafvalues[i] = leafslider[i].getValue();
			}

			int k = 0;
			String sFileName = "/tmp/tree-";
			while (isAnimating) {
				doOneAnimationStep(values);
				
				updateTree(animationState, leafvalues);
				
				BufferedImage bi;
				Graphics g;
				bi = new BufferedImage(treePanel.getWidth(), treePanel.getHeight(), BufferedImage.TYPE_INT_RGB);
				g = bi.getGraphics();
				g.setPaintMode();
				g.setColor(getBackground());
				g.fillRect(0, 0, treePanel.getWidth(), treePanel.getHeight());
				treePanel.printAll(g);
				try {
//					ImageIO.write(bi, "png", new File(sFileName +
//							(k<10?"00":(k<100?"0":"")) +
//							k + ".png"));
				} catch (Exception e) {
					JOptionPane.showMessageDialog(null,
							sFileName + " was not written properly: " + e.getMessage());
					e.printStackTrace();
				}
				k++;
				
//				try {
//					Thread.sleep(10);
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
			}
		}

		public void updateTree(int [] values, int [] leafvalues) {
			nodes = new Node[N*2+2];
			scalednodes = new Node[N*2+2];
			for (int i = 0; i < nodes.length; i++) {
				nodes[i] = new Node();
				nodes[i].setNr(i);
				nodes[i].setHeight(0);
				
				scalednodes[i] = new Node();
				scalednodes[i].setNr(i);
				scalednodes[i].setHeight(0);				
			}
			
			root = nodes[N*2+1];
			root.setHeight(1);
			root.addChild(nodes[0]);

			Node current = nodes[0];
			int next = N + 1;
			for (int i = 0; i < N; i++) {
				double target = values[i] / 100.0;
				while (target > current.getParent().getHeight()) {
					current = current.getParent();
				}
				Node parent = current.getParent();
				parent.removeChild(current);
				parent.addChild(nodes[next]);
				nodes[next].addChild(current);
				nodes[next].addChild(nodes[i+1]);
				nodes[next].setHeight(target);
				current = nodes[i+1];
				next++;
			}
			
			

			Node scaledroot = root.copy();
			collectNodes(scaledroot, scalednodes);

			
			transformNodes(leafvalues);
			
			System.out.print(root.toNewick());
			treePanel.repaint();
			
		}


		private void collectNodes(Node node, Node[] scalednodes) {
			scalednodes[node.getNr()] = node;
			for (Node child : node.getChildren()) {
				collectNodes(child, scalednodes);
			}			
		}
	}
	
	
	protected OperatorPanel newOperatorPanel() {
		OperatorPanel panel = new OperatorPanel();
		panel.init();
		return panel;
	}
	
	protected void transformNodes(int [] leafvalues) {
		double lower = (operatorPanel.lowerslider.getValue()/100.0);
		double upper = (operatorPanel.upperslider.getValue()/100.0);
		double scale = 1+(operatorPanel.scaleslider.getValue()/100.0);
		double to = lower + scale * (upper - lower);
		double delta = to-upper;
		System.out.println("lower=" + lower + " upper=" + upper + " scale=" + scale);
		for (Node node : scalednodes) {
			double h = node.getHeight() ;
			if (h > lower) {
				if (h > upper) {
					node.setHeight(h + delta);
				} else {
					node.setHeight(lower + scale * (h-lower));
				}
			}
		}
		for (int i = 0; i < leafvalues.length; i++) {
			nodes[i].setHeight(leafvalues[i] / 100.0);
			scalednodes[i].setHeight(leafvalues[i] / 100.0);
		}
	}

	public int findClosestNonZeroNode(int i) {
		int j = nodes[i].getParent().getNr();
		while (nodes[j].getHeight() < 1e-10) {
			j = nodes[j].getParent().getNr();
		}
		int k = 10 * N;
		for (Node node : nodes[j].getAllLeafNodes()) {
			if (node.getLength() > 1e-10 && Math.abs(node.getNr() - i) < Math.abs(k)) {
				k = node.getNr() - i;
			}
		}
		
		// if a suitable candidate was found, k != 10 * N 
		if (k == 10 * N) {
			// no suitable candidate was found
			// find left and right most leafs of left & right child of node j
			int iMin = N;
			int iMax = 0;
			for (Node node : nodes[j].getLeft().getAllLeafNodes()) {
				iMin = Math.min(node.getNr(), iMin);
				iMax = Math.max(node.getNr(), iMax);
			}
			int [] result = new int[4];
			result[0] = iMin;
			result[1] = iMax;
			iMin = N;
			iMax = 0;
			for (Node node : nodes[j].getRight().getAllLeafNodes()) {
				iMin = Math.min(node.getNr(), iMin);
				iMax = Math.max(node.getNr(), iMax);
			}
			result[2] = iMin;
			result[3] = iMax;
			Arrays.sort(result);
			k = result[1] - i;
			if (Math.abs(result[2] - i) < Math.abs(k)) {
				k = result[2] - i;
			}
			return i + k;
		}
		
		if (k < 0) {
			k++;
		} else if (k > 0) {
			k--;
		}
		return i + k;
	}

	@Override
	public void initAndValidate() {	}

	@Override
	public String getID() {	return null; }

	@Override
	public void setID(String ID) {	}

	@Override
	public Set<BEASTInterface> getOutputs() { return null; }

	@Override
	public Map<String, Input<?>> getInputs() { return null; }
	
	@Override
	public String getDescription() {		
		String description = BEASTInterface.super.getDescription();
		if (!description.contains("\n")) {
			for (int i = 60; i < description.length(); i += 60) {
				while (i < description.length() && !Character.isSpaceChar(description.charAt(i))) {
					i++;
				}
				if (i < description.length()) {
					description = description.substring(0, i) + "\n" + description.substring(i);						
				}				
			}
		}
		return description;
	}

	
	protected void exportPDF(String sFileName) {
		try {
			com.itextpdf.text.Document doc = new com.itextpdf.text.Document();
			PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(sFileName));
			doc.setPageSize(new com.itextpdf.text.Rectangle(treePanel.getWidth(), treePanel.getHeight()));
			doc.open();
			PdfContentByte cb = writer.getDirectContent();
			Graphics2D g = new PdfGraphics2D(cb, treePanel.getWidth(), treePanel.getHeight());
			 
			//BufferedImage bi;
			//bi = new BufferedImage(m_Panel.getWidth(), m_Panel.getHeight(), BufferedImage.TYPE_INT_RGB);
			//g = bi.getGraphics();
			g.setPaintMode();
			g.setColor(getBackground());
			g.fillRect(0, 0, treePanel.getWidth(), treePanel.getHeight());
			treePanel.paint(g);
			//m_Panel.printAll(g);
		
			g.dispose();
			doc.close();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(treePanel, "Export may have failed: " + e.getMessage());
		}
	}

	public static void main(String[] args) {
		JFrame frame = new JFrame();
		frame.setSize(1024, 768);
		EpochFlexOperatorViewer viewer = new EpochFlexOperatorViewer(args.length > 0 ? args[0] : null);
		frame.add(viewer);
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

}
