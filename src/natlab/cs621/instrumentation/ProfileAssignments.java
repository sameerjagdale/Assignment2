package natlab.cs621.instrumentation;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import natlab.toolkits.analysis.varorfun.VFPreorderAnalysis;
import natlab.utils.NodeFinder;
import nodecases.AbstractNodeCaseHandler;
import ast.ASTNode;
import ast.AssignStmt;
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
import ast.WhileStmt;

/**
 * This class is an example of instrumentation. It rewrites an input program to
 * keep track of the number of assignments made at runtime.
 */
public class ProfileAssignments extends AbstractNodeCaseHandler {
	public static void instrument(ASTNode<?> node) {

		node.analyze(new ProfileAssignments());
	}

	// This remembers either the current function name, or "script" for scripts
	private String currentScope;
	private int loopCount = 1;
	static VFPreorderAnalysis kind;
	private int flag = 0;
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
		// stmts.insertChild(init("stmt"), 0);
		// stmts.insertChild(init("loop"), 0);
		// stmts.addChild(display("stmt"));
		// stmts.addChild(display("loop"));
		// recurse on children
		stmts.insertChild(init("func"), 0);
		stmts.addChild(display("func"));
		caseASTNode(stmts);

	}

	public void clearSkip() {
		skip.clear();
	}

	public void caseScript(Script node) {
		currentScope = "script";
		instrumentStmtList(node.getStmts());
		kind = new VFPreorderAnalysis(node);
		kind.analyze();
		clearSkip();
	}

	public void caseFunction(Function node) {
		currentScope = node.getName();
		instrumentStmtList(node.getStmts());
		// kind = new VFPreorderAnalysis(node);
		// kind.analyze();
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
		skip.add(newnode);
		caseASTNode(node);
	}

	@Override
	public void caseWhileStmt(WhileStmt node) {
		if (skip.contains(node)) {
			return;
		}
		AstUtil.insertBefore(node,
				init("loop" + "_" + Integer.toString(loopCount)));

		node.getStmtList().insertChild(
				increment("loop" + "_" + Integer.toString(loopCount)), 0);
		AstUtil.insertAfter(node,
				display("loop" + "_" + Integer.toString(loopCount++)));
		skip.add(node);

		caseASTNode(node);
	}

	// @Override
	// public void caseName(Name node) {
	// if (kind.getResult(node).isFunction()) {
	// AstUtil.insertBefore(node, increment("func"));
	// }
	// caseASTNode(node);
	// }

	@Override
	public void caseParameterizedExpr(ParameterizedExpr node) {
		if (skipExpr.contains(node)) {
			// if (flag == 0) {
			// flag = 1;
			// } else {
			// flag = 0;
			return;
			// }
		}
		// Iterator<NameExpr> I = node.getNameExpressions().iterator();
		// for (; I.hasNext();) {
		// Name n = I.next().getName();
		AstUtil.insertBefore(node.getParent(), increment("func"));
		// }
		// skip.add((Stmt) node.getParent());
		skipExpr.add(node);
		// caseLValueExpr(node);

	}

	@Override
	public void caseAssignStmt(AssignStmt node) {
		if (skip.contains(node)) {
			return;
		}
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
		// AstUtil.insertBefore(node, display("func"));
		skip.add(node);
	}
}