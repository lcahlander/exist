/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.debuggee.dbgp.packets;

import org.apache.mina.core.session.IoSession;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class StdOut extends Command {

	/**
	 * [0|1|2] 0 - disable, 1 - copy data, 2 - redirection
	 * 0 (disable)   stdout/stderr output goes to regular place, but not to IDE
	 * 1 (copy)      stdout/stderr output goes to both regular destination and IDE
	 * 2 (redirect)  stdout/stderr output goes to IDE only.
	 */
	private Short outputGoes;
	
    public StdOut(IoSession session, String args) {
        super(session, args);
    }

    @Override
    protected void setArgument(String arg, String val) {
        if (arg.equals("c"))
        	outputGoes = Short.valueOf(val);

        else
            super.setArgument(arg, val);
    }

    @Override
    public void exec() {
    }

	public byte[] responseBytes() {
		String response = xml_declaration + 
			"<response " +
				namespaces +
				"command=\"stdout\" " +
				"success=\"0\" " +
				"transaction_id=\""+transactionID+"\"/>";

		return response.getBytes();
	}

	public byte[] commandBytes() {
		String command = "stdout" +
				" -i "+transactionID+
				" -c "+String.valueOf(outputGoes);
		
		return command.getBytes();
	}
	
	public String toString() {
		return "stdout ["+transactionID+"]";
	}
}
