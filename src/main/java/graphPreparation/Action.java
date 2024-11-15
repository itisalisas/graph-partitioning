package graphPreparation;

enum ActionType {
	ADD, DELETE
}

public class Action {
	
	private double x;
	private int edgeNum;
	private ActionType type;
	
	public Action(double x, int edgeNum, ActionType type) {
		this.setX(x);
		this.setEdgeNum(edgeNum);
		this.setType(type);
	}
	
	
	public double getX() {
		return x;
	}
	
	
	public void setX(double x) {
		this.x = x;
	}
	
	
	public int getEdgeNum() {
		return edgeNum;
	}
	
	
	public void setEdgeNum(int edgeNum) {
		this.edgeNum = edgeNum;
	}
	
	
	public ActionType getType() {
		return type;
	}
	
	
	public void setType(ActionType type) {
		this.type = type;
	}
}
