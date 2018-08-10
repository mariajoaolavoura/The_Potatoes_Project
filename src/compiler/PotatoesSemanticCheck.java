/***************************************************************************************
*	Title: PotatoesProject - PotatoesSemanticCheck Class Source GlobalStatement
*	GlobalStatement version: 2.0
*	Author: Luis Moura (https://github.com/LuisPedroMoura)
*	Acknowledgments for version 1.0: Maria Joao Lavoura
*	(https://github.com/mariajoaolavoura), for the help in brainstorming the concepts
*	needed to create the first working version of this Class.
*	Date: July-2018
*	Availability: https://github.com/LuisPedroMoura/PotatoesProject
*
***************************************************************************************/

package compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

import potatoesGrammar.grammar.PotatoesBaseVisitor;
import potatoesGrammar.grammar.PotatoesFunctionNames;
import potatoesGrammar.grammar.PotatoesParser.*;
import potatoesGrammar.utils.Variable;
import typesGrammar.grammar.TypesFileInfo;
import typesGrammar.utils.Type;
import utils.errorHandling.ErrorHandling;

/**
 * 
 * <b>PotatoesSemanticCheck</b><p>
 * This class performs a semantic analysis for a Parse Tree generated from a Potatoes Source File<p>
 * 
 * @author Ines Justo (84804), Luis Pedro Moura (83808), Maria Joao Lavoura (84681), Pedro Teixeira (84715)
 * @version May-June 2018
 */
public class PotatoesSemanticCheck extends PotatoesBaseVisitor<Boolean>  {

	// Static Constant (Debug Only)
	private static final boolean debug = false;

	// --------------------------------------------------------------------------
	// Static Fields
	private static String TypesFilePath;
	
	private	static TypesFileInfo					typesFileInfo;	// initialized in visitUsing();
	private	static List<String>						reservedWords;	// initialized in visitUsing();
	private static Map<String, Type> 				typesTable;		// initialized in visitUsing();
	private static PotatoesFunctionNames			functions;		// initialized in CTOR;
	private static Map<String, Function_IDContext>	functionNames;	// initialized in CTOR;
	private static Map<String, List<String>>		functionArgs;	// initialized in CTOR;

	protected static ParseTreeProperty<Object> 		mapCtxObj		= new ParseTreeProperty<>();
	protected static List<HashMap<String, Object>>	symbolTable 	= new ArrayList<>();
	
	protected static boolean visitedMain = false;
	protected static Object currentReturn = null;
	
 	public PotatoesSemanticCheck(String PotatoesFilePath){
		functions = new PotatoesFunctionNames(PotatoesFilePath);
		functionNames = functions.getFunctions();
		functionArgs = functions.getFunctionsArgs();
		symbolTable.add(new HashMap<String, Object>());
		if (debug) ErrorHandling.printInfo("The PotatoesFilePath is: " + PotatoesFilePath);
	}
	
	// --------------------------------------------------------------------------
	// Getters
	public static ParseTreeProperty<Object> getMapCtxObj(){
		return mapCtxObj;
	}

	public static TypesFileInfo getTypesFileInfo() {
		return typesFileInfo;
	}

	// --------------------------------------------------------------------------
	// Main Rules 
	@Override 
	public Boolean visitProgram(ProgramContext ctx) {
		Boolean valid = visit(ctx.using());
		List<GlobalStatementContext> globalStatementsInstructions = ctx.globalStatement();

		// Visit all globalStatement rules
		for (GlobalStatementContext c : globalStatementsInstructions) {
			Boolean res = visit(c);
			valid = valid && res;
		}
		return valid;
	}

	@Override 
	public Boolean visitUsing(UsingContext ctx) {
		// Get information from the types file
		TypesFilePath = getStringText(ctx.STRING().getText());
		if (debug) ErrorHandling.printInfo(ctx, TypesFilePath);
		typesFileInfo = new TypesFileInfo(TypesFilePath);
		reservedWords = typesFileInfo.getReservedWords();
		typesTable = typesFileInfo.getTypesTable();

		// Debug
		if (debug) {
			ErrorHandling.printInfo(ctx, "Types File path is: " + TypesFilePath);
			ErrorHandling.printInfo(ctx, typesFileInfo.toString());
		}

		return true;
	}

	@Override
	public Boolean visitGlobalStatement_Declaration(GlobalStatement_DeclarationContext ctx) {
		return visit(ctx.varDeclaration());
	}

	@Override 
	public Boolean visitGlobalStatement_Assignment(GlobalStatement_AssignmentContext ctx) {
		Boolean result =  visit(ctx.assignment());
		if(debug) {ErrorHandling.printInfo("Visited " + ctx.assignment().getText() + " : " + result);}
		return result;
	}

	@Override
	public Boolean visitGlobalStatement_Function(GlobalStatement_FunctionContext ctx) {
		return visitChildren(ctx);
	}

	// --------------------------------------------------------------------------
	// Statements 

	@Override 
	public Boolean visitStatement_Declaration(Statement_DeclarationContext ctx) {
		return visit(ctx.varDeclaration());
	}

	@Override 
	public Boolean visitStatement_Assignment(Statement_AssignmentContext ctx) {
		boolean valid =  visit(ctx.assignment());
		if(debug) {ErrorHandling.printInfo(ctx, "Visited " + ctx.assignment().getText() + " : " + valid);}
		return valid;
	}

	@Override 
	public Boolean visitStatement_Control_Flow_Statement(Statement_Control_Flow_StatementContext ctx) {
		return visitChildren(ctx);
	}

	@Override 
	public Boolean visitStatement_FunctionCall(Statement_FunctionCallContext ctx) {
		return visit(ctx.functionCall());
	}

	@Override 
	public Boolean visitStatement_Function_Return(Statement_Function_ReturnContext ctx) {
		return visitChildren(ctx);
	}

	@Override 
	public Boolean visitStatement_Print(Statement_PrintContext ctx) {
		return visit(ctx.print());
	}

	// --------------------------------------------------------------------------
	// Assignments
	
	@Override
	public Boolean visitAssignment_Var_Declaration_Expression(Assignment_Var_Declaration_ExpressionContext ctx) {
		if (!visit(ctx.varDeclaration()) || !visit(ctx.expression())) {
			return false;
		};

		String typeName = (String) mapCtxObj.get(ctx.varDeclaration().type());
		String varName = ctx.varDeclaration().ID().getText();
		Object obj = mapCtxObj.get(ctx.expression());

		// verify that variable to be created has valid name
		if(!isValidNewVariableName(varName, ctx)) return false;

		if (debug) {
			ErrorHandling.printInfo(ctx, "[ASSIGN_VARDEC_EXPR] Visited visitAssignment_Var_Declaration_Expression");
			ErrorHandling.printInfo(ctx, "--- Assigning to " + varName + " with type " + typeName);
		}

		// assign Variable to string is not possible
		if(typeName.equals("string")) {
			if (obj instanceof String) {
				updateSymbolTable(ctx.varDeclaration().ID().getText(), "str");
				mapCtxObj.put(ctx, "str");
				return true;
			}
			ErrorHandling.printError(ctx, "expression result is not compatible with string Type");
			return false;
		}

		// assign Variable to boolean is not possible
		if (typeName.equals("boolean")) {
			if (obj instanceof Boolean) {
				updateSymbolTable(ctx.varDeclaration().ID().getText(), true);
				mapCtxObj.put(ctx, true);
				return true;
			}
			ErrorHandling.printError(ctx, "expression result is not compatible with boolean Type");
			return false;
		}

		// assign Variable to Variable
		Variable temp = (Variable) mapCtxObj.get(ctx.expression());
		Variable a = new Variable(temp); // deep copy

		if (debug) {
			ErrorHandling.printInfo(ctx, "--- Variable to assign is " + a);
			ErrorHandling.printInfo(ctx, "type to assign to is: " + typesTable.get(typeName));
		}

		if (a.convertTypeTo(typesTable.get(typeName))) {
			updateSymbolTable(ctx.varDeclaration().ID().getText(), a);
			mapCtxObj.put(ctx, a);
			if(debug) {ErrorHandling.printInfo(ctx, "assigned Variable var=" + a.getType().getTypeName() + ", " +
					"val=" + a.getValue() + " to " + ctx.varDeclaration().ID().getText());}
			return true;
		}

		// Types are not compatible
		ErrorHandling.printError(ctx, "Type \"" + typeName + "\" is not compatible with \"" + a.getType().getTypeName() + "\"!");
		return false;
	}

	@Override
	public Boolean visitAssignment_Var_Expression(Assignment_Var_ExpressionContext ctx) {
		if (!visit(ctx.var()) || !visit(ctx.expression())) {
			return false;
		};
		
		Object varObj = mapCtxObj.get(ctx.var());
		Object exprResObj = mapCtxObj.get(ctx.expression());

		if (debug) {
			ErrorHandling.printInfo(ctx, "[OP_ASSIGN_VAR_OP] Visited visitAssignment_Var_Declaration_Expression");
			ErrorHandling.printInfo(ctx, "--- Assigning to " + ctx.var().ID().getText() + " with type " + checkSymbolTable().get(ctx.var().ID().getText()));
		}

		if(varObj instanceof String) {
			if (exprResObj instanceof String) {
				updateSymbolTable(ctx.var().ID().getText(), "str");
				mapCtxObj.put(ctx, "str");
				return true;
			}
			ErrorHandling.printError(ctx, "expression result is not compatible with string Type");
			return false;
		}

		if(varObj instanceof Boolean) {
			if (exprResObj instanceof Boolean) {
				updateSymbolTable(ctx.var().ID().getText(), true);
				mapCtxObj.put(ctx, true);
				return true;
			}
			ErrorHandling.printError(ctx, "expression result is not compatible with boolean Type");
			return false;
		}

		Variable aux = (Variable) varObj;
		Variable var = new Variable(aux);
		String typeName = var.getType().getTypeName();

		aux = (Variable) exprResObj;
		Variable a = new Variable(aux);

		if (debug) {ErrorHandling.printInfo(ctx, "--- Variable to assign is " + a);}

		// If type of variable a can be converted to the destination type (ie are compatible)
		if (a.convertTypeTo(typesTable.get(typeName))) {
			updateSymbolTable(ctx.var().ID().getText(), a);
			mapCtxObj.put(ctx, a);
			if(debug) {ErrorHandling.printInfo(ctx, "assigned Variable var=" + a.getType().getTypeName() + ", " +
					"val=" + a.getValue() + " to " + ctx.var().ID().getText());}
			return true;
		}

		// Types are not compatible
		ErrorHandling.printError(ctx, "Type \"" + typeName + "\" is not compatible with \"" + a.getType().getTypeName() + "\"");
		return false;
	}
	
	// --------------------------------------------------------------------------
	// Functions
	
	@Override
	public Boolean visitFunction_Main(Function_MainContext ctx) {
		if (visitedMain == true) {
			ErrorHandling.printError(ctx, "Only one main function is allowed");
			return false;
		}
		
		visitedMain = true;
		
		openFunctionScope();
		visit(ctx.scope());
		
		return true;
	}

	@Override
	public Boolean visitFunction_ID(Function_IDContext ctx) {
		boolean valid = true;
		for (TypeContext type : ctx.type()) {
			valid = valid && visit(type);
		}
		if (!valid) {
			return false;
		}
		
		// get args list from function call
		@SuppressWarnings("unchecked")
		List<Object> args = (List<Object>) mapCtxObj.get(ctx.getParent());
		
		// open new scope
		openFunctionScope();
		
		// store new variables with function call value and function signature name
		for (int i = 0; i < args.size(); i++) {
			updateSymbolTable((String) mapCtxObj.get(ctx.type(i)), args.get(i));
		}
		
		return valid && visit(ctx.scope());
	}

	@Override
	public Boolean visitFunctionReturn(FunctionReturnContext ctx) {
		if(!visit(ctx.expression())) {
			return false;
		}
		
		Object obj = mapCtxObj.get(ctx.expression());
		
		if ((currentReturn instanceof String && obj instanceof String) || (currentReturn instanceof Boolean && obj instanceof Boolean)) {
			mapCtxObj.put(ctx, mapCtxObj.get(ctx.expression()));
			return true;
		}
		
		if (currentReturn instanceof Variable && obj instanceof Variable) {
			Variable a = (Variable) obj;
			Variable b = (Variable) currentReturn;
			if(a.typeIsCompatible(b)) {
				mapCtxObj.put(ctx, mapCtxObj.get(ctx.expression()));
				return true;
			}
		}
		
		ErrorHandling.printError(ctx, "return is not compatible with function signature");
		return false;
	}
	
	@Override
	public Boolean visitFunctionCall(FunctionCallContext ctx) {
		Boolean valid = true;
		for (ExpressionContext expr : ctx.expression()) {
			valid = valid && visit(expr);
		}
		if(!valid) {
			return false;
		}
		
		// get function context to be visited and args needed from list of functions	
		Function_IDContext functionToVisit = functionNames.get(ctx.ID().getText());
		List<String> argsToUse	= functionArgs.get(ctx.ID().getText());
		
		// get list of arguments given in function call
		List<Object> functionCallArgs = new ArrayList<>();
		for (ExpressionContext expr : ctx.expression()) {
			visit(expr);
			functionCallArgs.add(mapCtxObj.get(expr));
		}
				
		// if number of arguments do not match -> error
		if(argsToUse.size() != functionCallArgs.size()) {
			ErrorHandling.printError(ctx, "NUmber of arguments in function call do not match required arguments");
			return false;
		}
		
		// verify that all arguments types match function arguments
		for (int i = 0; i < argsToUse.size(); i++) {
			if (argsToUse.get(i).equals("string") && functionCallArgs.get(i) instanceof String) {
				continue;
			}
			else if (argsToUse.get(i).equals("boolean") && functionCallArgs.get(i) instanceof Boolean) {
				continue;
			}
			else if (argsToUse.get(i).equals("string") || functionCallArgs.get(i) instanceof String) {
				ErrorHandling.printError(ctx, "function call arguments are no compatible with function signature");
				return false;
			}
			else if (argsToUse.get(i).equals("boolean") || functionCallArgs.get(i) instanceof Boolean) {
				ErrorHandling.printError(ctx, "function call arguments are no compatible with function signature");
				return false;
			}
			else {
				Variable arg = (Variable) functionCallArgs.get(i);
				String argTypeName = arg.getType().getTypeName();
				if (argsToUse.get(i).equals(argTypeName)) {
					continue;
				}
				ErrorHandling.printError(ctx, "function call arguments are no compatible with function signature");
				return false;
			}
		}
		
		mapCtxObj.put(ctx, functionCallArgs);
		visit(functionToVisit);
		return true;
	}


	// --------------------------------------------------------------------------
	// Control Flow Statements

	@Override 
	public Boolean visitControlFlowStatement(ControlFlowStatementContext ctx) {
		extendScope();
		return visitChildren(ctx);
	}

	@Override 
	public Boolean visitForLoop(ForLoopContext ctx) {
		Boolean valid = true;
		Boolean res   = true;

		// visit all assignments
		for (AssignmentContext a : ctx.assignment()) {		
			res = visit(a);
			valid = valid && res;
		}

		res = visit(ctx.expression());
		valid = valid && res;

		// visit scope
		valid = valid && visit(ctx.scope());

		return valid;
	}

	@Override 
	public Boolean visitWhileLoop(WhileLoopContext ctx) {
		Boolean valid = true;
		Boolean res = visit(ctx.expression());
		valid = valid && res;

		// visit all scope
		valid = valid && visit(ctx.scope());

		return valid;
	}

	@Override
	public Boolean visitCondition(ConditionContext ctx) {
		return visitChildren(ctx);
	}
	
	@Override 
	public Boolean visitIfCondition(IfConditionContext ctx) {
		Boolean valid = true;
		Boolean res = visit(ctx.expression());
		valid = valid && res;

		// visit all scope
		valid = valid && visit(ctx.scope());
		
		return valid;
	}

	@Override 
	public Boolean visitElseIfCondition(ElseIfConditionContext ctx) {
		Boolean valid = true;
		Boolean res = visit(ctx.expression());
		valid = valid && res;

		// visit all scope
		valid = valid && visit(ctx.scope());
		
		return valid;
	}

	@Override 
	public Boolean visitElseCondition(ElseConditionContext ctx) {
		Boolean valid = true;

		// visit all scope
		valid = valid && visit(ctx.scope());
				
		return valid;
	}

	@Override
	public Boolean visitScope(ScopeContext ctx) {
		Boolean valid = true;
		List<StatementContext> statements = ctx.statement();

		// Visit all statement rules
		for (StatementContext stat : statements) {
			Boolean res = visit(stat);
			valid = valid && res;
		}
		
		closeScope();
		
		return valid;
	}

	// --------------------------------------------------------------------------
	// Expressions
	
	@Override 
	public Boolean visitExpression_Parenthesis(Expression_ParenthesisContext ctx) {
		if(!visit(ctx.expression())) {
			return false;
		}
		mapCtxObj.put(ctx, mapCtxObj.get(ctx.expression()));
		return true;
	}
	
	@Override
	public Boolean visitExpression_Cast(Expression_CastContext ctx) {
		if(!visitChildren(ctx)) {
			return false;
		}
		
		String castName = ctx.cast().ID().getText();
		Object obj = mapCtxObj.get(ctx.expression());
		
		// erify if cast is valid ID
		if (!typesTable.keySet().contains(castName)) {
			ErrorHandling.printError(ctx, "'" + castName + "' is not a valid Type");
			return false;
		}
		
		// booleans cannot be casted
		if(obj instanceof Boolean) {
			ErrorHandling.printError(ctx, "boolean Type cannot be casted");
			return false;
		}
		
		if (obj instanceof String) {
			try {
				String str = (String) obj;
				Double value = Double.parseDouble(str);
				Type newType = typesTable.get(castName);
				mapCtxObj.put(ctx, new Variable(newType, value));
				return true;
			}
			catch (NumberFormatException e) {
				ErrorHandling.printError("String does not contain a parsable value");
				return false;
			}
			catch (NullPointerException e) {
				ErrorHandling.printError("String is null");
				return false;
			}
		}
		
		if (obj instanceof Variable) {
			Variable temp = (Variable) obj;
			Variable opRes = new Variable(temp); // deep copy
			Type newType = typesTable.get(ctx.cast().ID().getText());

			boolean wasConverted = opRes.convertTypeTo(newType);
			//Types are not compatible
			if (!wasConverted) {
				if (castName.equals("number")){
					opRes = new Variable(newType, opRes.getValue());
				}
				else {
					ErrorHandling.printError(ctx,"cast and expression Types are not compatible");
					return false;
				}	
			}

			mapCtxObj.put(ctx, opRes);
			return true;
		}
		return false;
	}
	
	@Override
	public Boolean visitExpression_UnaryOperators(Expression_UnaryOperatorsContext ctx) {
		if(!visit(ctx.expression())) {
			return false;
		}
		
		String op = ctx.op.getText();
		Object obj = mapCtxObj.get(ctx.expression());
		
		if (op.equals("-")) {
			
			if (obj instanceof String) {
				ErrorHandling.printError(ctx, "Unary Simetric operator cannot be applied to string Type");
				return false;
			}
			if(obj instanceof Boolean) {
				ErrorHandling.printError(ctx, "Unary Simetric operator cannot be applied to boolean Type");
				return false;
			}
			
			mapCtxObj.put(ctx, obj); // don't need to calculate symmetric value to guarantee semantic correctness in future calculations

			if(debug) ErrorHandling.printInfo(ctx, "[OP_OP_SIMETRIC]");
		}
		
		if (op.equals("!")) {
			
			if (obj instanceof String) {
				ErrorHandling.printError(ctx, "Unary Not operator cannot be applied to string Type");
				return false;
			}
			if(obj instanceof Variable) {
				ErrorHandling.printError(ctx, "Unary Not operator cannot be applied to numeric Types");
				return false;
			}
			
			mapCtxObj.put(ctx, true);

			if (debug) ErrorHandling.printInfo(ctx, "[OP_LOGIC_OPERAND_NOT_VAR]");
		}
		
		return true;
	}
	
	@Override 
	public Boolean visitExpression_Power(Expression_PowerContext ctx) {
		if(!visit(ctx.expression(0)) || !visit(ctx.expression(1))) {
			return false;
		}
		
		Object base = mapCtxObj.get(ctx.expression(0));
		Object pow = mapCtxObj.get(ctx.expression(1));
		
		// pow has to have type number
		if (pow instanceof String || pow instanceof Boolean) {
			ErrorHandling.printError(ctx, "exponent has invalid Type for power operation");
			return false;
		}
		if (pow instanceof Variable) {
			Variable var = (Variable) pow;
			if (!var.getType().equals(typesTable.get("number"))) {
				
			}
		}
		
		if (base instanceof Boolean) {
			ErrorHandling.printError(ctx, "boolean Type cannot be powered");
			return false;
		}
		if (base instanceof String) { 
			mapCtxObj.put(ctx, "str"); // strings can be powered, "str"^3 == "strstrstr"
			return true;
		}
		if (base instanceof Variable) {
			Variable aux = (Variable) pow;
			Variable powVar = new Variable(aux);
			aux = (Variable) base;
			Variable baseVar = new Variable(aux);
			
			Variable res = Variable.power(baseVar, powVar);
			mapCtxObj.put(ctx, res);
			
			if (debug) {
				ErrorHandling.printInfo(ctx, "[OP_POWER] Visited Expression Power");
				ErrorHandling.printInfo(ctx, "--- Powering Variable " + base + "with power " + pow + "and result is " + res);
			}
			return true;
		}

		
		return false;
	}
	
	@Override 
	public Boolean visitExpression_Mult_Div_Mod(Expression_Mult_Div_ModContext ctx) {
		if(!visit(ctx.expression(0)) || !visit(ctx.expression(1))) {
			return false;
		}
		
		String op = ctx.op.getText();
		Object obj0 = mapCtxObj.get(ctx.expression(0));
		Object obj1 = mapCtxObj.get(ctx.expression(1));
		
		if(obj0 instanceof Boolean || obj1 instanceof Boolean) {
			ErrorHandling.printError(ctx, "bad operand Types for operator '" + op + "'");
			return false;
		}

		if(obj0 instanceof String || obj1 instanceof String) {
			ErrorHandling.printError(ctx, "bad operand Types for operator '" + op + "'");
			return false;
		}
		
		// string multiplication (expanded concatenation)
		if(obj0 instanceof String && obj1 instanceof Variable) {
			Variable var = (Variable) obj1;
			if (var.getType().equals(typesTable.get("number")) && op.equals("*")) {
				mapCtxObj.put(ctx, "str");
				return true;
			}
		}
		if(obj0 instanceof Variable && obj1 instanceof String) {
			Variable var = (Variable) obj0;
			if (var.getType().equals(typesTable.get("number")) && op.equals("*")) {
				mapCtxObj.put(ctx, "str");
				return true;
			}
		}
		
		if (obj0 instanceof Variable && obj1 instanceof Variable) {
			Variable aux = (Variable) obj0;
			Variable a = new Variable(aux); // deep copy
			aux = (Variable) obj1;
			Variable b = new Variable(aux); // deep copy
			
			// Modulus
			if (op.equals("%")) {
				try {
					Variable res = Variable.mod(a, b);
					mapCtxObj.put(ctx, res);
					return true;
				}
				catch (IllegalArgumentException e) {
					ErrorHandling.printError(ctx, "Right side of mod expression has to be of Type Number!");
					return false;
				}
			}
			
			// Multiplication
			if (op.equals("*")) {
				Variable res = Variable.multiply(a, b); 
				mapCtxObj.put(ctx, res); 
				if (debug) { ErrorHandling.printInfo(ctx, "result of multiplication is Variable " + res);}
				return true;
			}
			
			// Division expression
			if (op.equals("/")) {
				try {
					Variable res = Variable.divide(a, b); 
					mapCtxObj.put(ctx, res);
					if (debug) { ErrorHandling.printInfo(ctx, "result of division is Variable " + res);}
					return true;
				}
				catch (ArithmeticException e) {
					ErrorHandling.printError(ctx, "Cannot divide by zero");
				}
			}
			
			if (debug) {
				ErrorHandling.printInfo(ctx, "[OP_MULTDIVMOD] Visiting Expression Mult_Div_Mod");
				ErrorHandling.printInfo(ctx, "[OP_MULTDIVMOD] op0: " + ctx.expression(0).getText());
				ErrorHandling.printInfo(ctx, "[OP_MULTDIVMOD] op1: " + ctx.expression(1).getText());
				ErrorHandling.printInfo(ctx, "[OP_MULTDIVMOD] variable a " + a);
				ErrorHandling.printInfo(ctx, "[OP_MULTDIVMOD] variable b " + b);
				ErrorHandling.printInfo(ctx, "[OP_MULTDIVMOD] op		 " + op + "\n");
				ErrorHandling.printInfo(ctx, "[OP_MULTDIVMOD] temp: " + aux);
			}
		}
		return false;
	}
	
	@Override 
	public Boolean visitExpression_Add_Sub(Expression_Add_SubContext ctx) {
		if(!visit(ctx.expression(0)) || !visit(ctx.expression(1))) {
			return false;
		}
		
		Object obj0 = mapCtxObj.get(ctx.expression(0));
		Object obj1 = mapCtxObj.get(ctx.expression(1));
		String op = ctx.op.getText();
		
		// one of the elements in expression is boolean
		if(obj0 instanceof Boolean || obj1 instanceof Boolean) {
			ErrorHandling.printError(ctx, "bad operand Types for operator '" + op + "'");
			return false;
		}
		
		// both elements are string (concatenation or removeAll)
		if(obj0 instanceof String && obj1 instanceof String) {
			mapCtxObj.put(ctx, "str");
			return true;
		}
		
		// only one element is string (not possible)
		if (obj0 instanceof String || obj1 instanceof String) {
			ErrorHandling.printError(ctx, "bad operand Types for operator '" + op + "'");
			return false;
		}
		
		// both elements are Variables
		if (obj0 instanceof Variable && obj1 instanceof Variable) {
			Variable aux = (Variable) obj0;
			Variable a = new Variable(aux); // deep copy
			aux = (Variable) obj1;
			Variable b = new Variable(aux); // deep copy
	
			if (debug) {
				ErrorHandling.printInfo(ctx, "[OP_ADDSUB] Visiting Expression Add_Sub");
				ErrorHandling.printInfo(ctx, "[OP_ADDSUB] variable a " + a);
				ErrorHandling.printInfo(ctx, "[OP_ADDSUB] variable b " + b + "\n");
			}
	
			// Addition or Subtraction (if one is compatible the other is also compatible
			try {
				Variable res = Variable.add(a, b);
				if (debug) { ErrorHandling.printInfo(ctx, "result of sum is Variable " + res);}
				mapCtxObj.put(ctx, res);
				return true;
			}
			catch (IllegalArgumentException e) {
				ErrorHandling.printError(ctx, "Incompatible types in expression");
				return false;
			}
		}
		
		return false;
	}
	
	@Override
	public Boolean visitExpression_RelationalQuantityOperators(Expression_RelationalQuantityOperatorsContext ctx) {
		if(!visit(ctx.expression(0)) || !visit(ctx.expression(1))) {
			return false;
		}

		if (debug) {
			ErrorHandling.printInfo(ctx, "[OP_COMPARISON]");
		}
		
		Object obj0 = mapCtxObj.get(ctx.expression(0));
		Object obj1 = mapCtxObj.get(ctx.expression(1));
		String op = ctx.op.getText();
		
		if(obj0 instanceof Boolean || obj1 instanceof Boolean) {
			ErrorHandling.printError(ctx, "bad operand types for relational operator '" + op + "'");
			return false;
		}
		
		if(obj0 instanceof String && obj1 instanceof String) {
			mapCtxObj.put(ctx, true);
			return true;
		}
		
		if(obj0 instanceof String || obj1 instanceof String) {
			ErrorHandling.printError(ctx, "bad operand types for relational operator '" + op + "'");
			return false;
		}
		
		if(obj0 instanceof Variable && obj1 instanceof Variable) {
			Variable a = (Variable) obj0;
			Variable b = (Variable) obj1;
			
			if (debug) {
				ErrorHandling.printInfo(ctx, "THIS IS A : " + a);
				ErrorHandling.printInfo(ctx, "THIS IS B : " + b);
			}
			
			boolean comparisonIsPossible = a.typeIsCompatible(b);
			
			if(!comparisonIsPossible) {
				ErrorHandling.printError(ctx, "Types are not compatible");
				return false;
			}
			
			mapCtxObj.put(ctx, comparisonIsPossible);
			return true;
		}
		return false;
	}

	@Override
	public Boolean visitExpression_RelationalEquality(Expression_RelationalEqualityContext ctx) {
		if(!visit(ctx.expression(0)) || !visit(ctx.expression(1))) {
			return false;
		}

		if (debug) {
			ErrorHandling.printInfo(ctx, "[OP_COMPARISON]");
		}
		
		Object obj0 = mapCtxObj.get(ctx.expression(0));
		Object obj1 = mapCtxObj.get(ctx.expression(1));
		
		if (obj0 instanceof Boolean && obj1 instanceof Boolean) {
			mapCtxObj.put(ctx, true);
			return true;
		}
		
		if (obj0 instanceof String && obj1 instanceof String) {
			mapCtxObj.put(ctx, "str");
			return true;
		}
		
		if (obj0 instanceof Variable && obj1 instanceof Variable) {
			Variable a = (Variable) obj0;
			Variable b = (Variable) obj1;
			
			if (debug) {
				ErrorHandling.printInfo(ctx, "THIS IS A : " + a);
				ErrorHandling.printInfo(ctx, "THIS IS B : " + b);
			}
			
			boolean comparisonIsPossible = a.typeIsCompatible(b);
			
			if(!comparisonIsPossible) {
				ErrorHandling.printError(ctx, "Types to be compared are not compatible");
				return false;
			}
			
			mapCtxObj.put(ctx, comparisonIsPossible);
			return true;
		}
		return false;
	}
	
	@Override
	public Boolean visitExpression_logicalOperation(Expression_logicalOperationContext ctx) {
		if(!visit(ctx.expression(0)) || !visit(ctx.expression(1))) {
			return false;
		}

		if (debug) {
			ErrorHandling.printInfo(ctx, "[OP_COMPARISON]");
		}
		
		Object obj0 = mapCtxObj.get(ctx.expression(0));
		Object obj1 = mapCtxObj.get(ctx.expression(1));
		String op = ctx.op.getText();
		
		if(obj0 instanceof Variable || obj1 instanceof Variable || obj0 instanceof String || obj1 instanceof String) {
			ErrorHandling.printError(ctx, "bad operand types for logical operator '" + op + "'");
			return false;
		}
		
		// both obj have Type boolean
		mapCtxObj.put(ctx, true);
		return true;
	}
	
	@Override 
	public Boolean visitExpression_Var(Expression_VarContext ctx) {
		if(!visit(ctx.var())) {
			return false;
		}
		mapCtxObj.put(ctx, mapCtxObj.get(ctx.var()));
		return true;
	}
	
	@Override
	public Boolean visitExpression_Value(Expression_ValueContext ctx) {
		if(!visit(ctx.value())) {
			return false;
		}
		mapCtxObj.put(ctx, mapCtxObj.get(ctx.value()));
		return true;
	}

	@Override
	public Boolean visitExpression_FunctionCall(Expression_FunctionCallContext ctx) {
		if(!visit(ctx.functionCall())) {
			return false;
		}
		mapCtxObj.put(ctx, mapCtxObj.get(ctx.functionCall()));
		return true;
	}

	// --------------------------------------------------------------------------
	// Prints
	
	@Override
	public Boolean visitPrint(PrintContext ctx) {
		if(!visit(ctx.expression())) {
			return false;
		}
		mapCtxObj.put(ctx, mapCtxObj.get(ctx.expression()));
		return true;
	}
	
	@Override
	public Boolean visitSave(SaveContext ctx) {
		return visit(ctx.expression());
	}
	
	@Override
	public Boolean visitInput(InputContext ctx) {
		return true;
	}

	// --------------------------------------------------------------------------
	// Variables
	

	@Override 
	public Boolean visitVar(VarContext ctx) {
		String key = ctx.ID().getText();
		if (!checkSymbolTable().containsKey(key)) {
			ErrorHandling.printError(ctx, "Variable \"" + key + "\" is not declared!");
			return false;
		};
		mapCtxObj.put(ctx, checkSymbolTable().get(key));
		return true;
	}

	@Override  
	public Boolean visitVarDeclaration(VarDeclarationContext ctx) {
		return visit(ctx.type());
	}

	// --------------------------------------------------------------------------
	// Types

	@Override 
	public Boolean visitType_Number_Type(Type_Number_TypeContext ctx) {
		mapCtxObj.put(ctx, ctx.NUMBER_TYPE().getText());
		return true;
	}

	@Override 
	public Boolean visitType_Boolean_Type(Type_Boolean_TypeContext ctx) {
		mapCtxObj.put(ctx, ctx.BOOLEAN_TYPE().getText());
		return true;
	}

	@Override 
	public Boolean visitType_String_Type(Type_String_TypeContext ctx) {
		mapCtxObj.put(ctx, ctx.STRING_TYPE().getText());
		return true;
	}

	@Override 
	public Boolean visitType_Void_Type(Type_Void_TypeContext ctx) {
		mapCtxObj.put(ctx, ctx.VOID_TYPE().getText());
		return true;
	}

	@Override 
	public Boolean visitType_ID_Type(Type_ID_TypeContext ctx) {
		mapCtxObj.put(ctx, ctx.ID().getText());
		return true;
	}

	// --------------------------------------------------------------------------
	// Values

	@Override 
	public Boolean visitValue_Number(Value_NumberContext ctx) {
		Variable a = new Variable(typesTable.get("number"), 1.0);
		mapCtxObj.put(ctx, a);
		return true;
	}

	@Override 
	public Boolean visitValue_Boolean(Value_BooleanContext ctx) {
		mapCtxObj.put(ctx, true);
		return true;
	}

	@Override 
	public Boolean visitValue_String(Value_StringContext ctx) {
		mapCtxObj.put(ctx, "str");
		return true;
	}

	@Override 
	public Boolean visitCast(CastContext ctx) {
		mapCtxObj.put(ctx, ctx.ID().getText());
		return true;
	}

	// --------------------------------------------------------------------------
	// Auxiliar Functions
	
	/**
	 * Extends the previous scope into a new scope for use inside control flow statements
	 */
	private static void extendScope() {
		// create copy of scope context
		HashMap<String, Object> newScope = new HashMap<>();
		HashMap<String, Object> oldScope = symbolTable.get(0);
		for (String key : oldScope.keySet()) {
			newScope.put(key, oldScope.get(key));
		}
		symbolTable.add(newScope);
	}
	
	/**
	 * Creates a new clean scope for the function and adds global variables (that are always in scope[0]
	 */
	private static void openFunctionScope() {
		HashMap<String, Object> newScope = new HashMap<>();
		HashMap<String, Object> globalScope = symbolTable.get(0);
		for (String key : globalScope.keySet()) {
			newScope.put(key, globalScope.get(key));
		}
		symbolTable.add(newScope);
	}
	
	private static void closeScope() {
		int lastIndex = symbolTable.size();
		symbolTable.remove(lastIndex);
	}
	
	private static void updateSymbolTable(String key, Object value) {
		int lastIndex = symbolTable.size();
		symbolTable.get(lastIndex).put(key, value);
	}
	
	private static HashMap<String, Object> checkSymbolTable() {
		int lastIndex = symbolTable.size();
		return symbolTable.get(lastIndex);
	}
	

	private static boolean isValidNewVariableName(String varName, ParserRuleContext ctx) {

		if (checkSymbolTable().containsKey(varName)) {
			ErrorHandling.printError(ctx, "Variable \"" + varName +"\" already declared");
			return false;
		}
		
		if (reservedWords.contains(varName)) {
			ErrorHandling.printError(ctx, varName +"\" is a reserved word");
			return false;
		}
		
		return true;
	}

	private static String getStringText(String str) {
		str = str.substring(1, str.length() -1);
		return str;
	}

}