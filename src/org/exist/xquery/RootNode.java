/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04 The eXist Team
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xquery;

import java.util.Iterator;

import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.util.LockException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Reads a set of document root nodes from the context. Used for
 * absolute path expression that do not start with fn:doc() or fn:collection().
 * 
 * @author Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class RootNode extends Step {

    private Sequence cached = null;

    private DocumentSet cachedDocs = null;

    /** Constructor for the RootNode object */
    public RootNode(XQueryContext context) {
        super(context, Constants.SELF_AXIS);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        // get statically known documents from the context
        DocumentSet ds = context.getStaticallyKnownDocuments();
        if (ds == null || ds.getLength() == 0) return Sequence.EMPTY_SEQUENCE;
        
        // if the expression occurs in a nested context, we might have cached the
        // document set
        if (cachedDocs != null && cachedDocs.equals(ds)) return cached;
        
        // check if the loaded documents should remain locked
        boolean lockOnLoad = context.lockDocumentsOnLoad();
        try {
            // wait for pending updates
            ds.lock(false);
            
	        NodeSet result = new ArraySet(ds.getLength());
	        DocumentImpl doc;
	        for (Iterator i = ds.iterator(); i.hasNext();) {
	            doc = (DocumentImpl) i.next();
	            if(doc.getResourceType() == DocumentImpl.XML_FILE) {  // skip binary resources
	            	result.add(new NodeProxy(doc, -1));
	            	if(lockOnLoad) {
	            	    context.getLockedDocuments().add(doc);
	            	}
	            }
	        }
	        cached = result;
	        cachedDocs = ds;
            
	        return result;
        } catch (LockException e) {
            throw new XPathException(getASTNode(), "Failed to acquire lock on the context document set");
        } finally {
            if(!lockOnLoad)
                // release all locks
                ds.unlock(false);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Step#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("/ROOT");
    }
    
    public String toString() {
        return "/ROOT";
    }    
    
    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.Step#returnsType()
     */
    public int returnsType() {
        return Type.NODE;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.Step#resetState()
     */
    public void resetState() {
        cachedDocs = null;
        cached = null;
    }
}