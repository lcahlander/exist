/* eXist Open Source Native XML Database
 * Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id: XQuery.g,v 1.30 2004/09/26 21:55:00 wolfgang_m Exp $
 */
header {
	package org.exist.xquery.parser;

	import antlr.debug.misc.*;
	import java.io.StringReader;
	import java.io.BufferedReader;
	import java.io.InputStreamReader;
	import java.util.ArrayList;
	import java.util.List;
	import java.util.Iterator;
	import java.util.Stack;
	import org.exist.storage.BrokerPool;
	import org.exist.storage.DBBroker;
	import org.exist.storage.analysis.Tokenizer;
	import org.exist.EXistException;
	import org.exist.dom.DocumentSet;
	import org.exist.dom.DocumentImpl;
	import org.exist.dom.QName;
	import org.exist.security.PermissionDeniedException;
	import org.exist.security.User;
	import org.exist.xquery.*;
	import org.exist.xquery.value.*;
	import org.exist.xquery.functions.*;
	import org.exist.xquery.update.*;
}

/**
 * The tree parser: walks the AST created by {@link XQueryParser} and generates
 * an internal representation of the query in the form of XQuery expression objects.
 */
class XQueryTreeParser extends TreeParser;

options {
	importVocab=XQuery;
	k= 1;
	defaultErrorHandler = false;
	ASTLabelType = org.exist.xquery.parser.XQueryAST;
}

{
	private XQueryContext context;
	private ExternalModule myModule = null;
	protected ArrayList exceptions= new ArrayList(2);
	protected boolean foundError= false;

	public XQueryTreeParser(XQueryContext context) {
		this();
		this.context= context;
	}

	public ExternalModule getModule() {
		return myModule;
	}
	
	public boolean foundErrors() {
		return foundError;
	}

	public String getErrorMessage() {
		StringBuffer buf= new StringBuffer();
		for (Iterator i= exceptions.iterator(); i.hasNext();) {
			buf.append(((Exception) i.next()).toString());
			buf.append('\n');
		}
		return buf.toString();
	}

	public Exception getLastException() {
		return (Exception) exceptions.get(exceptions.size() - 1);
	}

	protected void handleException(Exception e) {
		foundError= true;
		exceptions.add(e);
	}

	private void throwException(XQueryAST ast, String message) throws XPathException {
		throw new XPathException(ast, message);
	}
	
	private static class ForLetClause {
		XQueryAST ast;
		String varName;
		SequenceType sequenceType= null;
		String posVar= null;
		Expression inputSequence;
		Expression action;
		boolean isForClause= true;
	}

	private static class FunctionParameter {
		String varName;
		SequenceType type= FunctionSignature.DEFAULT_TYPE;

		public FunctionParameter(String name) {
			this.varName= name;
		}
	}
}

xpointer [PathExpr path]
throws XPathException
{ Expression step = null; }:
	#( XPOINTER step=expr [path] )
	|
	#( XPOINTER_ID nc:NCNAME )
	{
		Function fun= new FunId(context);
		List params= new ArrayList(1);
		params.add(new LiteralValue(context, new StringValue(nc.getText())));
		fun.setArguments(params);
		path.addPath(fun);
	}
	;
//	exception catch [RecognitionException e]
//	{ handleException(e); }
	exception catch [EXistException e]
	{ handleException(e); }
	catch [PermissionDeniedException e]
	{ handleException(e); }
//	catch [XPathException e]
//	{ handleException(e); }

xpath [PathExpr path]
throws XPathException
{ context.setRootExpression(path); }
:
	module [path]
	{
		context.resolveForwardReferences();
	}
	;
	exception catch [RecognitionException e]
	{ handleException(e); }
	catch [EXistException e]
	{ handleException(e); }
	catch [PermissionDeniedException e]
	{ handleException(e); }
//	catch [XPathException e]
//	{ handleException(e); }

module [PathExpr path]
throws PermissionDeniedException, EXistException, XPathException
{ Expression step = null; }:
	#(
		m:MODULE_DECL uri:STRING_LITERAL
		{
			myModule = new ExternalModuleImpl(uri.getText(), m.getText());
			context.declareNamespace(m.getText(), uri.getText());
		}
	)
	prolog [path]
	|
	prolog [path] step=expr [path]
	;

/**
 * Process the XQuery prolog.
 */
prolog [PathExpr path]
throws PermissionDeniedException, EXistException, XPathException
{ Expression step = null; }:
	(
		#(
			v:VERSION_DECL
			{
				if (!v.getText().equals("1.0"))
					throw new XPathException(v, "Wrong XQuery version: require 1.0");
			}
		)
	)?
	(
		#(
			prefix:NAMESPACE_DECL uri:STRING_LITERAL
			{ context.declareNamespace(prefix.getText(), uri.getText()); }
		)
		|
		#(
			"xmlspace"
			(
				"preserve" { context.setStripWhitespace(false); }
				|
				"strip" { context.setStripWhitespace(true); }
			)
		)
		|
		#(
			"base-uri" base:STRING_LITERAL
			{ context.setBaseURI(base.getText(), true); }
		)
		|
		#(
			"ordering" ( "ordered" | "unordered" )	// ignored
		)
		|
		#(
			"construction" ( "preserve" | "strip" )	// ignored
		)
		|
		#(
			DEF_NAMESPACE_DECL defu:STRING_LITERAL
			{ context.declareNamespace("", defu.getText()); }
		)
		|
		#(
			DEF_FUNCTION_NS_DECL deff:STRING_LITERAL
			{ context.setDefaultFunctionNamespace(deff.getText()); }
		)
		|
		#(
			DEF_COLLATION_DECL defc:STRING_LITERAL
			{ context.setDefaultCollation(defc.getText()); }
		)
		|
		#(
			qname:GLOBAL_VAR
			{
				PathExpr enclosed= new PathExpr(context);
				SequenceType type= null;
			}
			(
				#(
					"as"
					{ type= new SequenceType(); }
					sequenceType [type]
				)
			)?
			(
				step=e:expr [enclosed]
				{
					VariableDeclaration decl= new VariableDeclaration(context, qname.getText(), enclosed);
					decl.setSequenceType(type);
					decl.setASTNode(e);
					path.add(decl);
					if(myModule != null) {
						QName qn = QName.parse(context, qname.getText());
						myModule.declareVariable(qn, decl);
					}
				}
				|
				"external"
				{
					context.declareVariable(qname.getText(), null);
				}
			)
		)
		|
		functionDecl [path]
		|
		#(
			i:"import" 
			{ 
				String modulePrefix = null;
				String location = null;
			}
			( pfx:NCNAME { modulePrefix = pfx.getText(); } )? 
			moduleURI:STRING_LITERAL 
			( at:STRING_LITERAL { location = at.getText(); } )?
			{
		                try {
					context.importModule(moduleURI.getText(), modulePrefix, location);
		                } catch(XPathException xpe) {
		                    xpe.prependMessage("error found while loading module " + modulePrefix + ": ");
		                    throw xpe;
		                }
			}
		)
	)*
	;

/**
 * Parse a declared function.
 */
functionDecl [PathExpr path]
throws PermissionDeniedException, EXistException, XPathException
{ Expression step = null; }:
	#(
		name:FUNCTION_DECL { PathExpr body= new PathExpr(context); }
		{
			QName qn= null;
			try {
				qn = QName.parse(context, name.getText());
			} catch(XPathException e) {
				// throw exception with correct source location
				e.setASTNode(name);
				throw e;
			}
			FunctionSignature signature= new FunctionSignature(qn);
			UserDefinedFunction func= new UserDefinedFunction(context, signature);
			func.setASTNode(name);
			List varList= new ArrayList(3);
		}
		( paramList [varList] )?
		{
			SequenceType[] types= new SequenceType[varList.size()];
			int j= 0;
			for (Iterator i= varList.iterator(); i.hasNext(); j++) {
				FunctionParameter param= (FunctionParameter) i.next();
				types[j]= param.type;
				func.addVariable(param.varName);
			}
			signature.setArgumentTypes(types);
		}
		(
			#(
				"as"
				{ SequenceType type= new SequenceType(); }
				sequenceType [type]
				{ signature.setReturnType(type); }
			)
		)?
		(
			// the function body:
			#(
				LCURLY step=expr [body]
				{ 
					func.setFunctionBody(body);
					context.declareFunction(func);
					if(myModule != null)
						myModule.declareFunction(func);
				}
			)
			|
			"external"
		)
	)
	;

/**
 * Parse params in function declaration.
 */
paramList [List vars]
throws XPathException
:
	param [vars] ( param [vars] )*
	;

/**
 * Single function param.
 */
param [List vars]
throws XPathException
:
	#(
		varname:VARIABLE_BINDING
		{
			FunctionParameter var= new FunctionParameter(varname.getText());
			vars.add(var);
		}
		(
			#(
				"as"
				{ SequenceType type= new SequenceType(); }
				sequenceType [type]
			)
			{ var.type= type; }
		)?
	)
	;

/**
 * A sequence type declaration.
 */
sequenceType [SequenceType type]
throws XPathException
:
	(
		#(
			t:ATOMIC_TYPE
			{
				QName qn= QName.parse(context, t.getText());
				int code= Type.getType(qn);
				if(!Type.subTypeOf(code, Type.ATOMIC))
					throw new XPathException(t, "Type " + qn.toString() + " is not an atomic type");
				type.setPrimaryType(code);
			}
		)
		|
		#(
			"empty"
			{
				type.setPrimaryType(Type.EMPTY);
				type.setCardinality(Cardinality.EMPTY);
			}
		)
		|
		#(
			"item" { type.setPrimaryType(Type.ITEM); }
		)
		|
		#(
			"node" { type.setPrimaryType(Type.NODE); }
		)
		|
		#(
			"element" 
			{ type.setPrimaryType(Type.ELEMENT); }
			(
				WILDCARD
				|
				qn1:QNAME
				{ 
					QName qname= QName.parse(context, qn1.getText());
					type.setNodeName(qname);
				}
				( QNAME
					{
						throwException(qn1, "Tests of the form element(QName, TypeName) are not supported!");
					}
				)?
			)?
		)
		|
		#(
			ATTRIBUTE_TEST 
			{ type.setPrimaryType(Type.ATTRIBUTE); }
			(
				WILDCARD
				|
				qn2:QNAME
				{
					QName qname= QName.parse(context, qn2.getText());
					type.setNodeName(qname);
				}
				( QNAME
					{
						throwException(qn1, "Tests of the form attribute(QName, TypeName) are not supported!");
					}
				)?
			)?
		)
		|
		#(
			"text" { type.setPrimaryType(Type.TEXT); }
		)
		|
		#(
			"processing-instruction" { type.setPrimaryType(Type.PROCESSING_INSTRUCTION); }
		)
		|
		#(
			"comment" { type.setPrimaryType(Type.COMMENT); }
		)
		|
		#(
			"document-node" { type.setPrimaryType(Type.DOCUMENT); }
		)
	)
	(
		STAR { type.setCardinality(Cardinality.ZERO_OR_MORE); }
		|
		PLUS { type.setCardinality(Cardinality.ONE_OR_MORE); }
		|
		QUESTION { type.setCardinality(Cardinality.ZERO_OR_ONE); }
	)?
	;

/**
 * Process a top-level expression like FLWOR, conditionals, comparisons etc.
 */
expr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{ 
	step= null;
}
:
	step=typeCastExpr [path]
	|
	// sequence constructor:
	#(
		c:COMMA
		{
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		}
		step=expr [left]
		step=expr [right]
		{
			SequenceConstructor sc= new SequenceConstructor(context);
			sc.setASTNode(c);
			sc.addPath(left);
			sc.addPath(right);
			path.addPath(sc);
			step = sc;
		}
	)
	|
	// conditional:
	#(
		astIf:"if"
		{
			PathExpr testExpr= new PathExpr(context);
			PathExpr thenExpr= new PathExpr(context);
			PathExpr elseExpr= new PathExpr(context);
		}
		step=expr [testExpr]
		step=expr [thenExpr]
		step=expr [elseExpr]
		{
			ConditionalExpression cond= new ConditionalExpression(context, testExpr, thenExpr, elseExpr);
			cond.setASTNode(astIf);
			path.add(cond);
			step = cond;
		}
	)
	|
	// quantified expression: some
	#(
		"some"
		{
			List clauses= new ArrayList();
			PathExpr satisfiesExpr = new PathExpr(context);
		}
		(
			#(
				someVarName:VARIABLE_BINDING
				{
					ForLetClause clause= new ForLetClause();
					PathExpr inputSequence = new PathExpr(context);
				}
				(
					#(
						"as"
						sequenceType[clause.sequenceType]
					)
				)?
				step=expr[inputSequence]
				{
					clause.varName= someVarName.getText();
					clause.inputSequence= inputSequence;
					clauses.add(clause);
				}
			)
		)*
		step=expr[satisfiesExpr]
		{
			Expression action = satisfiesExpr;
			for (int i= clauses.size() - 1; i >= 0; i--) {
				ForLetClause clause= (ForLetClause) clauses.get(i);
				BindingExpression expr = new QuantifiedExpression(context, QuantifiedExpression.SOME);
				expr.setVariable(clause.varName);
				expr.setSequenceType(clause.sequenceType);
				expr.setInputSequence(clause.inputSequence);
				expr.setReturnExpression(action);
				satisfiesExpr= null;
				action= expr;
			}
			path.add(action);
			step = action;
		}
	)
	|
	// quantified expression: every
	#(
		"every"
		{
			List clauses= new ArrayList();
			PathExpr satisfiesExpr = new PathExpr(context);
		}
		(
			#(
				everyVarName:VARIABLE_BINDING
				{
					ForLetClause clause= new ForLetClause();
					PathExpr inputSequence = new PathExpr(context);
				}
				(
					#(
						"as"
						sequenceType[clause.sequenceType]
					)
				)?
				step=expr[inputSequence]
				{
					clause.varName= everyVarName.getText();
					clause.inputSequence= inputSequence;
					clauses.add(clause);
				}
			)
		)*
		step=expr[satisfiesExpr]
		{
			Expression action = satisfiesExpr;
			for (int i= clauses.size() - 1; i >= 0; i--) {
				ForLetClause clause= (ForLetClause) clauses.get(i);
				BindingExpression expr = new QuantifiedExpression(context, QuantifiedExpression.EVERY);
				expr.setVariable(clause.varName);
				expr.setSequenceType(clause.sequenceType);
				expr.setInputSequence(clause.inputSequence);
				expr.setReturnExpression(action);
				satisfiesExpr= null;
				action= expr;
			}
			path.add(action);
			step = action;
		}
	)
	|
	// FLWOR expressions: let and for
	#(
		r:"return"
		{
			List clauses= new ArrayList();
			Expression action= new PathExpr(context);
			action.setASTNode(r);
			PathExpr whereExpr= null;
			List orderBy= null;
		}
		(
			#(
				f:"for"
				(
					#(
						varName:VARIABLE_BINDING
						{
							ForLetClause clause= new ForLetClause();
							clause.ast = f;
							PathExpr inputSequence= new PathExpr(context);
						}
						(
							#(
								"as"
								{ clause.sequenceType= new SequenceType(); }
								sequenceType [clause.sequenceType]
							)
						)?
						(
							posVar:POSITIONAL_VAR
							{ clause.posVar= posVar.getText(); }
						)?
						step=expr [inputSequence]
						{
							clause.varName= varName.getText();
							clause.inputSequence= inputSequence;
							clauses.add(clause);
						}
					)
				)+
			)
			|
			#(
				l:"let"
				(
					#(
						letVarName:VARIABLE_BINDING
						{
							ForLetClause clause= new ForLetClause();
							clause.ast = l;
							clause.isForClause= false;
							PathExpr inputSequence= new PathExpr(context);
						}
						(
							#(
								"as"
								{ clause.sequenceType= new SequenceType(); }
								sequenceType [clause.sequenceType]
							)
						)?
						step=expr [inputSequence]
						{
							clause.varName= letVarName.getText();
							clause.inputSequence= inputSequence;
							clauses.add(clause);
						}
					)
				)+
			)
		)+
		(
			w:"where"
			{ 
				whereExpr= new PathExpr(context); 
				whereExpr.setASTNode(w);
			}
			step=expr [whereExpr]
		)?
		(
			#(
				ORDER_BY { orderBy= new ArrayList(3); }
				(
					{ PathExpr orderSpecExpr= new PathExpr(context); }
					step=expr [orderSpecExpr]
					{
						OrderSpec orderSpec= new OrderSpec(context, orderSpecExpr);
						int modifiers= 0;
						orderBy.add(orderSpec);
					}
					(
						(
							"ascending"
							|
							"descending"
							{
								modifiers= OrderSpec.DESCENDING_ORDER;
								orderSpec.setModifiers(modifiers);
							}
						)
					)?
					(
						"empty"
						(
							"greatest"
							|
							"least"
							{
								modifiers |= OrderSpec.EMPTY_LEAST;
								orderSpec.setModifiers(modifiers);
							}
						)
					)?
					(
						"collation" collURI:STRING_LITERAL
						{
							orderSpec.setCollation(collURI.getText());
						}
					)?
				)+
			)
		)?
		step=expr [(PathExpr) action]
		{
			for (int i= clauses.size() - 1; i >= 0; i--) {
				ForLetClause clause= (ForLetClause) clauses.get(i);
				BindingExpression expr;
				if (clause.isForClause)
					expr= new ForExpr(context);
				else
					expr= new LetExpr(context);
				expr.setASTNode(clause.ast);
				expr.setVariable(clause.varName);
				expr.setSequenceType(clause.sequenceType);
				expr.setInputSequence(clause.inputSequence);
				expr.setReturnExpression(action);
				if (clause.isForClause)
					 ((ForExpr) expr).setPositionalVariable(clause.posVar);
				if (whereExpr != null) {
					expr.setWhereExpression(whereExpr);
					whereExpr= null;
				}
				action= expr;
			}
			if (orderBy != null) {
				OrderSpec orderSpecs[]= new OrderSpec[orderBy.size()];
				int k= 0;
				for (Iterator j= orderBy.iterator(); j.hasNext(); k++) {
					OrderSpec orderSpec= (OrderSpec) j.next();
					orderSpecs[k]= orderSpec;
				}
				((BindingExpression)action).setOrderSpecs(orderSpecs);
			}
			path.add(action);
			step = action;
		}
	)
	|
	// instance of:
	#(
		"instance"
		{ 
			PathExpr expr = new PathExpr(context);
			SequenceType type= new SequenceType(); 
		}
		step=expr [expr]
		sequenceType [type]
		{ 
			step = new InstanceOfExpression(context, expr, type); 
			path.add(step);
		}
	)
	|
	// typeswitch
/*	#(
		"typeswitch"
		{
			PathExpr operand = new PathExpr(context);
		}
		step=expr [operand]
		(
			{ 
				SequenceType type = new SequenceType();
				PathExpr returnExpr = new PathExpr(context);
			}
			#(
				"case"
				sequenceType [type]
				step=expr [returnExpr]
				{
					System.out.println("case:" + type);
				}
			)
		)+
	)
	|*/
	// logical operator: or
	#(
		"or"
		{
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		}
		step=expr [left]
		step=expr [right]
	)
	{
		OpOr or= new OpOr(context);
		or.addPath(left);
		or.addPath(right);
		path.addPath(or);
		step = or;
	}
	|
	// logical operator: and
	#(
		"and"
		{
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		}
		step=expr [left]
		step=expr [right]
	)
	{
		OpAnd and= new OpAnd(context);
		and.addPath(left);
		and.addPath(right);
		path.addPath(and);
		step = and;
	}
	|
	// union expressions: | and union
	#(
		UNION
		{
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		}
		step=expr [left]
		step=expr [right]
	)
	{
		Union union= new Union(context, left, right);
		path.add(union);
		step = union;
	}
	|
	// intersections:
	#( "intersect"
		{
			PathExpr left = new PathExpr(context);
			PathExpr right = new PathExpr(context);
		}
		step=expr [left]
		step=expr [right]
	)
	{
		Intersection intersect = new Intersection(context, left, right);
		path.add(intersect);
		step = intersect;
	}
	|
	#( "except"
		{
			PathExpr left = new PathExpr(context);
			PathExpr right = new PathExpr(context);
		}
		step=expr [left]
		step=expr [right]
	)
	{
		Except intersect = new Except(context, left, right);
		path.add(intersect);
		step = intersect;
	}
	|
	// absolute path expression starting with a /
	#(
		ABSOLUTE_SLASH
		{
			RootNode root= new RootNode(context);
			path.add(root);
		}
		( step=expr [path] )?
	)
	|
	// absolute path expression starting with //
	#(
		ABSOLUTE_DSLASH
		{
			RootNode root= new RootNode(context);
			path.add(root);
		}
		(
			step=expr [path]
			{
				if (step instanceof LocationStep) {
					LocationStep s= (LocationStep) step;
					if (s.getAxis() == Constants.ATTRIBUTE_AXIS)
						// combines descendant-or-self::node()/attribute:*
						s.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
					else
						s.setAxis(Constants.DESCENDANT_SELF_AXIS);
				} else
					step.setPrimaryAxis(Constants.DESCENDANT_SELF_AXIS);
			}
		)?
	)
	|
	// range expression: to
	#(
		"to"
		{
			PathExpr start= new PathExpr(context);
			PathExpr end= new PathExpr(context);
			List args= new ArrayList(2);
			args.add(start);
			args.add(end);
		}
		step=expr [start]
		step=expr [end]
		{
			RangeExpression range= new RangeExpression(context);
			range.setArguments(args);
			path.addPath(range);
			step = range;
		}
	)
	|
	step=generalComp [path]
	|
	step=valueComp [path]
	|
	step=nodeComp [path]
	|
	step=fulltextComp [path]
	|
	step=primaryExpr [path]
	|
	step=pathExpr [path]
	|
	step=numericExpr [path]
	|
	step=updateExpr [path]
	;

/**
 * Process a primary expression like function calls,
 * variable references, value constructors etc.
 */
primaryExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	step = null;
}:
	step=constructor [path]
	step=predicates [step]
	{
		path.add(step);
	}
	|
	#(
		PARENTHESIZED
		{ PathExpr pathExpr= new PathExpr(context); }
		( step=expr [pathExpr] )?
	)
	step=predicates [pathExpr]
	{ path.add(step); }
	|
	step=literalExpr [path]
	step=predicates [step]
	{ path.add(step); }
	|
	v:VARIABLE_REF
	{ 
        step= new VariableReference(context, v.getText());
        step.setASTNode(v);
    }
	step=predicates [step]
	{ path.add(step); }
	|
	step=functionCall [path]
	step=predicates [step]
	{ path.add(step); }
	;
	
pathExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	Expression rightStep= null;
	step= null;
	int axis= Constants.CHILD_AXIS;
}
:
	( axis=forwardAxis )?
	{ NodeTest test; }
	(
		qn:QNAME
		{
			QName qname= QName.parse(context, qn.getText());
			test= new NameTest(Type.ELEMENT, qname);
			if (axis == Constants.ATTRIBUTE_AXIS)
				test.setType(Type.ATTRIBUTE);
		}
		|
		#( PREFIX_WILDCARD nc1:NCNAME )
		{
			QName qname= new QName(nc1.getText(), null, null);
			qname.setNamespaceURI(null);
			test= new NameTest(Type.ELEMENT, qname);
			if (axis == Constants.ATTRIBUTE_AXIS)
				test.setType(Type.ATTRIBUTE);
		}
		|
		#( nc:NCNAME WILDCARD )
		{
			String namespaceURI= context.getURIForPrefix(nc.getText());
			QName qname= new QName(null, namespaceURI, nc.getText());
			test= new NameTest(Type.ELEMENT, qname);
			if (axis == Constants.ATTRIBUTE_AXIS)
				test.setType(Type.ATTRIBUTE);
		}
		|
		WILDCARD
		{ 
			if (axis == Constants.ATTRIBUTE_AXIS)
				test= new TypeTest(Type.ATTRIBUTE);
			else
				test= new TypeTest(Type.ELEMENT);
		}
		|
		n:"node"
		{
			if (axis == Constants.ATTRIBUTE_AXIS)
				throw new XPathException(n, "Cannot test for node() on the attribute axis");
			test= new AnyNodeTest(); 
		}
		|
		"text"
		{
			if (axis == Constants.ATTRIBUTE_AXIS)
				throw new XPathException(n, "Cannot test for text() on the attribute axis"); 
			test= new TypeTest(Type.TEXT); 
		}
		|
		#( "element"
			{
				if (axis == Constants.ATTRIBUTE_AXIS)
					throw new XPathException(n, "Cannot test for element() on the attribute axis"); 
				test= new TypeTest(Type.ELEMENT); 
			}
			(
				qn2:QNAME 
				{ 
					QName qname= QName.parse(context, qn2.getText());
					test= new NameTest(Type.ELEMENT, qname);
				}
				|
				WILDCARD
			)?
		)
		|
		#( ATTRIBUTE_TEST
			{ test= new TypeTest(Type.ATTRIBUTE); }
			(
				qn3:QNAME 
				{ 
					QName qname= QName.parse(context, qn3.getText());
					test= new NameTest(Type.ATTRIBUTE, qname);
					axis= Constants.ATTRIBUTE_AXIS;
				}
				|
				WILDCARD
			)?
		)
		|
		"comment"
		{
			if (axis == Constants.ATTRIBUTE_AXIS)
				throw new XPathException(n, "Cannot test for comment() on the attribute axis");
			test= new TypeTest(Type.COMMENT); 
		}
		|
		"document-node"
		{ test= new TypeTest(Type.DOCUMENT); }
	)
	{
		step= new LocationStep(context, axis, test);
		path.add(step);
	}
	( predicate [(LocationStep) step] )*
	|
	AT
	{ QName qname= null; }
	(
		attr:QNAME
		{ qname= QName.parse(context, attr.getText(), null); }
		|
		WILDCARD
		|
		#( PREFIX_WILDCARD nc2:NCNAME )
		{ qname= new QName(nc2.getText(), null, null); }
		|
		#( nc3:NCNAME WILDCARD )
		{
			String namespaceURI= context.getURIForPrefix(nc3.getText());
			if (namespaceURI == null)
				throw new EXistException("No namespace defined for prefix " + nc3.getText());
			qname= new QName(null, namespaceURI, null);
		}
	)
	{
		NodeTest test= qname == null ? new TypeTest(Type.ATTRIBUTE) : new NameTest(Type.ATTRIBUTE, qname);
		step= new LocationStep(context, Constants.ATTRIBUTE_AXIS, test);
		path.add(step);
	}
	( predicate [(LocationStep) step] )*
	|
	SELF
	{
		step= new LocationStep(context, Constants.SELF_AXIS, new TypeTest(Type.NODE));
		path.add(step);
	}
	( predicate [(LocationStep) step] )*
	|
	PARENT
	{
		step= new LocationStep(context, Constants.PARENT_AXIS, new TypeTest(Type.NODE));
		path.add(step);
	}
	( predicate [(LocationStep) step] )*
	|
	#(
		SLASH step=expr [path]
		(
			rightStep=expr [path]
			{
				if (rightStep instanceof LocationStep) {
					if(((LocationStep) rightStep).getAxis() == -1)
						((LocationStep) rightStep).setAxis(Constants.CHILD_AXIS);
				} else {
					rightStep.setPrimaryAxis(Constants.CHILD_AXIS);
					if(rightStep instanceof VariableReference) {
						rightStep = new SimpleStep(context, Constants.CHILD_AXIS, rightStep);
						path.replaceLastExpression(rightStep);
					}
				}
			}
		)?
	)
	{
		if (step instanceof LocationStep && ((LocationStep) step).getAxis() == -1)
			 ((LocationStep) step).setAxis(Constants.CHILD_AXIS);
	}
	|
	#(
		DSLASH step=expr [path]
		(
			rightStep=expr [path]
			{
				if (rightStep instanceof LocationStep) {
					LocationStep rs= (LocationStep) rightStep;
					if (rs.getAxis() == Constants.ATTRIBUTE_AXIS)
						rs.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
					else
						rs.setAxis(Constants.DESCENDANT_SELF_AXIS);
				} else {
					rightStep.setPrimaryAxis(Constants.DESCENDANT_SELF_AXIS);
					if(rightStep instanceof VariableReference) {
						rightStep = new SimpleStep(context, Constants.DESCENDANT_SELF_AXIS, rightStep);
						path.replaceLastExpression(rightStep);
					}
				}
			}
		)?
	)
	{
		if (step instanceof LocationStep && ((LocationStep) step).getAxis() == -1)
			 ((LocationStep) step).setAxis(Constants.DESCENDANT_SELF_AXIS);
	}
	;

literalExpr [PathExpr path]
returns [Expression step]
throws XPathException
{ step= null; }
:
	c:STRING_LITERAL
	{ 
		StringValue val = new StringValue(c.getText());
		val.expand();
        step= new LiteralValue(context, val);
        step.setASTNode(c);
    }
	|
	i:INTEGER_LITERAL
	{ 
        step= new LiteralValue(context, new IntegerValue(i.getText()));
        step.setASTNode(i);
    }
	|
	(
		dec:DECIMAL_LITERAL
		{ 
            step= new LiteralValue(context, new DecimalValue(dec.getText()));
            step.setASTNode(dec);
        }
		|
		dbl:DOUBLE_LITERAL
		{ 
            step= new LiteralValue(context, 
                new DoubleValue(Double.parseDouble(dbl.getText())));
            step.setASTNode(dbl);
        }
	)
	;

numericExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	step= null;
	PathExpr left= new PathExpr(context);
	PathExpr right= new PathExpr(context);
}
:
	#( plus:PLUS step=expr [left] step=expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.PLUS);
        op.setASTNode(plus);
		path.addPath(op);
		step= op;
	}
	|
	#( minus:MINUS step=expr [left] step=expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.MINUS);
        op.setASTNode(minus);
		path.addPath(op);
		step= op;
	}
	|
	#( uminus:UNARY_MINUS step=expr [left] )
	{
		UnaryExpr unary= new UnaryExpr(context, Constants.MINUS);
        unary.setASTNode(uminus);
		unary.add(left);
		path.addPath(unary);
		step= unary;
	}
	|
	#( uplus:UNARY_PLUS step=expr [left] )
	{
		UnaryExpr unary= new UnaryExpr(context, Constants.PLUS);
        unary.setASTNode(uplus);
		unary.add(left);
		path.addPath(unary);
		step= unary;
	}
	|
	#( div:"div" step=expr [left] step=expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.DIV);
        op.setASTNode(div);
		path.addPath(op);
		step= op;
	}
	|
	#( idiv:"idiv" step=expr [left] step=expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.IDIV);
        op.setASTNode(idiv);
		path.addPath(op);
		step= op;
	}
	|
	#( mod:"mod" step=expr [left] step=expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.MOD);
        op.setASTNode(mod);
		path.addPath(op);
		step= op;
	}
	|
	#( mult:STAR step=expr [left] step=expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.MULT);
        op.setASTNode(mult);
		path.addPath(op);
		step= op;
	}
	;

predicates [Expression expression]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	FilteredExpression filter= null;
	step= expression;
}
:
	(
		#(
			PREDICATE
			{
				if (filter == null) {
					filter= new FilteredExpression(context, step);
					step= filter;
				}
				Predicate predicateExpr= new Predicate(context);
			}
			expr [predicateExpr]
			{
				filter.addPredicate(predicateExpr);
			}
		)
	)*
	;

predicate [LocationStep step]
throws PermissionDeniedException, EXistException, XPathException
:
	#(
		PREDICATE
		{ Predicate predicateExpr= new Predicate(context); }
		expr [predicateExpr]
		{ step.addPredicate(predicateExpr); }
	)
	;

functionCall [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	PathExpr pathExpr;
	step= null;
}
:
	#(
		fn:FUNCTION
		{ List params= new ArrayList(2); }
		(
			{ pathExpr= new PathExpr(context); }
			expr [pathExpr]
			{ params.add(pathExpr); }
		)*
	)
	{ step= FunctionFactory.createFunction(context, fn, path, params); }
	;

forwardAxis returns [int axis]
throws PermissionDeniedException, EXistException
{ axis= -1; }
:
	"child" { axis= Constants.CHILD_AXIS; }
	|
	"attribute" { axis= Constants.ATTRIBUTE_AXIS; }
	|
	"self" { axis= Constants.SELF_AXIS; }
	|
	"parent" { axis= Constants.PARENT_AXIS; }
	|
	"descendant" { axis= Constants.DESCENDANT_AXIS; }
	|
	"descendant-or-self" { axis= Constants.DESCENDANT_SELF_AXIS; }
	|
	"following-sibling" { axis= Constants.FOLLOWING_SIBLING_AXIS; }
    |
    "following" { axis= Constants.FOLLOWING_AXIS; }
    |
	"preceding-sibling" { axis= Constants.PRECEDING_SIBLING_AXIS; }
    |
    "preceding" { axis= Constants.PRECEDING_AXIS; }
	|
	"ancestor" { axis= Constants.ANCESTOR_AXIS; }
	|
	"ancestor-or-self" { axis= Constants.ANCESTOR_SELF_AXIS; }
	;

fulltextComp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	step= null;
	PathExpr nodes= new PathExpr(context);
	PathExpr query= new PathExpr(context);
}
:
	#( ANDEQ step=expr [nodes] step=expr [query] )
	{
		ExtFulltext exprCont= new ExtFulltext(context, Constants.FULLTEXT_AND);
		exprCont.setPath(nodes);
		exprCont.addTerm(query);
		path.addPath(exprCont);
	}
	|
	#( OREQ step=expr [nodes] step=expr [query] )
	{
		ExtFulltext exprCont= new ExtFulltext(context, Constants.FULLTEXT_OR);
		exprCont.setPath(nodes);
		exprCont.addTerm(query);
		path.addPath(exprCont);
	}
	;

valueComp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	step= null;
	PathExpr left= new PathExpr(context);
	PathExpr right= new PathExpr(context);
}
:
	#(
		eq:"eq" step=expr [left]
		step=expr [right]
		{
			step= new ValueComparison(context, left, right, Constants.EQ);
            step.setASTNode(eq);
			path.add(step);
		}
	)
	|
	#(
		ne:"ne" step=expr [left]
		step=expr [right]
		{
			step= new ValueComparison(context, left, right, Constants.NEQ);
            step.setASTNode(ne);
			path.add(step);
		}
	)
	|
	#(
		lt:"lt" step=expr [left]
		step=expr [right]
		{
			step= new ValueComparison(context, left, right, Constants.LT);
            step.setASTNode(lt);
			path.add(step);
		}
	)
	|
	#(
		le:"le" step=expr [left]
		step=expr [right]
		{
			step= new ValueComparison(context, left, right, Constants.LTEQ);
            step.setASTNode(le);
			path.add(step);
		}
	)
	|
	#(
		gt:"gt" step=expr [left]
		step=expr [right]
		{
			step= new ValueComparison(context, left, right, Constants.GT);
            step.setASTNode(gt);
			path.add(step);
		}
	)
	|
	#(
		ge:"ge" step=expr [left]
		step=expr [right]
		{
			step= new ValueComparison(context, left, right, Constants.GTEQ);
            step.setASTNode(ge);
			path.add(step);
		}
	)
	;
	
generalComp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	step= null;
	PathExpr left= new PathExpr(context);
	PathExpr right= new PathExpr(context);
}
:
	#(
		eq:EQ step=expr [left]
		step=expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.EQ);
            step.setASTNode(eq);
			path.add(step);
		}
	)
	|
	#(
		neq:NEQ step=expr [left]
		step=expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.NEQ);
            step.setASTNode(neq);
			path.add(step);
		}
	)
	|
	#(
		lt:LT step=expr [left]
		step=expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.LT);
            step.setASTNode(lt);
			path.add(step);
		}
	)
	|
	#(
		lteq:LTEQ step=expr [left]
		step=expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.LTEQ);
            step.setASTNode(lteq);
			path.add(step);
		}
	)
	|
	#(
		gt:GT step=expr [left]
		step=expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.GT);
            step.setASTNode(gt);
			path.add(step);
		}
	)
	|
	#(
		gteq:GTEQ step=expr [left]
		step=expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.GTEQ);
            step.setASTNode(gteq);
			path.add(step);
		}
	)
	;

nodeComp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	step= null;
	PathExpr left= new PathExpr(context);
	PathExpr right= new PathExpr(context);
}
:
	#(
		is:"is" step=expr [left] step=expr [right]
		{
			step = new NodeComparison(context, left, right, Constants.IS);
            step.setASTNode(is);
			path.add(step);
		}
	)
	|
	#(
		isnot:"isnot" step=expr[left] step=expr[right]
		{
			step = new NodeComparison(context, left, right, Constants.ISNOT);
            step.setASTNode(isnot);
			path.add(step);
		}
	)
	|
	#(
		before:BEFORE step=expr[left] step=expr[right]
		{
			step = new NodeComparison(context, left, right, Constants.BEFORE);
            step.setASTNode(before);
			path.add(step);
		}
	)
	|
	#(
		after:AFTER step=expr[left] step=expr[right]
		{
			step = new NodeComparison(context, left, right, Constants.AFTER);
            step.setASTNode(after);
			path.add(step);
		}
	)
	;
	
constructor [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	step= null;
	PathExpr elementContent= null;
	Expression contentExpr= null;
	Expression qnameExpr = null;
}
:
	// computed element constructor
	#(
		qn:COMP_ELEM_CONSTRUCTOR
		{
			ElementConstructor c= new ElementConstructor(context);
			c.setASTNode(qn);
			step= c;
			SequenceConstructor construct = new SequenceConstructor(context);
			EnclosedExpr enclosed = new EnclosedExpr(context);
			enclosed.addPath(construct);
			c.setContent(enclosed);
			PathExpr qnamePathExpr = new PathExpr(context);
			c.setNameExpr(qnamePathExpr);
		}
		
		qnameExpr=expr [qnamePathExpr]
		(
			#( prefix:COMP_NS_CONSTRUCTOR uri:STRING_LITERAL )
			{
				c.addNamespaceDecl(prefix.getText(), uri.getText());
			}
			|
			{ elementContent = new PathExpr(context); }
			contentExpr=expr[elementContent]
			{ construct.addPath(elementContent); }
		)*
	)
	|
	#(
		attr:COMP_ATTR_CONSTRUCTOR
		{
			DynamicAttributeConstructor a= new DynamicAttributeConstructor(context);
            a.setASTNode(attr);
            step = a;
            PathExpr qnamePathExpr = new PathExpr(context);
            a.setNameExpr(qnamePathExpr);
            elementContent = new PathExpr(context);
            a.setContentExpr(elementContent);
		}
		qnameExpr=expr [qnamePathExpr]
		contentExpr=expr [elementContent]
	)
	|
	#(
		pid:COMP_PI_CONSTRUCTOR
		{
			DynamicPIConstructor pd= new DynamicPIConstructor(context);
            pd.setASTNode(pid);
            step = pd;
            PathExpr qnamePathExpr = new PathExpr(context);
            pd.setNameExpr(qnamePathExpr);
            elementContent = new PathExpr(context);
            pd.setContentExpr(elementContent);
		}
		qnameExpr=expr [qnamePathExpr]
		contentExpr=expr [elementContent]
	)
	|
	// direct element constructor
	#(
		e:ELEMENT
		{
			ElementConstructor c= new ElementConstructor(context, e.getText());
			c.setASTNode(e);
			step= c;
		}
		(
			#(
				attrName:ATTRIBUTE
				{
					AttributeConstructor attrib= new AttributeConstructor(context, attrName.getText());
                    attrib.setASTNode(attrName);
				}
				(
					attrVal:ATTRIBUTE_CONTENT
					{
						attrib.addValue(attrVal.getText()); 
					}
					|
					#(
						LCURLY { PathExpr enclosed= new PathExpr(context); }
						expr [enclosed]
						{ attrib.addEnclosedExpr(enclosed); }
					)
				)+
				{ c.addAttribute(attrib); }
			)
		)*
		(
			{
				if (elementContent == null) {
					elementContent= new PathExpr(context);
					c.setContent(elementContent);
				}
			}
			contentExpr=constructor [elementContent]
			{ elementContent.add(contentExpr); }
		)*
	)
	|
	#(
		pcdata:TEXT
		{
			TextConstructor text= new TextConstructor(context, pcdata.getText());
            text.setASTNode(pcdata);
			step= text;
		}
	)
	|
	#(
		t:COMP_TEXT_CONSTRUCTOR
		{ 
			elementContent = new PathExpr(context);
			DynamicTextConstructor text = new DynamicTextConstructor(context, elementContent);
			text.setASTNode(t);
			step= text;
		}
		contentExpr=expr [elementContent]
	)
	|
	#(
		tc:COMP_COMMENT_CONSTRUCTOR
		{
			elementContent = new PathExpr(context);
			DynamicCommentConstructor comment = new DynamicCommentConstructor(context, elementContent);
			comment.setASTNode(t);
			step= comment;
		}
		contentExpr=expr [elementContent]
	)
	|
	#(
		d:COMP_DOC_CONSTRUCTOR
		{
			elementContent = new PathExpr(context);
			DocumentConstructor doc = new DocumentConstructor(context, elementContent);
			doc.setASTNode(d);
			step= doc;
		}
		contentExpr=expr [elementContent]
	)
	|
	#(
		cdata:XML_COMMENT
		{
			CommentConstructor comment= new CommentConstructor(context, cdata.getText());
            comment.setASTNode(cdata);
			step= comment;
		}
	)
	|
	#(
		p:XML_PI
		{
			PIConstructor pi= new PIConstructor(context, p.getText());
            pi.setASTNode(p);
			step= pi;
		}
	)
	|
	#(
		cdataSect:XML_CDATA
		{
			CDATAConstructor cd = new CDATAConstructor(context, cdataSect.getText());
			cd.setASTNode(cdataSect);
			step= cd;
		}
	)
	|
	// enclosed expression within element content
	#(
		l:LCURLY { 
            EnclosedExpr subexpr= new EnclosedExpr(context); 
            subexpr.setASTNode(l);
        }
		step=expr [subexpr]
		{ step= subexpr; }
	)
	;
	
typeCastExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{ 
	step= null;
	PathExpr expr= new PathExpr(context);
	int cardinality= Cardinality.EXACTLY_ONE;
}:
	#(
		castAST:"cast"
		step=expr [expr]
		t:ATOMIC_TYPE
		(
			QUESTION
			{ cardinality= Cardinality.ZERO_OR_ONE; }
		)?
		{
			QName qn= QName.parse(context, t.getText());
			int code= Type.getType(qn);
			CastExpression castExpr= new CastExpression(context, expr, code, cardinality);
			castExpr.setASTNode(castAST);
			path.add(castExpr);
			step = castExpr;
		}
	)
	|
	#(
		castableAST:"castable"
		step=expr [expr]
		t2:ATOMIC_TYPE
		(
			QUESTION
			{ cardinality= Cardinality.ZERO_OR_ONE; }
		)?
		{
			QName qn= QName.parse(context, t2.getText());
			int code= Type.getType(qn);
			CastableExpression castExpr= new CastableExpression(context, expr, code, cardinality);
			castExpr.setASTNode(castAST);
			path.add(castExpr);
			step = castExpr;
		}
	)
	;
	
updateExpr [PathExpr path]
returns [Expression step]
throws XPathException, PermissionDeniedException, EXistException
{
}:
	#( updateAST:"update"
		{ 
			PathExpr p1 = new PathExpr(context);
			PathExpr p2 = new PathExpr(context);
			int type;
			int position = Insert.INSERT_APPEND;
		}
		(
			"replace" { type = 0; }
			|
			"value" { type = 1; }
			|
			"insert"{ type = 2; }
			|
			"delete" { type = 3; }
			|
			"rename" { type = 4; }
		)
		step=expr [p1]
		(
			"before" { position = Insert.INSERT_BEFORE; }
			|
			"after" { position = Insert.INSERT_AFTER; }
			|
			"into" { position = Insert.INSERT_APPEND; }
		)?
		( step=expr [p2] )?
		{
			Modification mod;
			if (type == 0)
				mod = new Replace(context, p1, p2);
			else if (type == 1)
				mod = new Update(context, p1, p2);
			else if (type == 2)
				mod = new Insert(context, p2, p1, position);
			else if (type == 3)
				mod = new Delete(context, p1);
			else
				mod = new Rename(context, p1, p2);
			mod.setASTNode(updateAST);
			path.add(mod);
			step = mod;
		}
	)
	;
