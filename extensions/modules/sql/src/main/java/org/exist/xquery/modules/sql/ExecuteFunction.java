/*
 *  eXist SQL Module Extension ExecuteFunction
 *  Copyright (C) 2006-2019 Adam Retter <adam@exist-db.org>
 *  www.adamretter.co.uk
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package org.exist.xquery.modules.sql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.memtree.*;
import org.exist.util.XMLReaderPool;
import org.exist.util.io.FastByteArrayOutputStream;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.io.IOException;
import java.io.PrintStream;

import java.io.Reader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;

import org.exist.xquery.value.DateTimeValue;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.annotation.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.modules.sql.SQLUtils.functionSignatures;


/**
 * eXist-db SQL Module Extension ExecuteFunction.
 *
 * Execute a SQL statement against a SQL capable Database
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @version 1.2.0
 */
public class ExecuteFunction extends BasicFunction {

    private static final Logger LOG = LogManager.getLogger(ExecuteFunction.class);

    private static final String FS_EXECUTE_NAME = "execute";

    private static final FunctionParameterSequenceType FS_PARAM_CONNECTION_HANDLE = param(
            "connection-handle",
            Type.LONG,
            "The connection handle");
    private static final FunctionParameterSequenceType FS_PARAM_MAKE_NODE_FROM_COLUMN_NAME = param(
            "make-node-from-column-name",
            Type.BOOLEAN,
            "The flag that indicates whether the xml nodes should be formed from the column names" +
                    " (in this mode a space in a Column Name will be replaced by an underscore!)");

    static final FunctionSignature[] FS_EXECUTE = functionSignatures(
            FS_EXECUTE_NAME,
            "Executes a prepared SQL statement against a SQL db.",
            returnsOpt(Type.ELEMENT, "the results"),
            arities(
                    arity(
                            FS_PARAM_CONNECTION_HANDLE,
                            param("sql-statement", Type.STRING, "The SQL statement"),
                            FS_PARAM_MAKE_NODE_FROM_COLUMN_NAME
                    ),
                    arity(
                            FS_PARAM_CONNECTION_HANDLE,
                            param("statement-handle", Type.INTEGER, "The prepared statement handle"),
                            optParam("parameters", Type.ELEMENT, "Parameters for the prepared statement. e.g. <sql:parameters><sql:param sql:type=\"varchar\">value</sql:param></sql:parameters>"),
                            FS_PARAM_MAKE_NODE_FROM_COLUMN_NAME
                    )
            )
    );

    private final static String PARAMETERS_ELEMENT_NAME = "parameters";
    private final static String PARAM_ELEMENT_NAME = "param";
    private final static String TYPE_ATTRIBUTE_NAME = "type";

    /**
     * ExecuteFunction Constructor.
     *
     * @param context   The Context of the calling XQuery
     * @param signature The function signature
     */
    public ExecuteFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    /**
     * evaluate the call to the XQuery execute() function, it is really the main entry point of this class.
     *
     * @param args            arguments from the execute() function call
     * @param contextSequence the Context Sequence to operate on (not used here internally!)
     *
     * @return An element representing the SQL result set
     *
     * @throws XPathException if an error occurs whilst executing the query
     */
    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        // get the Connection
        final long connectionUID = ((IntegerValue) args[0].itemAt(0)).getLong();
        final Connection con = SQLModule.retrieveConnection(context, connectionUID);
        if (con == null) {
            throw new XPathException(this, "No such SQL Connection");
        }

        Element parametersElement = null;

        //setup the SQL statement
        String sql = null;
        Statement stmt = null;

        try {
            final boolean makeNodeFromColumnName;
            final boolean executeResult;

            // Static SQL or PreparedStatement?
            if (args.length == 3) {

                // get the static SQL statement
                sql = args[1].getStringValue();
                stmt = con.createStatement();
                makeNodeFromColumnName = ((BooleanValue) args[2].itemAt(0)).effectiveBooleanValue();

                //execute the static SQL statement
                executeResult = stmt.execute(sql);

            } else if (args.length == 4) {
                //get the prepared statement
                final long statementUID = ((IntegerValue) args[1].itemAt(0)).getLong();
                final PreparedStatementWithSQL stmtWithSQL = SQLModule.retrievePreparedStatement(context, statementUID);
                sql = stmtWithSQL.getSql();
                stmt = stmtWithSQL.getStmt();

                if (stmt.getConnection() != con) {
                    throw new XPathException(this, "SQL Connection does not match that used for creating the PreparedStatement");
                }

                makeNodeFromColumnName = ((BooleanValue) args[3].itemAt(0)).effectiveBooleanValue();

                if (!args[2].isEmpty()) {
                    parametersElement = (Element) args[2].itemAt(0);
                    setParametersOnPreparedStatement(stmt, parametersElement);
                }

                //execute the PreparedStatement
                executeResult = ((PreparedStatement) stmt).execute();

            } else {
                throw new XPathException(this, "Unknown function call: " + getSignature());
            }

            // return the XML result set
            return resultAsElement(makeNodeFromColumnName, executeResult, stmt);

        } catch (final SQLException sqle) {
            LOG.error("sql:execute() Caught SQLException \"" + sqle.getMessage() + "\" for SQL: \"" + sql + "\"", sqle);
            return sqlExceptionAsElement(sqle, sql, parametersElement);

        } finally {
            // if it's not a prepared statement then close it
            if (stmt != null && !(stmt instanceof PreparedStatement)) {
                try {
                    stmt.close();
                } catch (final SQLException se) {
                    LOG.warn("Unable to close JDBC PreparedStatement: " + se.getMessage(), se);
                }
            }

        }
    }

    private void setParametersOnPreparedStatement(final Statement stmt, final Element parametersElement) throws SQLException, XPathException {
        final String ns = parametersElement.getNamespaceURI();
        if (ns != null && ns.equals(SQLModule.NAMESPACE_URI) && parametersElement.getLocalName().equals(PARAMETERS_ELEMENT_NAME)) {
            final NodeList paramElements = parametersElement.getElementsByTagNameNS(SQLModule.NAMESPACE_URI, PARAM_ELEMENT_NAME);

            for (int i = 0; i < paramElements.getLength(); i++) {
                final Element param = ((Element) paramElements.item(i));
                Node child = param.getFirstChild();

                final String value;
                if (child != null) {
                    if (child instanceof ReferenceNode) {
                        child = ((ReferenceNode) child).getReference().getNode();
                    }

                    value = child.getNodeValue();
                } else {
                    // TODO for VARCHAR, either null or "" could be appropriate (an empty sql:param element is ambiguous)
                    value = null;
                }

                final int sqlType;
                final String type = param.getAttributeNS(SQLModule.NAMESPACE_URI, TYPE_ATTRIBUTE_NAME);
                if (type != null) {
                    sqlType = SQLUtils.sqlTypeFromString(type);
                } else {
                    throw new XPathException(ErrorCodes.ERROR, "<sql:param> must contain attribute sql:type");
                }

                if (sqlType == Types.TIMESTAMP) {
                    final DateTimeValue dv = new DateTimeValue(value);
                    final Timestamp timestampValue = new Timestamp(dv.getDate().getTime());
                    ((PreparedStatement) stmt).setTimestamp(i + 1, timestampValue);

                } else {
                    ((PreparedStatement) stmt).setObject(i + 1, value, sqlType);
                }
            }
        }
    }

    private ElementImpl resultAsElement(final boolean makeNodeFromColumnName,
            final boolean executeResult, final Statement stmt) throws SQLException, XPathException {
        final MemTreeBuilder builder = context.getDocumentBuilder();

        builder.startDocument();

        builder.startElement(new QName("result", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
        builder.addAttribute(new QName("count", null, null), "-1");
        builder.addAttribute(new QName("updateCount", null, null), String.valueOf(stmt.getUpdateCount()));

        int rowCount = 0;
        ResultSet rs = null;
        try {

            if (executeResult) {
                /* SQL Query returned results */

                // iterate through the result set building an XML document
                rs = stmt.getResultSet();
                final ResultSetMetaData rsmd = rs.getMetaData();
                final int iColumns = rsmd.getColumnCount();

                while (rs.next()) {
                    builder.startElement(new QName("row", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
                    builder.addAttribute(new QName("index", null, null), String.valueOf(rs.getRow()));

                    // get each tuple in the row
                    for (int i = 0; i < iColumns; i++) {
                        final String columnName = rsmd.getColumnLabel(i + 1);

                        if (columnName != null) {

                            String colElement = "field";

                            if (makeNodeFromColumnName && columnName.length() > 0) {
                                // use column names as the XML node

                                /*
                                 * Spaces in column names are replaced with
                                 * underscore's
                                 */
                                colElement = SQLUtils.escapeXmlAttr(columnName.replace(' ', '_'));
                            }

                            builder.startElement(new QName(colElement, SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);

                            if (!makeNodeFromColumnName || columnName.length() <= 0) {
                                final String name;
                                if (columnName.length() > 0) {
                                    name = SQLUtils.escapeXmlAttr(columnName);
                                } else {
                                    name = "Column: " + (i + 1);
                                }

                                builder.addAttribute(new QName("name", null, null), name);
                            }

                            builder.addAttribute(new QName(TYPE_ATTRIBUTE_NAME, SQLModule.NAMESPACE_URI, SQLModule.PREFIX), rsmd.getColumnTypeName(i + 1));
                            builder.addAttribute(new QName(TYPE_ATTRIBUTE_NAME, Namespaces.SCHEMA_NS, "xs"), Type.getTypeName(SQLUtils.sqlTypeToXMLType(rsmd.getColumnType(i + 1))));

                            //get the content
                            if (rsmd.getColumnType(i + 1) == Types.SQLXML) {
                                //parse sqlxml value
                                try {
                                    final SQLXML sqlXml = rs.getSQLXML(i + 1);

                                    if (rs.wasNull()) {
                                        // Add a null indicator attribute if the value was SQL Null
                                        builder.addAttribute(new QName("null", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), "true");
                                    } else {
                                        try (final Reader charStream = sqlXml.getCharacterStream()) {
                                            final InputSource src = new InputSource(charStream);
                                            final XMLReaderPool parserPool = context.getBroker().getBrokerPool().getParserPool();
                                            XMLReader reader = null;
                                            try {
                                                reader = parserPool.borrowXMLReader();

                                                final SAXAdapter adapter = new AppendingSAXAdapter(builder);
                                                reader.setContentHandler(adapter);
                                                reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
                                                reader.parse(src);
                                            } finally {
                                                if (reader != null) {
                                                    parserPool.returnXMLReader(reader);
                                                }
                                            }
                                        }
                                    }
                                } catch (final Exception e) {
                                    throw new XPathException("Could not parse column of type SQLXML: " + e.getMessage(), e);
                                }
                            } else {
                                //otherwise assume string value
                                final String colValue = rs.getString(i + 1);

                                if (rs.wasNull()) {
                                    // Add a null indicator attribute if the value was SQL Null
                                    builder.addAttribute(new QName("null", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), "true");
                                } else {
                                    if (colValue != null) {
                                        builder.characters(colValue);
                                    }
                                }
                            }

                            builder.endElement();
                        }
                    }

                    builder.endElement();
                    rowCount++;
                }
            }

            // close `result` element
            builder.endElement();

            // Change the root element count attribute to have the correct value
            final ElementImpl docElement = (ElementImpl) builder.getDocument().getDocumentElement();
            final Node count = docElement.getNode().getAttributes().getNamedItem("count");
            if (count != null) {
                count.setNodeValue(String.valueOf(rowCount));
            }

            builder.endDocument();

            return docElement;
        } finally {
            // close record set
            if (rs != null) {
                try {
                    rs.close();
                } catch (final SQLException se) {
                    LOG.warn("Unable to close JDBC RecordSet: " + se.getMessage(), se);
                }
            }
        }
    }

    private ElementImpl sqlExceptionAsElement(final SQLException sqle, final String sql,
            @Nullable final Element parametersElement) {
        final MemTreeBuilder builder = context.getDocumentBuilder();

        builder.startDocument();
        builder.startElement(new QName("exception", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);

        final boolean recoverable = sqle instanceof SQLRecoverableException;
        builder.addAttribute(new QName("recoverable", null, null), String.valueOf(recoverable));

        builder.startElement(new QName("state", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
        builder.characters(sqle.getSQLState());
        builder.endElement();

        builder.startElement(new QName("message", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
        final String state = sqle.getMessage();
        if (state != null) {
            builder.characters(state);
        }
        builder.endElement();

        builder.startElement(new QName("stack-trace", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
        try (final FastByteArrayOutputStream bufStackTrace = new FastByteArrayOutputStream();
             final PrintStream ps = new PrintStream(bufStackTrace)) {
            sqle.printStackTrace(ps);
            builder.characters(bufStackTrace.toString(UTF_8));
        } catch (final IOException e) {
            LOG.warn("Unable to get stack-trace of JDBC SQLException: " + e.getMessage(), e);
        }
        builder.endElement();

        builder.startElement(new QName("sql", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
        builder.characters(sql);
        builder.endElement();

        if (parametersElement != null) {
            final String ns = parametersElement.getNamespaceURI();
            if (ns != null && ns.equals(SQLModule.NAMESPACE_URI) && parametersElement.getLocalName().equals(PARAMETERS_ELEMENT_NAME)) {
                final NodeList paramElements = parametersElement.getElementsByTagNameNS(SQLModule.NAMESPACE_URI, PARAM_ELEMENT_NAME);

                builder.startElement(new QName(PARAMETERS_ELEMENT_NAME, SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);

                for (int i = 0; i < paramElements.getLength(); i++) {
                    final Element param = ((Element) paramElements.item(i));
                    final Node valueNode = param.getFirstChild();
                    final String value = valueNode != null ? valueNode.getNodeValue() : null;
                    final String type = param.getAttributeNS(SQLModule.NAMESPACE_URI, TYPE_ATTRIBUTE_NAME);

                    builder.startElement(new QName(PARAM_ELEMENT_NAME, SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
                    builder.addAttribute(new QName(TYPE_ATTRIBUTE_NAME, SQLModule.NAMESPACE_URI, SQLModule.PREFIX), type);
                    builder.characters(value);
                    builder.endElement();
                }

                builder.endElement();
            }
        }

        builder.startElement(new QName("xquery", SQLModule.NAMESPACE_URI, SQLModule.PREFIX), null);
        builder.addAttribute(new QName("line", null, null), String.valueOf(getLine()));
        builder.addAttribute(new QName("column", null, null), String.valueOf(getColumn()));
        builder.endElement();

        builder.endElement();
        builder.endDocument();

        return (ElementImpl) builder.getDocument().getDocumentElement();
    }
}
