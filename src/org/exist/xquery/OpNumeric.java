/* eXist Open Source Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.xquery;

import java.util.Map;
import java.util.TreeMap;

import org.exist.dom.NodeSet;
import org.exist.storage.DBBroker;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.ComputableValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * numeric operation on two operands by +, -, *, div, mod etc..
 *
 */
public class OpNumeric extends BinaryOp {

	protected int operator;
	protected int returnType = Type.ATOMIC;
	protected NodeSet temp = null;
	protected DBBroker broker;

	public OpNumeric(XQueryContext context, int operator) {
		super(context);
		this.operator = operator;
	}

	public OpNumeric(XQueryContext context, Expression left, Expression right, int operator) {
		super(context);
		this.operator = operator;
		left = atomizeIfNecessary(left);
		right = atomizeIfNecessary(right);
		int ltype = left.returnsType();
		int rtype = right.returnsType();

		if (Type.subTypeOf(ltype, Type.NUMBER) && Type.subTypeOf(rtype, Type.NUMBER)) {
			if (ltype > rtype) {
				right = new UntypedValueCheck(context, ltype, right);
			} else if (rtype > ltype) {
				left = new UntypedValueCheck(context, rtype, left);
			}
			if (operator == Constants.DIV && ltype == Type.INTEGER && rtype == Type.INTEGER) {
				returnType = Type.DECIMAL;
			} else if (operator == Constants.IDIV) {
				returnType = Type.INTEGER;
			} else {
				returnType = Math.max(ltype, rtype);
			}
		} else {
			if (Type.subTypeOf(ltype, Type.NUMBER)) ltype = Type.NUMBER;
			if (Type.subTypeOf(rtype, Type.NUMBER)) rtype = Type.NUMBER;
			OpEntry entry = (OpEntry) OP_TYPES.get(new OpEntry(operator, ltype, rtype));
			if (entry != null) returnType = entry.typeResult;
		}

		add(left);
		add(right);
	}
	
	private Expression atomizeIfNecessary(Expression x) {
		return Type.subTypeOf(x.returnsType(), Type.ATOMIC) ? x : new Atomize(context, x);
	}

	public int returnsType() {
		return returnType;
	}

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
		if (contextItem != null) contextSequence = contextItem.toSequence();
        
		Sequence lseq = getLeft().eval(contextSequence);		
		Sequence rseq = getRight().eval(contextSequence);
		Item lvalue = lseq.itemAt(0);
        Item rvalue = rseq.itemAt(0);
		
        Sequence result;
        
        if (lseq.getLength() == 0) 
            result = Sequence.EMPTY_SEQUENCE;
        else if (rseq.getLength() == 0) 
            result = Sequence.EMPTY_SEQUENCE;
        else {
    		try {
    			// runtime type checks:
    			if (!(lvalue instanceof ComputableValue)) lvalue = lvalue.convertTo(Type.DOUBLE);
    			if (!(rvalue instanceof ComputableValue)) rvalue = rvalue.convertTo(Type.DOUBLE);
    
    			int ltype = lvalue.getType();
                int rtype = rvalue.getType();
                
    			if (Type.subTypeOf(ltype, Type.NUMBER) && Type.subTypeOf(rtype, Type.NUMBER)) {
    				if (ltype > rtype) {
    					rvalue = rvalue.convertTo(ltype);
    				} else if (rtype > ltype) {
    					lvalue = lvalue.convertTo(rtype);
    				}				
    			} else if (Type.subTypeOf(ltype, Type.NUMBER)) {
    				rvalue = rvalue.convertTo(ltype);				
    			} else if (Type.subTypeOf(rtype, Type.NUMBER)) {
    				lvalue = lvalue.convertTo(rtype);				
    			}
    
    			if (operator == Constants.IDIV) {
    				if (!(lvalue instanceof NumericValue && rvalue instanceof NumericValue))
    					throw new XPathException("idiv not supported for types " + Type.getTypeName(lvalue.getType()) + " and " + Type.getTypeName(rvalue.getType()));
                    result = ((NumericValue) lvalue).idiv((NumericValue) rvalue);
    			} else {
                    result = applyOperator((ComputableValue) lvalue, (ComputableValue) rvalue);
    			}
    		} catch (XPathException e) {
    			e.setASTNode(getASTNode());
    			throw e;
    		}
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);
        
        return result;
        
	}

	public ComputableValue applyOperator(ComputableValue left, ComputableValue right)
		throws XPathException {
		switch (operator) {
			case Constants.MINUS:	return left.minus(right);
			case Constants.PLUS:		return left.plus(right);
			case Constants.MULT:		return left.mult(right);
			case Constants.DIV:			return left.div(right);
			case Constants.MOD:		return ((NumericValue) left).mod((NumericValue) right);
			default:								throw new RuntimeException("unknown numeric operator " + operator);
		}
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        getLeft().dump(dumper);
        dumper.display(' ').display(Constants.OPS[operator]).display(' ');
        getRight().dump(dumper);
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();
    	result.append(getLeft().toString());
    	result.append(' ').append(Constants.OPS[operator]).append(' ');
    	result.append(getRight());
    	return result.toString();
    }    
    
    // excerpt from operator mapping table in XQuery 1.0 section B.2
    // http://www.w3.org/TR/xquery/#mapping
    private static final int[] OP_TABLE = {
   	 Constants.PLUS, 	Type.NUMBER, Type.NUMBER, 							Type.NUMBER,
   	 Constants.PLUS,		Type.DATE, Type.YEAR_MONTH_DURATION,		Type.DATE,
   	 Constants.PLUS,		Type.YEAR_MONTH_DURATION, Type.DATE,		Type.DATE,
   	 Constants.PLUS,		Type.DATE, Type.DAY_TIME_DURATION,			Type.DATE,
   	 Constants.PLUS,		Type.DAY_TIME_DURATION, Type.DATE,			Type.DATE,
   	 Constants.PLUS,		Type.TIME, Type.DAY_TIME_DURATION,			Type.TIME,
   	 Constants.PLUS,		Type.DAY_TIME_DURATION, Type.TIME,			Type.TIME,
   	 Constants.PLUS,		Type.DATE_TIME, Type.YEAR_MONTH_DURATION, 	Type.DATE_TIME,
   	 Constants.PLUS,		Type.YEAR_MONTH_DURATION, Type.DATE_TIME, 	Type.DATE_TIME,
   	 Constants.PLUS,		Type.DATE_TIME, Type.DAY_TIME_DURATION,	Type.DATE_TIME,
   	 Constants.PLUS,		Type.DAY_TIME_DURATION, Type.DATE_TIME,	Type.DATE_TIME,
   	 Constants.PLUS,		Type.YEAR_MONTH_DURATION, Type.YEAR_MONTH_DURATION, Type.YEAR_MONTH_DURATION,
   	 Constants.PLUS,		Type.DAY_TIME_DURATION, Type.DAY_TIME_DURATION, Type.DAY_TIME_DURATION,
   	 
   	 Constants.MINUS,	Type.NUMBER, Type.NUMBER,								Type.NUMBER,
   	 Constants.MINUS,	Type.DATE, Type.DATE,											Type.DAY_TIME_DURATION,
   	 Constants.MINUS,	Type.DATE, Type.YEAR_MONTH_DURATION,		Type.DATE,
   	 Constants.MINUS,	Type.DATE, Type.DAY_TIME_DURATION,			Type.DATE,
   	 Constants.MINUS,	Type.TIME, Type.TIME,											Type.DAY_TIME_DURATION,
   	 Constants.MINUS,	Type.TIME, Type.DAY_TIME_DURATION,			Type.TIME,
   	 Constants.MINUS,	Type.DATE_TIME, Type.DATE_TIME,					Type.DAY_TIME_DURATION,
   	 Constants.MINUS,	Type.DATE_TIME, Type.YEAR_MONTH_DURATION, Type.DATE_TIME,
   	 Constants.MINUS,	Type.DATE_TIME, Type.DAY_TIME_DURATION,	Type.DATE_TIME,
   	 Constants.MINUS,	Type.YEAR_MONTH_DURATION, Type.YEAR_MONTH_DURATION, Type.YEAR_MONTH_DURATION,
   	 Constants.MINUS,	Type.DAY_TIME_DURATION, Type.DAY_TIME_DURATION, Type.DAY_TIME_DURATION,
   	 
   	 Constants.MULT,	Type.NUMBER, Type.NUMBER,								Type.NUMBER,
   	 Constants.MULT,	Type.YEAR_MONTH_DURATION, Type.NUMBER,	Type.YEAR_MONTH_DURATION,
   	 Constants.MULT,	Type.NUMBER, Type.YEAR_MONTH_DURATION,	Type.YEAR_MONTH_DURATION,
   	 Constants.MULT,	Type.DAY_TIME_DURATION, Type.NUMBER,		Type.DAY_TIME_DURATION,
   	 Constants.MULT,	Type.NUMBER, Type.DAY_TIME_DURATION,		Type.DAY_TIME_DURATION,
   	 
   	 Constants.IDIV,		Type.NUMBER, Type.NUMBER,								Type.INTEGER,
   	 
   	 Constants.DIV,			Type.NUMBER,	Type.NUMBER,								Type.NUMBER,  // except for integer -> decimal
   	 Constants.DIV,			Type.YEAR_MONTH_DURATION, Type.NUMBER,	Type.YEAR_MONTH_DURATION,
   	 Constants.DIV,			Type.DAY_TIME_DURATION, Type.NUMBER,		Type.DAY_TIME_DURATION,
   	 Constants.DIV,			Type.YEAR_MONTH_DURATION, Type.YEAR_MONTH_DURATION, Type.DECIMAL,
   	 Constants.DIV,			Type.DAY_TIME_DURATION, Type.DAY_TIME_DURATION, Type.DECIMAL,
   	 
   	 Constants.MOD,		Type.NUMBER, Type.NUMBER,								Type.NUMBER,
    };
    
    private static class OpEntry implements Comparable {
   	 public final int op, typeA, typeB, typeResult;
   	 public OpEntry(int op, int typeA, int typeB) {
   		 this(op, typeA, typeB, Type.ATOMIC);
   	 }
   	 public OpEntry(int op, int typeA, int typeB, int typeResult) {
   		 this.op = op; this.typeA = typeA; this.typeB = typeB; this.typeResult = typeResult;
   	 }
   	 public int compareTo(Object o) {
   		 OpEntry that = (OpEntry) o;
   		 if (this.op != that.op) return this.op - that.op;
   		 else if (this.typeA != that.typeA) return this.typeA - that.typeA;
   		 else if (this.typeB != that.typeB) return this.typeB - that.typeB;
   		 else return 0;
   	 }
   	 public boolean equals(Object o) {
   		 try {
      		 OpEntry that = (OpEntry) o;
      		 return this.op == that.op && this.typeA == that.typeA && this.typeB == that.typeB;
   		 } catch (ClassCastException e) {
   			 return false;
   		 }
   	 }
   	 // TODO: implement hashcode, if needed
    }
    
    private static final Map OP_TYPES = new TreeMap();
    static {
   	 for (int i=0; i<OP_TABLE.length; i+=4) {
   		 OpEntry entry = new OpEntry(OP_TABLE[i], OP_TABLE[i+1], OP_TABLE[i+2], OP_TABLE[i+3]);
   		 OP_TYPES.put(entry, entry);
   	 }
    }
}
