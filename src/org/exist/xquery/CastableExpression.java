/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Implements the "castable as" XQuery expression.
 * 
 * @author wolf
 */
public class CastableExpression extends AbstractExpression {

	private Expression expression;
	private int requiredType;
	private int cardinality = Cardinality.EXACTLY_ONE;
	
	/**
	 * @param context
	 * @param expr
	 * @param requiredType
	 * @param cardinality
	 */
	public CastableExpression(XQueryContext context, Expression expr,
			int requiredType, int cardinality) {
		super(context);
		this.expression = expr;
		this.requiredType = requiredType;
		this.cardinality = cardinality;
		if(!Type.subTypeOf(expression.returnsType(), Type.ATOMIC))
			expression = new Atomize(context, expression);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.CastExpression#returnsType()
	 */
	public int returnsType() {
		return Type.BOOLEAN;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		return Cardinality.EXACTLY_ONE;
	}
	
	public int getDependencies() {
		return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(Expression parent, int flags) throws XPathException {
        expression.analyze(this, flags);
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
        Sequence result;
		Sequence seq = expression.eval(contextSequence, contextItem);
		if(seq.getLength() == 0) {
			if ((cardinality & Cardinality.ZERO) == 0)
                result = BooleanValue.FALSE;
			else
                result = BooleanValue.TRUE;
		}
        else {
    		try {
    			seq.itemAt(0).convertTo(requiredType);
                result = BooleanValue.TRUE;
            //TODO : why catch this xception ?
    		} catch(XPathException e) {       
                System.err.println("Caught exception in CatableExpression");
                result = BooleanValue.FALSE;
    		}
        }
        
        if (context.getProfiler().isEnabled())           
            context.getProfiler().end(this, "", result);   
     
        return result;        
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        expression.dump(dumper);
        dumper.display(" castable as ");
        dumper.display(Type.getTypeName(requiredType));
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();
    	result.append(expression.toString());
    	result.append(" castable as ");
    	result.append(Type.getTypeName(requiredType));
    	return result.toString();
    }    
    
	public void resetState() {
		expression.resetState();
	}
}
