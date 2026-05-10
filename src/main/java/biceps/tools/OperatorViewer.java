package biceps.tools;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import beast.base.core.Description;
import beast.base.evolution.tree.Node;
import beastfx.app.util.FXUtils;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.stage.Stage;

@Description("Visualises epoch flex operator")
public class OperatorViewer extends Application { 
	@Override
	public void start(Stage stage) {
	
	    initUI(stage);
	}
	
	Canvas canvas;
	Slider slider;

	int N = 0;
	Node root, scaledroot;
	Node [] nodes, scalednodes;

	private void initUI(Stage stage) {
	
		Pane root = new Pane();
	
		
		VBox box = FXUtils.newVBox();
		
		canvas = new Canvas(1024, 700);
	    
	    Button nextButton = new Button("next");
	    nextButton.setOnAction(e->{
	    	current++;
	    	if (current == gapValue.length) {
	    		current = 0;
	    	}
	    	N = gapValue[current].length;
	    	updateTree();
	    });
	    Button prevButton = new Button("prev");
	    prevButton.setOnAction(e->{
	    	current--;
	    	if (current == -1) {
	    		current = gapValue.length - 1;
	    	}
	    	N = gapValue[current].length;
	    	updateTree();
	    });
	    HBox buttonBox = new HBox();
	    buttonBox.getChildren().addAll(prevButton, nextButton);
	    slider = new Slider();
	    slider.setMin(-5.0);
	    slider.setMax(5.0);
	    slider.setValue(0.0);
	    slider.setShowTickLabels(true);
	    slider.setShowTickMarks(true);
	    slider.setMajorTickUnit(50);
	    slider.setMinorTickCount(5);
	    slider.setBlockIncrement(10);
	    slider.valueProperty().addListener(new ChangeListener<Number>() {
            public void changed(ObservableValue<? extends Number> ov,
                    Number old_val, Number new_val) {
        		updateTree();
                }

            });
	    box.getChildren().addAll(canvas, slider, buttonBox);
	    
	    
	    root.getChildren().add(box);
	
	    Scene scene = new Scene(root, 1024, 768, Paint.valueOf("white"));
	
	    stage.setTitle("OperatorViewer");
	    stage.setScene(scene);
	    stage.show();

	    updateTree();
	}

	
	double lower = 20/100.0;
	double upper = 35/100.0;

	int current = 4;
	int [][] gapValue = new int[][] {
		{35, 50, 25, 10},
		{35, 50, 25, 10},
		{35, 50, 25, 10},
		{35, 50, 25, 10},
		{35, 50, 25, 10, 40, 30, 20, 10},
		{35, 50, 25, 10, 40, 30, 20, 10},
	};
	int [][] leafValue = new int[][] {
		{0, 0, 0, 0, 0},
		{32, 32, 0, 0, 0, 0},
		{32, 32, 0, 0, 0, 0},
		{0, 0, 0, 0, 0},
		{0, 0, 0, 0, 0, 0, 0, 0, 0},
		{20, 20, 0, 0, 0, 0, 0, 0, 0},
	};
	int [] scaleType = {0, 0, 1, 1, 2, 2};
	
	public void updateTree() {
		int [] values = new int [N];
		for (int i = 0; i < N; i++) {
			values[i] = gapValue[current][i];
		}
		int [] leafvalues = new int [N+1];
		for (int i = 0; i < N+1; i++) {
			leafvalues[i] = leafValue[current][i];
		}
		System.out.println(Arrays.toString(values)+ " " + Arrays.toString(leafvalues));
		updateTree(values, leafvalues);
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
		Platform.runLater(() -> {
			drawLines();
		});
		
	}

	protected void transformNodes(int [] leafvalues) {
		for (int i = 0; i < leafvalues.length; i++) {
			nodes[i].setHeight(leafvalues[i] / 100.0);
			scalednodes[i].setHeight(leafvalues[i] / 100.0);
		}

		double scale = Math.exp(slider.getValue()); 
		switch(scaleType[current]) {
		case 0:
			for (Node node : scalednodes) {
				node.setHeight(node.getHeight()*scale);
			}
			break;
		case 1:
			Node root = scalednodes[scalednodes.length - 1];
			double [] oldLengths = new double[scalednodes.length - 1];
			for (int i = 0; i < scalednodes.length - 1; i++) {
				oldLengths[i] = scalednodes[i].getLength();
			}
			stretch(root, scale, oldLengths);
			break;
		case 2:
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
			
			break;
		}
		for (int i = 0; i < leafvalues.length; i++) {
			scalednodes[i].setHeight(leafvalues[i] / 100.0);
		}		
	}
	
	
	
	private void stretch(Node node, double scale, double [] oldLengths) {
		if (!node.isLeaf()) {
			for (Node child : node.getChildren()) {
				stretch(child, scale, oldLengths);
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

	private void collectNodes(Node node, Node[] scalednodes) {
		scalednodes[node.getNr()] = node;
		for (Node child : node.getChildren()) {
			collectNodes(child, scalednodes);
		}			
	}
	private void scale(Number new_val) {
		Platform.runLater(() -> {
			drawLines();
		});
	}
	
	private void drawLines() {
		GraphicsContext gc = canvas.getGraphicsContext2D();
		double y = slider.getValue();
		gc.setFill(Paint.valueOf("white"));
		gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
		gc.setFill(Paint.valueOf("blue"));
	    gc.beginPath();
	    gc.moveTo(30.5, 30.5 + y);
	    gc.lineTo(150.5, 30.5 + y);
	    gc.lineTo(150.5, 150.5 + y);
	    gc.lineTo(30.5, 30.5 + y);
	    gc.stroke();
	    
	    gc.fillText(y+"", 10, 10);
	    
	    paintComponent(gc);
	    
	}

	
		protected void paintComponent(GraphicsContext g) {
			int w = (int)canvas.getWidth();
			int h = (int)canvas.getHeight();
			g.setFill(Color.WHITE);
			g.fillRect(0, 0, w, h);
			
			if (scaleType[current] == 2) {
				g.setFill(new Color(0,0,1,0.25));
				g.fillRect(0, h-upper * h, w, (upper-lower) * h);
			}

			
			// operatorPanel.paint(g, w, h);
			
			g.setFill(Color.BLACK);
			if (root != null) {
				g.setFill(new Color(0,0,1, 0.5));
				g.setStroke(new Color(0,0,1, 0.5));
				paint(nodes, root, w, h, g);
				g.setFill(new Color(0,0,1, 1));
				g.setStroke(new Color(0,0,1, 1));
				paint(scalednodes, scalednodes[N*2+1], w, h, g);

				g.setFill(Color.BLACK);
				for (int i = 0; i <= N; i++) {
					g.fillText("" + (char)(65+i), i * (w-20) / N, h - 5);
				}
				
				
			}
			g.setFont(Font.font(48));
			switch(scaleType[current]) {
			case 0:
				g.fillText("Standard scaler", 10, 65);
				break;
			case 1:
				g.fillText("Tree stretcher", 10, 65);
				break;
			case 2:
				g.fillText("Tree flexer", 10, 65);
				break;
			}		
			g.setFont(Font.font(10));
		}
		
		
		private void paint(Node[] nodes, Node root, int w, int h, GraphicsContext g) {
			double [] x = new double[N*2+2];
			int leafCount = N+1;
			for (int i = 0; i < leafCount; i++) {
				if (nodes[i].getLength() > 1e-10) {
					x[i] = i/(leafCount - 1.0);
				} else {
					int closest = findClosestNonZeroNode(i);
					x[i] = closest/(leafCount - 1.0);						
				}
			}
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
					
			g.setLineWidth(1.0);
			traverse(root, g, w, h, x2);
			
			g.setLineWidth(3.0);
			Node current = nodes[0];
			List<Node> path = new ArrayList<>(); 
			// path down to node 1
			while (current != root) {
				int i = current.getNr();
				int j = current.getParent().getNr();
				double hn = current.getHeight();
				double hp = current.getParent().getHeight();
				g.strokeLine((int)(x2[i] * w)-5, (int)(h-hn * h), (int)(x2[j] * w)-5, (int)(h-hp * h));
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
						g.strokeLine(X1, Y1, X2, Y2);							
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


		private void traverse(Node node, GraphicsContext g, int scaleX, int scaleY, double [] x) {
			int i = node.getNr();
			if (!node.isRoot()) {
				int j = node.getParent().getNr();
				double h = node.getHeight();
				double hp = node.getParent().getHeight();
				g.strokeLine((int)(x[i] * scaleX), (int)(scaleY-h * scaleY), (int)(x[j] * scaleX), (int)(scaleY-hp * scaleY));
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

	public static void main(String[] args) {
	    launch(args);
	}
}