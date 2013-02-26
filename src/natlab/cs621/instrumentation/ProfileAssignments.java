package natlab.cs621.instrumentation;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;

import natlab.toolkits.analysis.varorfun.VFPreorderAnalysis;
import natlab.utils.NodeFinder;
import nodecases.AbstractNodeCaseHandler;
import ast.ASTNode;
import ast.AssignStmt;
import ast.EndCallExpr;
import ast.EndExpr;
import ast.Expr;
import ast.ForStmt;
import ast.Function;
import ast.IfStmt;
import ast.List;
import ast.Name;
import ast.NameExpr;
import ast.ParameterizedExpr;
import ast.ReturnStmt;
import ast.Script;
import ast.Stmt;
import ast.SwitchStmt;
import ast.WhileStmt;

/**
 * This class is an example of instrumentation. It rewrites an input program to
 * keep track of the number of assignments made at runtime.
 */
public class ProfileAssignments extends AbstractNodeCaseHandler {
	public static void instrument(ASTNode<?> node) {

		node.analyze(new ProfileAssignments());
	}

	// private ASTNode<?> startNode;

	public ProfileAssignments() {
		// loopList = new Stack<Stmt>();
	}

	// This remembers either the current function name, or "script" for scripts
	private String currentScope;
	private int loopCount = 1;
	VFPreorderAnalysis kind;

	// Statements in the skip set won't be analyzed / instrumented.
	// In general we only want to analyze the input program, so we'll
	// add the instrumentation statements we create to this set.
	// There may also be other cases where we just want to skip a node for
	// whatever reason.
	private Set<Stmt> skip = new HashSet<Stmt>();
	private Set<Expr> skipExpr = new HashSet<Expr>();

	// Little helper to add something to the skip set while still using it as an
	// expression
	private Stmt skip(Stmt node) {
		skip.add(node);
		return node;
	}

	private Stmt init(String countName) {
		return skip(Asts.init(currentScope + "_" + countName));
	}

	private Stmt increment(String countName) {
		return skip(Asts.increment(currentScope + "_" + countName));
	}

	private Stmt display(String countName) {
		return skip(Asts.display(currentScope + "_" + countName));
	}

	// This is the default node case. We recurse on the children from left to
	// right,
	// so we're traversing the AST depth-first.
	@Override
	public void caseASTNode(ASTNode node) {
		for (int i = 0; i < node.getNumChild(); ++i) {

			node.getChild(i).analyze(this);
		}
	}

	// This is a helper used by both the Function and Script cases
	private void instrumentStmtList(List<Stmt> stmts) {
		// insert the counter initialization statement as the first statement,
		// and the counter display as the last statement
		stmts.insertChild(init("func"), 0);
		stmts.addChild(display("func"));
		caseASTNode(stmts);

	}

	public void clearSkip() {
		skip.clear();
	}

	public void caseScript(Script node) {
		// startNode = node;
		currentScope = "script";
		instrumentStmtList(node.getStmts());
		kind = new VFPreorderAnalysis(node);
		kind.analyze();
		clearSkip();
		// loopList = new Stack<Stmt>();
	}

	public void caseFunction(Function node) {
		// startNode = node;
		currentScope = node.getName();
		instrumentStmtList(node.getStmts());
		kind = new VFPreorderAnalysis(node);
		kind.analyze();
		clearSkip();
		node.getNestedFunctions().analyze(this);
	}

	/**
	 * We want to count assignments made in for loops, e.g. in for i = 1:10 a(i)
	 * = i; end
	 * 
	 * We want to count the assignments to a(i), but also the implicit
	 * assignments to i. We do this by adding a counter update as the first
	 * statement in the loop body.
	 */

	@Override
	public void caseForStmt(ForStmt node) {
		// Stmt outerNode = null;

		if (skip.contains(node)) {
			return;
		}

		AstUtil.insertBefore(node,
				init("loop" + "_" + Integer.toString(loopCount)));

		node.getStmtList().insertChild(
				increment("loop" + "_" + Integer.toString(loopCount)), 0);
		Stmt newnode = display("loop" + "_" + Integer.toString(loopCount++));
		AstUtil.insertAfter(node, newnode);

		skip.add(node);
		// skip.add(newnode);
		caseASTNode(node);
	}

	@Override
	public void caseWhileStmt(WhileStmt node) {
		if (skip.contains(node)) {
			return;
		}
		AstUtil.insertBefore(node,
				init("loop" + "_" + Integer.toString(loopCount)));

		init("loop_" + Integer.toString(loopCount));
		node.getStmtList().insertChild(
				increment("loop" + "_" + Integer.toString(loopCount)), 0);
		AstUtil.insertAfter(node,
				display("loop" + "_" + Integer.toString(loopCount++)));

		skip.add(node);

		caseASTNode(node);
	}

	@Override
	public void caseIfStmt(IfStmt node) {
		caseASTNode(node);
	}

	@Override
	public void caseStmt(Stmt node) {
		kind = new VFPreorderAnalysis(node);
		kind.analyze();
		// Iterator<NameExpr> ne = node.getNameExpressions().iterator();
		// for (; ne.hasNext();) {
		// if (ne.next().getVarName().toString().substring(0, 1).equals("end"))
		// {
		// System.out.println("entered pop");
		// loopList.pop();
		// }
		// }
	}

	@Override
	public void caseParameterizedExpr(ParameterizedExpr node) {
		if (skipExpr.contains(node)) {
			return;

		}

		Iterator<NameExpr> I = node.getNameExpressions().iterator();
		for (; I.hasNext();) {
			Name n = I.next().getName();
			if (kind != null) {

				if (kind.getResult(n).isFunction()) {
					AstUtil.insertBefore(node.getParent(), increment("func"));
				}
			}
		}

		skipExpr.add(node);

	}

	@Override
	public void caseAssignStmt(AssignStmt node) {
		if (skip.contains(node)) {
			return;
		}
		kind = new VFPreorderAnalysis(node);
		kind.analyze();
		Iterator<NameExpr> ne = node.getRHS().getNameExpressions().iterator();
		while (ne.hasNext()) {
			if (kind.getResult(ne.next().getName()).isFunction()) {
				AstUtil.insertBefore(node, increment("func"));
			}
		}
		skip.add(node);
	}

	/**
	 * Functions can end early with a return statement; in that case we also
	 * want to display the counter, but the end of the function won't be
	 * reached, so we insert the display before the return statement also.
	 */
	@Override
	public void caseReturnStmt(ReturnStmt node) {
		if (skip.contains(node)) {
			return;
		}
		// for (int i = 1; i <= loopCount; i++) {
		// AstUtil.insertBefore(node, display("loop_" + Integer.toString(i)));
		// }
		AstUtil.insertBefore(node, display("func"));
		skip.add(node);
	}
}