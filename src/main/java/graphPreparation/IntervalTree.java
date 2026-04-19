package graphPreparation;

import java.util.HashSet;

/**
 * Interval Tree (Augmented BST) для эффективного поиска пересекающихся интервалов.
 * Реализация на основе красно-чёрного дерева.
 * 
 * Операции:
 * - insert: O(log n)
 * - delete: O(log n)
 * - queryOverlapping: O(log n + k), где k — число результатов
 */
public class IntervalTree {
	private Node root;

	private static class Node {
		double lo, hi;
		double maxHi;
		int edgeIndex;
		Node left, right, parent;
		boolean isRed;

		Node(double lo, double hi, int edgeIndex) {
			this.lo = lo;
			this.hi = hi;
			this.maxHi = hi;
			this.edgeIndex = edgeIndex;
			this.isRed = true;
		}
	}

	public void insert(double lo, double hi, int edgeIndex) {
		Node newNode = new Node(lo, hi, edgeIndex);

		if (root == null) {
			root = newNode;
			root.isRed = false;
			return;
		}

		Node current = root;
		Node parent = null;

		while (current != null) {
			parent = current;
			current.maxHi = Math.max(current.maxHi, hi);
			if (lo < current.lo || (lo == current.lo && edgeIndex < current.edgeIndex)) {
				current = current.left;
			} else {
				current = current.right;
			}
		}

		newNode.parent = parent;
		if (lo < parent.lo || (lo == parent.lo && edgeIndex < parent.edgeIndex)) {
			parent.left = newNode;
		} else {
			parent.right = newNode;
		}

		fixInsert(newNode);
	}

	public void delete(double lo, int edgeIndex) {
		Node node = findNode(root, lo, edgeIndex);
		if (node == null) return;
		deleteNode(node);
	}

	private Node findNode(Node node, double lo, int edgeIndex) {
		while (node != null) {
			if (node.edgeIndex == edgeIndex) {
				return node;
			}
			if (lo < node.lo || (lo == node.lo && edgeIndex < node.edgeIndex)) {
				node = node.left;
			} else {
				node = node.right;
			}
		}
		return null;
	}

	public void queryOverlapping(double lo, double hi, HashSet<Integer> result) {
		queryOverlapping(root, lo, hi, result);
	}

	private void queryOverlapping(Node node, double lo, double hi, HashSet<Integer> result) {
		if (node == null) return;
		if (node.maxHi < lo) return;

		if (node.left != null) {
			queryOverlapping(node.left, lo, hi, result);
		}

		if (node.lo <= hi && node.hi >= lo) {
			result.add(node.edgeIndex);
		}

		if (node.lo <= hi && node.right != null) {
			queryOverlapping(node.right, lo, hi, result);
		}
	}

	private void fixInsert(Node node) {
		while (node != root && node.parent != null && node.parent.isRed) {
			Node parent = node.parent;
			Node grandparent = parent.parent;
			if (grandparent == null) break;

			if (parent == grandparent.left) {
				Node uncle = grandparent.right;
				if (uncle != null && uncle.isRed) {
					parent.isRed = false;
					uncle.isRed = false;
					grandparent.isRed = true;
					node = grandparent;
				} else {
					if (node == parent.right) {
						node = parent;
						rotateLeft(node);
						parent = node.parent;
						if (parent == null) break;
						grandparent = parent.parent;
						if (grandparent == null) break;
					}
					parent.isRed = false;
					grandparent.isRed = true;
					rotateRight(grandparent);
				}
			} else {
				Node uncle = grandparent.left;
				if (uncle != null && uncle.isRed) {
					parent.isRed = false;
					uncle.isRed = false;
					grandparent.isRed = true;
					node = grandparent;
				} else {
					if (node == parent.left) {
						node = parent;
						rotateRight(node);
						parent = node.parent;
						if (parent == null) break;
						grandparent = parent.parent;
						if (grandparent == null) break;
					}
					parent.isRed = false;
					grandparent.isRed = true;
					rotateLeft(grandparent);
				}
			}
		}
		root.isRed = false;
	}

	private void rotateLeft(Node node) {
		Node right = node.right;
		if (right == null) return;
		
		node.right = right.left;
		if (right.left != null) {
			right.left.parent = node;
		}
		right.parent = node.parent;
		if (node.parent == null) {
			root = right;
		} else if (node == node.parent.left) {
			node.parent.left = right;
		} else {
			node.parent.right = right;
		}
		right.left = node;
		node.parent = right;

		updateMaxHi(node);
		updateMaxHi(right);
	}

	private void rotateRight(Node node) {
		Node left = node.left;
		if (left == null) return;
		
		node.left = left.right;
		if (left.right != null) {
			left.right.parent = node;
		}
		left.parent = node.parent;
		if (node.parent == null) {
			root = left;
		} else if (node == node.parent.right) {
			node.parent.right = left;
		} else {
			node.parent.left = left;
		}
		left.right = node;
		node.parent = left;

		updateMaxHi(node);
		updateMaxHi(left);
	}

	private void updateMaxHi(Node node) {
		if (node == null) return;
		node.maxHi = node.hi;
		if (node.left != null) {
			node.maxHi = Math.max(node.maxHi, node.left.maxHi);
		}
		if (node.right != null) {
			node.maxHi = Math.max(node.maxHi, node.right.maxHi);
		}
	}

	private void deleteNode(Node node) {
		Node replacement;
		Node nodeToFix;
		boolean needFix;

		if (node.left != null && node.right != null) {
			Node successor = minimum(node.right);
			node.lo = successor.lo;
			node.hi = successor.hi;
			node.edgeIndex = successor.edgeIndex;
			node = successor;
		}

		replacement = (node.left != null) ? node.left : node.right;
		needFix = !node.isRed;

		if (replacement != null) {
			replacement.parent = node.parent;
			if (node.parent == null) {
				root = replacement;
			} else if (node == node.parent.left) {
				node.parent.left = replacement;
			} else {
				node.parent.right = replacement;
			}
			nodeToFix = replacement;
		} else if (node.parent == null) {
			root = null;
			return;
		} else {
			if (needFix) {
				fixDelete(node);
			}
			if (node.parent != null) {
				if (node == node.parent.left) {
					node.parent.left = null;
				} else {
					node.parent.right = null;
				}
			}
			nodeToFix = null;
		}

		if (nodeToFix != null && needFix) {
			fixDelete(nodeToFix);
		}

		updateMaxHiToRoot(node.parent);
	}

	private void updateMaxHiToRoot(Node node) {
		while (node != null) {
			double oldMax = node.maxHi;
			updateMaxHi(node);
			if (node.maxHi == oldMax) break;
			node = node.parent;
		}
	}

	private Node minimum(Node node) {
		while (node.left != null) {
			node = node.left;
		}
		return node;
	}

	private void fixDelete(Node node) {
		while (node != root && (node == null || !node.isRed)) {
			if (node == null || node.parent == null) break;
			
			if (node == node.parent.left) {
				Node sibling = node.parent.right;
				if (sibling != null && sibling.isRed) {
					sibling.isRed = false;
					node.parent.isRed = true;
					rotateLeft(node.parent);
					sibling = node.parent.right;
				}
				if (sibling == null) {
					node = node.parent;
					continue;
				}
				if ((sibling.left == null || !sibling.left.isRed) &&
					(sibling.right == null || !sibling.right.isRed)) {
					sibling.isRed = true;
					node = node.parent;
				} else {
					if (sibling.right == null || !sibling.right.isRed) {
                        sibling.left.isRed = false;
						sibling.isRed = true;
						rotateRight(sibling);
						sibling = node.parent.right;
					}
					if (sibling != null) {
						sibling.isRed = node.parent.isRed;
						if (sibling.right != null) sibling.right.isRed = false;
					}
					node.parent.isRed = false;
					rotateLeft(node.parent);
					node = root;
				}
			} else {
				Node sibling = node.parent.left;
				if (sibling != null && sibling.isRed) {
					sibling.isRed = false;
					node.parent.isRed = true;
					rotateRight(node.parent);
					sibling = node.parent.left;
				}
				if (sibling == null) {
					node = node.parent;
					continue;
				}
				if ((sibling.right == null || !sibling.right.isRed) &&
					(sibling.left == null || !sibling.left.isRed)) {
					sibling.isRed = true;
					node = node.parent;
				} else {
					if (sibling.left == null || !sibling.left.isRed) {
                        sibling.right.isRed = false;
						sibling.isRed = true;
						rotateLeft(sibling);
						sibling = node.parent.left;
					}
					if (sibling != null) {
						sibling.isRed = node.parent.isRed;
						if (sibling.left != null) sibling.left.isRed = false;
					}
					node.parent.isRed = false;
					rotateRight(node.parent);
					node = root;
				}
			}
		}
		if (node != null) node.isRed = false;
	}
}
