package natlab.cs621.analysis;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import natlab.toolkits.analysis.HashMapFlowMap;

import natlab.toolkits.analysis.Merger;
import natlab.toolkits.analysis.Mergers;
import natlab.toolkits.analysis.varorfun.VFPreorderAnalysis;
import nodecases.AbstractNodeCaseHandler;
import analysis.AbstractSimpleStructuralBackwardAnalysis;
import ast.ASTNode;
import ast.AssignStmt;
import ast.EmptyStmt;
import ast.IfStmt;
import ast.NameExpr;
import ast.Stmt;
import ast.WhileStmt;

/**
 * This is a code for Intra-procedural Live Variable analysis.
 */
public class LiveVariable
		extends
		AbstractSimpleStructuralBackwardAnalysis<HashMapFlowMap<String, Set<Stmt>>> {
	// Factory method, instantiates and runs the analysis

	public static LiveVariable of(ASTNode<?> tree) {

		LiveVariable analysis = new LiveVariable(tree);
		analysis.analyze();
		return analysis;
	}

	// Used for Kind analysis
	private VFPreorderAnalysis kind;

	public void prettyPrint() {
		getTree().analyze(this.new Printer());
	}

	private LiveVariable(ASTNode tree) {

		super(tree);
		currentInSet = newInitialFlow();
		currentOutSet = newInitialFlow();

	}

	// The initial flow is an empty map.
	@Override
	public HashMapFlowMap<String, Set<Stmt>> newInitialFlow() {
		return new HashMapFlowMap<String, Set<Stmt>>();
	}

	/**
	 * Handles statements where No variable is generated or killed
	 */
	@Override
	public void caseStmt(Stmt node) {
		outFlowSets.put(node, currentOutSet.copy());
		currentOutSet.copy(currentInSet);
		inFlowSets.put(node, currentInSet.copy());

	}

	/**
	 * Handles the if Statement. Also handles LHS variables in case of
	 * conditional expressions
	 */
	@Override
	public void caseIfStmt(IfStmt node) {
		outFlowSets.put(node, currentOutSet.copy());
		currentOutSet.copy(currentInSet);
		Set<String> kill = node.getLValues();

		HashMapFlowMap<String, Set<Stmt>> gen = newInitialFlow();

		Iterator<NameExpr> i = node.getNameExpressions().iterator();
		for (; i.hasNext();) {
			Set<Stmt> defs = new HashSet<Stmt>();
			String var = i.next().getVarName();

			if (!kill.contains(var)) {
				defs.add(node);
				gen.put(var, defs);
			}

		}

		currentInSet = newInitialFlow();
		currentOutSet.copy(currentInSet);
		currentInSet.removeKeys(kill);
		currentInSet.union(UNION, gen);
		inFlowSets.put(node, currentInSet.copy());
	}

	/**
	 * Handles code for while statements. Similar to if.
	 */
	@Override
	public void caseWhileStmt(WhileStmt node) {
		outFlowSets.put(node, currentOutSet.copy());
		currentOutSet.copy(currentInSet);
		Set<String> kill = node.getLValues();

		HashMapFlowMap<String, Set<Stmt>> gen = newInitialFlow();
		Iterator<NameExpr> i = node.getNameExpressions().iterator();

		for (; i.hasNext();) {

			Set<Stmt> defs = new HashSet<Stmt>();
			String var = i.next().getVarName();

			if (!kill.contains(var)) {
				defs.add(node);
				gen.put(var, defs);
			}

		}
		currentInSet = newInitialFlow();
		currentOutSet.copy(currentInSet);
		currentInSet.removeKeys(kill);
		currentInSet.union(UNION, gen);
		inFlowSets.put(node, currentInSet.copy());
	}

	/**
	 * Handles code for Assignment statements
	 */
	@Override
	public void caseAssignStmt(AssignStmt node) {

		outFlowSets.put(node, currentOutSet.copy());

		currentOutSet.copy(currentInSet);

		Set<String> kill = node.getLValues();

		HashMapFlowMap<String, Set<Stmt>> gen = newInitialFlow();

		// Kind analysis to differentiate functions from arrays
		kind = new VFPreorderAnalysis(node);
		kind.analyze();
		Iterator<NameExpr> I = node.getRHS().getNameExpressions().iterator();

		for (; I.hasNext();) {
			NameExpr ne = I.next();
			Set<Stmt> defs = new HashSet<Stmt>();
			defs.add(node);

			if (kind.getResult(ne.getName()).isFunction() != true) {
				gen.put(ne.getVarName(), defs);
			}
		}
		// compute (in - kill) + gen
		currentInSet = newInitialFlow();
		currentOutSet.copy(currentInSet);
		currentInSet.removeKeys(kill);
		currentInSet.union(UNION, gen);
		inFlowSets.put(node, currentInSet.copy());
	}

	// Copy is straightforward.
	@Override
	public void copy(HashMapFlowMap<String, Set<Stmt>> src,
			HashMapFlowMap<String, Set<Stmt>> dest) {
		// System.out.println("copy entered");
		if (src != null || dest != null) {

			src.copy(dest);
		}

	}

	// We just want to create this merger once. It's used in merge() below.
	private static final Merger<Set<Stmt>> UNION = Mergers.union();

	// Here we define the merge operation. There are two "levels" of set union
	// here:
	// union on the maps by key (the union method)
	// if a key is in both maps, union the two sets (the UNION merger passed to
	// the method)
	@Override
	public void merge(HashMapFlowMap<String, Set<Stmt>> in1,
			HashMapFlowMap<String, Set<Stmt>> in2,
			HashMapFlowMap<String, Set<Stmt>> out) {

		in1.union(UNION, in2, out);

	}

	// This class pretty prints the program annotated with analysis results.
	class Printer extends AbstractNodeCaseHandler {

		private int getLine(ASTNode<?> node) {
			return beaver.Symbol.getLine(node.getStart());
		}

		private int getColumn(ASTNode<?> node) {
			return beaver.Symbol.getColumn(node.getStart());
		}

		@Override
		public void caseASTNode(ASTNode node) {
			for (int i = 0; i < node.getNumChild(); i++) {
				node.getChild(i).analyze(this);
			}
		}

		@Override
		public void caseStmt(Stmt node) {

			if (inFlowSets.get(node) == null) {
				return;
			}
			System.out.println("in {");
			printMap(inFlowSets.get(node));
			System.out.println("}");
			System.out.println(node.getPrettyPrinted());
			System.out.println("out {");
			printMap(outFlowSets.get(node));
			System.out.println("}");
			System.out.println();

			caseASTNode(node);
		}

		@Override
		public void caseEmptyStmt(EmptyStmt node) {
			return;
		}

		private void printMap(HashMapFlowMap<String, Set<Stmt>> map) {
			// System.out.println("entering print set");
			if (map == null) {
				// System.out.println("map is null");
				return;
			}
			for (String var : map.keySet()) {

				System.out.print(var);

				System.out.println();
			}
		}
	}

}
