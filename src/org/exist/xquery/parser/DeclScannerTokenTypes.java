// $ANTLR 2.7.4: "DeclScanner.g" -> "DeclScanner.java"$

	package org.exist.xquery.parser;
	
	import org.exist.xquery.XPathException;

public interface DeclScannerTokenTypes {
	int EOF = 1;
	int NULL_TREE_LOOKAHEAD = 3;
	int QNAME = 4;
	int PREDICATE = 5;
	int FLWOR = 6;
	int PARENTHESIZED = 7;
	int ABSOLUTE_SLASH = 8;
	int ABSOLUTE_DSLASH = 9;
	int WILDCARD = 10;
	int PREFIX_WILDCARD = 11;
	int FUNCTION = 12;
	int UNARY_MINUS = 13;
	int UNARY_PLUS = 14;
	int XPOINTER = 15;
	int XPOINTER_ID = 16;
	int VARIABLE_REF = 17;
	int VARIABLE_BINDING = 18;
	int ELEMENT = 19;
	int ATTRIBUTE = 20;
	int ATTRIBUTE_CONTENT = 21;
	int TEXT = 22;
	int VERSION_DECL = 23;
	int NAMESPACE_DECL = 24;
	int DEF_NAMESPACE_DECL = 25;
	int DEF_COLLATION_DECL = 26;
	int DEF_FUNCTION_NS_DECL = 27;
	int GLOBAL_VAR = 28;
	int FUNCTION_DECL = 29;
	int PROLOG = 30;
	int ATOMIC_TYPE = 31;
	int MODULE = 32;
	int ORDER_BY = 33;
	int POSITIONAL_VAR = 34;
	int BEFORE = 35;
	int AFTER = 36;
	int MODULE_DECL = 37;
	int ATTRIBUTE_TEST = 38;
	int COMP_ELEM_CONSTRUCTOR = 39;
	int COMP_ATTR_CONSTRUCTOR = 40;
	int COMP_TEXT_CONSTRUCTOR = 41;
	int COMP_COMMENT_CONSTRUCTOR = 42;
	int COMP_PI_CONSTRUCTOR = 43;
	int COMP_NS_CONSTRUCTOR = 44;
	int COMP_DOC_CONSTRUCTOR = 45;
	int LITERAL_xpointer = 46;
	int LPAREN = 47;
	int RPAREN = 48;
	int NCNAME = 49;
	int LITERAL_xquery = 50;
	int LITERAL_version = 51;
	int SEMICOLON = 52;
	int LITERAL_module = 53;
	int LITERAL_namespace = 54;
	int EQ = 55;
	int STRING_LITERAL = 56;
	int LITERAL_import = 57;
	int LITERAL_declare = 58;
	int LITERAL_default = 59;
	int LITERAL_xmlspace = 60;
	int LITERAL_ordering = 61;
	int LITERAL_construction = 62;
	// "base-uri" = 63
	int LITERAL_function = 64;
	int LITERAL_variable = 65;
	int LITERAL_encoding = 66;
	int LITERAL_collation = 67;
	int LITERAL_element = 68;
	int LITERAL_preserve = 69;
	int LITERAL_strip = 70;
	int LITERAL_ordered = 71;
	int LITERAL_unordered = 72;
	int DOLLAR = 73;
	int LCURLY = 74;
	int RCURLY = 75;
	int LITERAL_external = 76;
	int LITERAL_at = 77;
	int LITERAL_as = 78;
	int COMMA = 79;
	int LITERAL_empty = 80;
	int QUESTION = 81;
	int STAR = 82;
	int PLUS = 83;
	int LITERAL_item = 84;
	int LITERAL_for = 85;
	int LITERAL_let = 86;
	int LITERAL_some = 87;
	int LITERAL_every = 88;
	int LITERAL_if = 89;
	int LITERAL_update = 90;
	int LITERAL_replace = 91;
	int LITERAL_value = 92;
	int LITERAL_insert = 93;
	int LITERAL_delete = 94;
	int LITERAL_rename = 95;
	int LITERAL_with = 96;
	int LITERAL_into = 97;
	int LITERAL_before = 98;
	int LITERAL_after = 99;
	int LITERAL_where = 100;
	int LITERAL_return = 101;
	int LITERAL_in = 102;
	int COLON = 103;
	int LITERAL_order = 104;
	int LITERAL_by = 105;
	int LITERAL_ascending = 106;
	int LITERAL_descending = 107;
	int LITERAL_greatest = 108;
	int LITERAL_least = 109;
	int LITERAL_satisfies = 110;
	int LITERAL_typeswitch = 111;
	int LITERAL_case = 112;
	int LITERAL_then = 113;
	int LITERAL_else = 114;
	int LITERAL_or = 115;
	int LITERAL_and = 116;
	int LITERAL_instance = 117;
	int LITERAL_of = 118;
	int LITERAL_castable = 119;
	int LITERAL_cast = 120;
	int LT = 121;
	int GT = 122;
	int LITERAL_eq = 123;
	int LITERAL_ne = 124;
	int LITERAL_lt = 125;
	int LITERAL_le = 126;
	int LITERAL_gt = 127;
	int LITERAL_ge = 128;
	int NEQ = 129;
	int GTEQ = 130;
	int LTEQ = 131;
	int LITERAL_is = 132;
	int LITERAL_isnot = 133;
	int ANDEQ = 134;
	int OREQ = 135;
	int LITERAL_to = 136;
	int MINUS = 137;
	int LITERAL_div = 138;
	int LITERAL_idiv = 139;
	int LITERAL_mod = 140;
	int LITERAL_union = 141;
	int UNION = 142;
	int LITERAL_intersect = 143;
	int LITERAL_except = 144;
	int SLASH = 145;
	int DSLASH = 146;
	int LITERAL_text = 147;
	int LITERAL_node = 148;
	int LITERAL_attribute = 149;
	int LITERAL_comment = 150;
	// "processing-instruction" = 151
	// "document-node" = 152
	int LITERAL_document = 153;
	int SELF = 154;
	int XML_COMMENT = 155;
	int XML_PI = 156;
	int LPPAREN = 157;
	int RPPAREN = 158;
	int AT = 159;
	int PARENT = 160;
	int LITERAL_child = 161;
	int LITERAL_self = 162;
	int LITERAL_descendant = 163;
	// "descendant-or-self" = 164
	// "following-sibling" = 165
	int LITERAL_following = 166;
	int LITERAL_parent = 167;
	int LITERAL_ancestor = 168;
	// "ancestor-or-self" = 169
	// "preceding-sibling" = 170
	int DOUBLE_LITERAL = 171;
	int DECIMAL_LITERAL = 172;
	int INTEGER_LITERAL = 173;
	int END_TAG_START = 174;
	int QUOT = 175;
	int APOS = 176;
	int QUOT_ATTRIBUTE_CONTENT = 177;
	int APOS_ATTRIBUTE_CONTENT = 178;
	int ELEMENT_CONTENT = 179;
	int XML_COMMENT_END = 180;
	int XML_PI_END = 181;
	int XML_CDATA = 182;
	int LITERAL_collection = 183;
	int LITERAL_preceding = 184;
	int XML_PI_START = 185;
	int XML_CDATA_START = 186;
	int XML_CDATA_END = 187;
	int LETTER = 188;
	int DIGITS = 189;
	int HEX_DIGITS = 190;
	int NMSTART = 191;
	int NMCHAR = 192;
	int WS = 193;
	int EXPR_COMMENT = 194;
	int PRAGMA = 195;
	int PRAGMA_CONTENT = 196;
	int PRAGMA_QNAME = 197;
	int PREDEFINED_ENTITY_REF = 198;
	int CHAR_REF = 199;
	int NEXT_TOKEN = 200;
	int CHAR = 201;
	int BASECHAR = 202;
	int IDEOGRAPHIC = 203;
	int COMBINING_CHAR = 204;
	int DIGIT = 205;
	int EXTENDER = 206;
}
